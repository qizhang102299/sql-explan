package com.sqlexplan.service;

import com.sqlexplan.config.SqlexplanProperties;
import com.sqlexplan.constant.ResultEquivalenceConstants;
import com.sqlexplan.exception.BizException;
import com.sqlexplan.model.domain.ResultEquivalenceReport;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import com.sqlexplan.model.res.SqlSafetyCheckRes;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 比较两条 SELECT 的结果等价性：行数一致；在配置行数上限内比较排序后的内容校验和。
 */
@Slf4j
@Service
public class ResultEquivalenceChecker {

  private static final String HASH_ALGORITHM = "SHA-256";

  private final SqlexplanProperties sqlexplanProperties;

  private final SqlSafetyChecker sqlSafetyChecker;

  public ResultEquivalenceChecker(
      SqlexplanProperties sqlexplanProperties,
      SqlSafetyChecker sqlSafetyChecker) {
    this.sqlexplanProperties = sqlexplanProperties;
    this.sqlSafetyChecker = sqlSafetyChecker;
  }

  /**
   * 校验原 SQL 与优化 SQL 结果是否等价。
   *
   * @param jdbcTemplate 目标库只读 JdbcTemplate
   * @param originalSql  原始 SELECT
   * @param optimizedSql 优化后 SELECT
   * @return 等价性报告
   */
  public ResultEquivalenceReport check(
      JdbcTemplate jdbcTemplate,
      String originalSql,
      String optimizedSql) {
    if (jdbcTemplate == null) {
      throw new BizException("JdbcTemplate 不能为空");
    }
    if (!StringUtils.hasText(originalSql) || !StringUtils.hasText(optimizedSql)) {
      throw new BizException("待比较的 SQL 不能为空");
    }
    ResultEquivalenceReport report = new ResultEquivalenceReport();
    int maxRows = sqlexplanProperties.getResultEquivalence().getMaxRowsForChecksum();
    FetchedResult original = fetchResult(jdbcTemplate, originalSql, maxRows);
    FetchedResult optimized = fetchResult(jdbcTemplate, optimizedSql, maxRows);
    report.setOriginalRowCount(original.totalRowCount);
    report.setOptimizedRowCount(optimized.totalRowCount);
    if (original.totalRowCount != optimized.totalRowCount) {
      report.setEquivalent(false);
      report.setChecksumSkipped(original.checksumSkipped || optimized.checksumSkipped);
      report.setMessage("行数不一致");
      return report;
    }
    if (original.checksumSkipped || optimized.checksumSkipped) {
      report.setEquivalent(true);
      report.setChecksumSkipped(true);
      report.setMessage("行数一致，结果集超过上限未比对内容校验和");
      return report;
    }
    report.setOriginalChecksum(original.checksum);
    report.setOptimizedChecksum(optimized.checksum);
    report.setChecksumSkipped(false);
    boolean sameChecksum = original.checksum.equals(optimized.checksum);
    report.setEquivalent(sameChecksum);
    report.setMessage(sameChecksum ? "行数与内容校验和一致" : "行数一致但内容校验和不一致");
    return report;
  }

  private FetchedResult fetchResult(JdbcTemplate jdbcTemplate, String sql, int maxRows) {
    SqlSafetyCheckRes safety = sqlSafetyChecker.check(sql);
    if (safety.getPassed() == null || !safety.getPassed()) {
      throw new BizException("SQL 安全校验未通过: " + safety.getReason());
    }
    String limitedSql = wrapWithLimit(sql, maxRows + 1);
    List<String> canonicalRows = jdbcTemplate.query(
        limitedSql,
        (rs, rowNum) -> canonicalRow(rs));
    long totalCount;
    boolean checksumSkipped;
    String checksum;
    if (canonicalRows.size() > maxRows) {
      totalCount = countAllRows(jdbcTemplate, sql);
      checksumSkipped = true;
      checksum = "";
    } else {
      totalCount = canonicalRows.size();
      checksumSkipped = false;
      checksum = checksumRows(canonicalRows);
    }
    return new FetchedResult(totalCount, checksumSkipped, checksum);
  }

  private long countAllRows(JdbcTemplate jdbcTemplate, String sql) {
    String countSql = "SELECT COUNT(*) FROM (" + sql.trim() + ") AS "
        + ResultEquivalenceConstants.COUNT_SUBQUERY_ALIAS;
    Long count = jdbcTemplate.queryForObject(countSql, Long.class);
    return count == null ? 0L : count;
  }

  private static String wrapWithLimit(String sql, int limitPlusOne) {
    String trimmed = sql.trim();
    if (trimmed.endsWith(";")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
    }
    return "SELECT * FROM (" + trimmed + ") AS _sqlexplan_eq_limited LIMIT " + limitPlusOne;
  }

  private static String canonicalRow(ResultSet rs) throws SQLException {
    ResultSetMetaData meta = rs.getMetaData();
    int columnCount = meta.getColumnCount();
    List<String> parts = new ArrayList<>(columnCount);
    for (int i = 1; i <= columnCount; i++) {
      Object value = rs.getObject(i);
      parts.add(value == null ? "NULL" : String.valueOf(value));
    }
    return String.join(ResultEquivalenceConstants.ROW_CANONICAL_SEPARATOR, parts);
  }

  private static String checksumRows(List<String> canonicalRows) {
    List<String> sorted = new ArrayList<>(canonicalRows);
    Collections.sort(sorted);
    StringBuilder payload = new StringBuilder();
    for (String row : sorted) {
      payload.append(row).append('\n');
    }
    try {
      MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
      byte[] hash = digest.digest(payload.toString().getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException ex) {
      throw new BizException("不支持的哈希算法");
    }
  }

  private record FetchedResult(long totalRowCount, boolean checksumSkipped, String checksum) {
  }
}
