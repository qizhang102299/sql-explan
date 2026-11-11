package com.sqlexplan.service;

import com.sqlexplan.exception.BizException;
import com.sqlexplan.manager.TargetJdbcResolver;
import com.sqlexplan.model.domain.AiOptimizedSqlValidation;
import com.sqlexplan.model.domain.BenchmarkReport;
import com.sqlexplan.model.domain.ExplainPlan;
import com.sqlexplan.model.domain.IndexAdviceReport;
import com.sqlexplan.model.domain.PlanInterpretation;
import com.sqlexplan.model.domain.SqlAnalysisEnhanceResult;
import com.sqlexplan.model.domain.SqlRewriteResult;
import com.sqlexplan.model.domain.SqlStructure;
import com.sqlexplan.model.domain.TableReference;
import com.sqlexplan.model.domain.TableSchemaMeta;
import com.sqlexplan.model.domain.ValidatedRewriteCandidate;
import com.sqlexplan.model.req.SqlAnalyzeReq;
import com.sqlexplan.model.res.OptimizedSqlRes;
import com.sqlexplan.model.res.SqlAnalyzeRes;
import com.sqlexplan.model.res.SqlPlanRes;
import com.sqlexplan.util.SqlTextUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * SQL 分析全流程编排。
 */
@Service
@RequiredArgsConstructor
public class AnalyzeOrchestrator {

  private final SqlSyntaxValidator sqlSyntaxValidator;

  private final SqlParseService sqlParseService;

  private final SchemaMetaService schemaMetaService;

  private final ExplainService explainService;

  private final PlanInterpreter planInterpreter;

  private final IndexAdvisor indexAdvisor;

  private final SqlRewriter sqlRewriter;

  private final RewriteCandidateValidator rewriteCandidateValidator;

  private final LlmEnhanceService llmEnhanceService;

  private final BenchmarkService benchmarkService;

  /**
   * 执行完整分析流水线。
   *
   * @param req               分析请求
   * @param connectionContext 目标库连接
   * @return 分析结果
   */
  public SqlAnalyzeRes analyze(SqlAnalyzeReq req, TargetJdbcResolver.TargetConnectionContext connectionContext) {
    String sql = SqlTextUtil.stripTrailingSemicolons(req.getSql());
    JdbcTemplate jdbcTemplate = connectionContext.getJdbcTemplate();
    String schemaName = connectionContext.getDbName();
    sqlSyntaxValidator.assertValid(sql, jdbcTemplate, schemaName);
    Long dataSourceId = connectionContext.getDataSourceId();

    SqlStructure sqlStructure = sqlParseService.parse(sql);
    List<String> tableNames = extractTableNames(sqlStructure);
    List<TableSchemaMeta> tableMetadata = schemaMetaService.loadTableMetadata(
        dataSourceId, schemaName, jdbcTemplate, tableNames);

    ExplainPlan explainPlan = explainService.explain(jdbcTemplate, sql);
    PlanInterpretation planInterpretation =
        planInterpreter.interpret(explainPlan, sqlStructure, tableMetadata);
    IndexAdviceReport indexAdviceReport = indexAdvisor.advise(sqlStructure, tableMetadata, planInterpretation);
    SqlRewriteResult rewriteResult = sqlRewriter.rewrite(sql, sqlStructure, tableMetadata);

    List<ValidatedRewriteCandidate> passedCandidates =
        rewriteCandidateValidator.validatePassedOnly(jdbcTemplate, sql, rewriteResult);
    String ruleOptimizedSql =
        rewriteCandidateValidator.pickBestOptimizedSql(sql, rewriteResult, passedCandidates);
    SqlRewriteResult rewriteForLlm = cloneRewriteWithRecommended(rewriteResult, ruleOptimizedSql);

    SqlAnalysisEnhanceResult enhanceResult = llmEnhanceService.enhance(
        jdbcTemplate,
        sql,
        sqlStructure,
        tableMetadata,
        explainPlan,
        planInterpretation,
        indexAdviceReport,
        rewriteForLlm);

    String finalOptimized = resolveFinalOptimizedSql(sql, ruleOptimizedSql, enhanceResult);
    AiOptimizedSqlValidation finalValidation =
        rewriteCandidateValidator.validateFinal(jdbcTemplate, sql, finalOptimized);

    SqlAnalyzeRes res = new SqlAnalyzeRes();
    res.setAnalysisSource(enhanceResult.getAnalysisSource());
    res.setAiStatusNote(enhanceResult.getAiStatusNote());
    res.setSemantic(enhanceResult.getSemantic());

    SqlPlanRes planRes = new SqlPlanRes();
    planRes.setExplainPlan(explainPlan);
    planRes.setInterpretation(planInterpretation);
    planRes.setNarrative(enhanceResult.getPlanNarrative());
    res.setPlan(planRes);

    res.setIndexAdvices(enhanceResult.getIndexAdviceReport());

    OptimizedSqlRes optimizedSqlRes = buildOptimizedSqlRes(
        sql, passedCandidates, finalOptimized, finalValidation, enhanceResult);
    res.setOptimizedSql(optimizedSqlRes);

    boolean runBenchmark = Boolean.TRUE.equals(req.getEnableBenchmark());
    if (runBenchmark) {
      boolean verifyIndex = Boolean.TRUE.equals(req.getVerifyIndexBenefit());
      int iterations = req.getBenchmarkIterations() == null
          ? com.sqlexplan.constant.BenchmarkConstants.DEFAULT_ITERATIONS
          : req.getBenchmarkIterations();
      BenchmarkReport benchmarkReport = benchmarkService.benchmark(
          jdbcTemplate,
          sql,
          finalOptimized,
          iterations,
          verifyIndex,
          res.getIndexAdvices(),
          connectionContext);
      res.setBenchmark(benchmarkReport);
    }

    res.setCreatedTime(System.currentTimeMillis());
    return res;
  }

