package com.sqlexplan.service;

import com.sqlexplan.config.SqlexplanProperties;
import com.sqlexplan.constant.BenchmarkConstants;
import com.sqlexplan.exception.BizException;
import com.sqlexplan.manager.TargetDataSourceManager;
import com.sqlexplan.manager.TargetJdbcResolver;
import com.sqlexplan.model.domain.BenchmarkLatencyStats;
import com.sqlexplan.model.domain.BenchmarkReport;
import com.sqlexplan.model.domain.IndexAdvice;
import com.sqlexplan.model.domain.IndexAdviceReport;
import com.sqlexplan.model.domain.ResultEquivalenceReport;
import com.sqlexplan.model.enums.IndexAdviceTypeEnum;
import com.sqlexplan.model.res.SqlSafetyCheckRes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 优化前后耗时对比：预热、交替执行、统计与等价告警；可选临时索引收益验证。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BenchmarkService {

  private static final Pattern CREATE_INDEX_PATTERN =
      Pattern.compile(
          "(?is)^\\s*CREATE\\s+(?:UNIQUE\\s+)?INDEX\\s+[`\"]?(\\w+)[`\"]?\\s+ON\\s+[`\"]?(\\w+)[`\"]?",
          Pattern.CASE_INSENSITIVE);

  private final SqlexplanProperties sqlexplanProperties;

  private final SqlSafetyChecker sqlSafetyChecker;

  private final ResultEquivalenceChecker resultEquivalenceChecker;

  private final TargetDataSourceManager targetDataSourceManager;

  /**
   * 执行基准测试。
   *
   * @param jdbcTemplate       目标库只读连接
   * @param originalSql        原始 SQL
   * @param optimizedSql       优化 SQL
   * @param iterations         每侧交替次数
   * @param verifyIndexBenefit 是否创建临时索引验证
   * @param indexAdviceReport  索引建议（用于挑选 DDL）
   * @param connectionContext  连接上下文（索引验证需可写连接，可为 null）
   * @return 基准报告
   */
  public BenchmarkReport benchmark(
      JdbcTemplate jdbcTemplate,
      String originalSql,
      String optimizedSql,
      int iterations,
      boolean verifyIndexBenefit,
      IndexAdviceReport indexAdviceReport,
      TargetJdbcResolver.TargetConnectionContext connectionContext) {
    int safeIterations = clampIterations(iterations);
    BenchmarkReport report = new BenchmarkReport();
    report.setIterations(safeIterations);
    ensureSafeSql(originalSql);
    ensureSafeSql(optimizedSql);
    if (originalSql.trim().equalsIgnoreCase(optimizedSql.trim())) {
      report.setExecuted(false);
      report.setWarningMessage("优化 SQL 与原始 SQL 相同，跳过耗时对比");
      attachEquivalence(report, jdbcTemplate, originalSql, optimizedSql);
      return report;
    }
    report.setExecuted(true);
    int maxRows = sqlexplanProperties.getBenchmark().getMaxRowsPerExecution();
    warmUp(jdbcTemplate, originalSql, maxRows);
    List<Long> originalLatencies = new ArrayList<>(safeIterations);
    List<Long> optimizedLatencies = new ArrayList<>(safeIterations);
    for (int i = 0; i < safeIterations; i++) {
      originalLatencies.add(measureOnce(jdbcTemplate, originalSql, maxRows));
      optimizedLatencies.add(measureOnce(jdbcTemplate, optimizedSql, maxRows));
    }
    report.setOriginalStats(toStats(originalLatencies));
    report.setOptimizedStats(toStats(optimizedLatencies));
    report.setImprovementRatio(calcImprovementRatio(
        report.getOriginalStats().getMedianMs(),
        report.getOptimizedStats().getMedianMs()));
    attachEquivalence(report, jdbcTemplate, originalSql, optimizedSql);
    if (!verifyIndexBenefit) {
      report.setIndexBenefitVerified(false);
      report.setIndexVerificationNote(BenchmarkConstants.INDEX_BENEFIT_EXPLAIN_ONLY_NOTE);
      return report;
    }
    if (!sqlexplanProperties.getBenchmark().isAllowTempIndexVerification()) {
      report.setIndexBenefitVerified(false);
      report.setIndexVerificationNote("配置未允许临时索引验证（sqlexplan.benchmark.allow-temp-index-verification）");
      return report;
    }
    if (connectionContext == null) {
      report.setIndexVerificationNote("未提供连接上下文，无法创建临时索引");
      return report;
    }
    runIndexBenefitVerification(
        jdbcTemplate,
        originalSql,
        safeIterations,
        maxRows,
        indexAdviceReport,
        connectionContext,
        report);
    return report;
  }

  /**
   * 计算耗时统计（供单测使用）。
   *
   * @param latenciesMs 多次耗时样本
   * @return 统计结果
   */
  public BenchmarkLatencyStats toStats(List<Long> latenciesMs) {
    BenchmarkLatencyStats stats = new BenchmarkLatencyStats();
    if (latenciesMs == null || latenciesMs.isEmpty()) {
      stats.setExecutionCount(0);
      stats.setMedianMs(0L);
      stats.setP95Ms(0L);
      stats.setMinMs(0L);
      stats.setMaxMs(0L);
      return stats;
    }
    List<Long> sorted = new ArrayList<>(latenciesMs);
    Collections.sort(sorted);
    stats.setExecutionCount(sorted.size());
    stats.setMinMs(sorted.get(0));
    stats.setMaxMs(sorted.get(sorted.size() - 1));
    stats.setMedianMs(median(sorted));
    stats.setP95Ms(percentile(sorted, 95));
    return stats;
  }

  private void runIndexBenefitVerification(
      JdbcTemplate readJdbc,
      String originalSql,
      int iterations,
      int maxRows,
      IndexAdviceReport indexAdviceReport,
      TargetJdbcResolver.TargetConnectionContext context,
      BenchmarkReport report) {
    IndexAdvice ddlAdvice = pickCreateIndexAdvice(indexAdviceReport);
    if (ddlAdvice == null || !StringUtils.hasText(ddlAdvice.getCreateIndexDdl())) {
      report.setIndexVerificationNote("无可用 CREATE INDEX 建议，跳过实库验证");
      return;
    }
    String tempIndexName = BenchmarkConstants.TEMP_INDEX_NAME_PREFIX + System.currentTimeMillis() + "_"
        + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    ParsedIndexDdl parsed = parseCreateIndexDdl(ddlAdvice.getCreateIndexDdl(), tempIndexName);
    if (parsed == null) {
      report.setIndexVerificationNote("无法解析索引 DDL，跳过实库验证");
      return;
    }
    JdbcTemplate writable = context.openWritableJdbcTemplate(targetDataSourceManager);
    try {
      report.setOriginalMedianBeforeIndexMs(
          benchmarkMedianOnly(readJdbc, originalSql, iterations, maxRows));
      writable.execute(parsed.createDdl());
      report.setOriginalMedianAfterIndexMs(
          benchmarkMedianOnly(readJdbc, originalSql, iterations, maxRows));
      report.setIndexBenefitVerified(true);
      report.setIndexVerificationNote("已在目标库创建临时索引 " + tempIndexName + " 并对比耗时，测后已删除");
    } catch (Exception ex) {
      log.warn("临时索引收益验证失败");
      report.setIndexVerificationNote("临时索引验证失败: " + ex.getMessage());
    } finally {
      try {
        writable.execute("DROP INDEX `" + parsed.indexName() + "` ON `" + parsed.tableName() + "`");
      } catch (Exception dropEx) {
        log.warn("删除临时索引失败, index={}", parsed.indexName());
      }
      targetDataSourceManager.closeDisposableJdbcTemplate(writable);
    }
  }

  private long benchmarkMedianOnly(
      JdbcTemplate jdbcTemplate, String sql, int iterations, int maxRows) {
    warmUp(jdbcTemplate, sql, maxRows);
    List<Long> samples = new ArrayList<>(iterations);
    for (int i = 0; i < iterations; i++) {
      samples.add(measureOnce(jdbcTemplate, sql, maxRows));
    }
    return median(samples);
  }

  private void attachEquivalence(
      BenchmarkReport report,
      JdbcTemplate jdbcTemplate,
      String originalSql,
      String optimizedSql) {
    ResultEquivalenceReport equivalence =
        resultEquivalenceChecker.check(jdbcTemplate, originalSql, optimizedSql);
    report.setEquivalence(equivalence);
    if (!equivalence.isEquivalent()) {
      report.setEquivalenceWarning(true);
      report.setWarningMessage(BenchmarkConstants.EQUIVALENCE_MISMATCH_WARNING + "："
          + nullToEmpty(equivalence.getMessage()));
    }
  }

  private void warmUp(JdbcTemplate jdbcTemplate, String sql, int maxRows) {
    measureOnce(jdbcTemplate, sql, maxRows);
  }

  private long measureOnce(JdbcTemplate jdbcTemplate, String sql, int maxRows) {
    long start = System.nanoTime();
    jdbcTemplate.query(
        sql,
        rs -> {
          int rowCount = 0;
          while (rs.next()) {
            rowCount++;
            if (rowCount >= maxRows) {
              break;
            }
          }
        });
    return (System.nanoTime() - start) / 1_000_000L;
  }

  private void ensureSafeSql(String sql) {
    SqlSafetyCheckRes check = sqlSafetyChecker.check(sql);
    if (check.getPassed() == null || !check.getPassed()) {
      throw new BizException("SQL 安全校验未通过: " + check.getReason());
    }
  }

  private int clampIterations(int iterations) {
    if (iterations < BenchmarkConstants.MIN_ITERATIONS) {
      return BenchmarkConstants.DEFAULT_ITERATIONS;
    }
    return Math.min(iterations, BenchmarkConstants.MAX_ITERATIONS);
  }

  private static Double calcImprovementRatio(Long originalMedian, Long optimizedMedian) {
    if (originalMedian == null || optimizedMedian == null || originalMedian <= 0) {
      return 0.0;
    }
    return (originalMedian.doubleValue() - optimizedMedian.doubleValue()) / originalMedian.doubleValue();
  }

  private static long median(List<Long> sortedInput) {
    List<Long> sorted = new ArrayList<>(sortedInput);
    Collections.sort(sorted);
    int n = sorted.size();
    if (n == 0) {
      return 0L;
    }
    if (n % 2 == 1) {
      return sorted.get(n / 2);
    }
    return (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2;
  }

  private static long percentile(List<Long> sorted, int percentile) {
    if (sorted.isEmpty()) {
      return 0L;
    }
    int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
    index = Math.max(0, Math.min(index, sorted.size() - 1));
    return sorted.get(index);
  }

  private static IndexAdvice pickCreateIndexAdvice(IndexAdviceReport report) {
    if (report == null || report.getAdvices() == null) {
      return null;
    }
    for (IndexAdvice advice : report.getAdvices()) {
      boolean creatable =
          advice.getAdviceType() == IndexAdviceTypeEnum.CREATE_INDEX
              || advice.getAdviceType() == IndexAdviceTypeEnum.COVERING_INDEX;
      if (creatable && StringUtils.hasText(advice.getCreateIndexDdl())) {
        return advice;
      }
    }
    return null;
  }

  private static ParsedIndexDdl parseCreateIndexDdl(String ddl, String tempIndexName) {
    Matcher matcher = CREATE_INDEX_PATTERN.matcher(ddl.trim());
    if (!matcher.find()) {
      return null;
    }
    String tableName = matcher.group(2);
    String createDdl = ddl.trim().replaceFirst(
        "(?i)CREATE\\s+(?:UNIQUE\\s+)?INDEX\\s+[`\"]?(\\w+)[`\"]?",
        "CREATE INDEX `" + tempIndexName + "`");
    return new ParsedIndexDdl(tempIndexName, tableName, createDdl);
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private record ParsedIndexDdl(String indexName, String tableName, String createDdl) {
  }
}