  private OptimizedSqlRes buildOptimizedSqlRes(
      String originalSql,
      List<ValidatedRewriteCandidate> passedCandidates,
      String finalOptimized,
      AiOptimizedSqlValidation finalValidation,
      SqlAnalysisEnhanceResult enhanceResult) {
    OptimizedSqlRes optimizedSqlRes = new OptimizedSqlRes();
    optimizedSqlRes.setSql(finalOptimized);
    optimizedSqlRes.setExplanation(enhanceResult.getRewriteExplanation());
    optimizedSqlRes.setEquivalencePassed(finalValidation.isEquivalencePassed());
    optimizedSqlRes.setEquivalenceReport(finalValidation.getEquivalenceReport());
    optimizedSqlRes.getPassedCandidates().addAll(passedCandidates);
    if (finalOptimized.trim().equalsIgnoreCase(originalSql.trim())) {
      optimizedSqlRes.setEquivalencePassed(true);
    }
    return optimizedSqlRes;
  }

  private static String resolveFinalOptimizedSql(
      String originalSql,
      String ruleOptimizedSql,
      SqlAnalysisEnhanceResult enhanceResult) {
    if (enhanceResult.isOptimizedSqlAccepted() && StringUtils.hasText(enhanceResult.getOptimizedSql())) {
      return enhanceResult.getOptimizedSql().trim();
    }
    if (StringUtils.hasText(ruleOptimizedSql)) {
      return ruleOptimizedSql.trim();
    }
    return originalSql;
  }

  private static SqlRewriteResult cloneRewriteWithRecommended(
      SqlRewriteResult source, String recommendedSql) {
    SqlRewriteResult copy = new SqlRewriteResult();
    if (source != null) {
      copy.setOriginalSql(source.getOriginalSql());
      copy.setRecommendedSql(recommendedSql);
      if (source.getSuggestions() != null) {
        copy.getSuggestions().addAll(source.getSuggestions());
      }
    } else {
      copy.setRecommendedSql(recommendedSql);
    }
    return copy;
  }

  private static List<String> extractTableNames(SqlStructure sqlStructure) {
    if (sqlStructure == null || CollectionUtils.isEmpty(sqlStructure.getAllTables())) {
      return List.of();
    }
    return sqlStructure.getAllTables().stream()
        .map(TableReference::getTableName)
        .filter(StringUtils::hasText)
        .map(name -> name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : name)
        .filter(StringUtils::hasText)
        .distinct()
        .collect(Collectors.toCollection(ArrayList::new));
  }
}
