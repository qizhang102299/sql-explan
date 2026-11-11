package com.sqlexplan.service;

import com.sqlexplan.config.SqlexplanProperties;
import com.sqlexplan.constant.LlmConstants;
import com.sqlexplan.exception.BizException;
import com.sqlexplan.manager.LlmClient;
import com.sqlexplan.manager.LlmPromptAssembler;
import com.sqlexplan.manager.LlmResponseCacheManager;
import com.sqlexplan.model.domain.ExplainPlan;
import com.sqlexplan.model.domain.IndexAdvice;
import com.sqlexplan.model.domain.IndexAdviceReport;
import com.sqlexplan.model.domain.LlmIndexAdviceItem;
import com.sqlexplan.model.domain.LlmStructuredAnalysis;
import com.sqlexplan.model.domain.PlanInterpretation;
import com.sqlexplan.model.domain.SqlAnalysisEnhanceResult;
import com.sqlexplan.model.domain.SqlRewriteResult;
import com.sqlexplan.model.domain.SqlSemanticInsight;
import com.sqlexplan.model.domain.SqlStructure;
import com.sqlexplan.model.domain.TableSchemaMeta;
import com.sqlexplan.model.enums.AnalysisSourceEnum;
import com.sqlexplan.model.enums.IndexAdviceTypeEnum;
import com.sqlexplan.util.SchemaMetaVersionUtil;
import com.sqlexplan.util.SqlFingerprintUtil;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 阶段 5：在规则引擎结果之上可选调用 LLM 增强，并校验 AI 改写 SQL。
 */
@Slf4j
@Service
public class LlmEnhanceService {

  private final SqlexplanProperties sqlexplanProperties;

  private final LlmClient llmClient;

  private final LlmPromptAssembler llmPromptAssembler;

  private final LlmResponseCacheManager llmResponseCacheManager;

  private final RuleBasedSemanticBuilder ruleBasedSemanticBuilder;

  private final PlanExplanationBuilder planExplanationBuilder;

  private final AiOptimizedSqlValidator aiOptimizedSqlValidator;

  public LlmEnhanceService(
      SqlexplanProperties sqlexplanProperties,
      LlmClient llmClient,
      LlmPromptAssembler llmPromptAssembler,
      LlmResponseCacheManager llmResponseCacheManager,
      RuleBasedSemanticBuilder ruleBasedSemanticBuilder,
      PlanExplanationBuilder planExplanationBuilder,
      AiOptimizedSqlValidator aiOptimizedSqlValidator) {
    this.sqlexplanProperties = sqlexplanProperties;
    this.llmClient = llmClient;
    this.llmPromptAssembler = llmPromptAssembler;
    this.llmResponseCacheManager = llmResponseCacheManager;
    this.ruleBasedSemanticBuilder = ruleBasedSemanticBuilder;
    this.planExplanationBuilder = planExplanationBuilder;
    this.aiOptimizedSqlValidator = aiOptimizedSqlValidator;
  }

  /**
   * 执行 LLM 增强分析（关闭 LLM 或失败时降级，不影响规则结果可用性）。
   *
   * @param jdbcTemplate        目标库 JdbcTemplate
   * @param sql                 原始 SQL
   * @param sqlStructure        解析结构
   * @param tableMetadata       表元数据
   * @param explainPlan         执行计划
   * @param planInterpretation  计划解读
   * @param ruleIndexReport     规则索引建议
   * @param ruleRewriteResult   规则改写结果
   * @return 增强结果
   */
  public SqlAnalysisEnhanceResult enhance(
      JdbcTemplate jdbcTemplate,
      String sql,
      SqlStructure sqlStructure,
      List<TableSchemaMeta> tableMetadata,
      ExplainPlan explainPlan,
      PlanInterpretation planInterpretation,
      IndexAdviceReport ruleIndexReport,
      SqlRewriteResult ruleRewriteResult) {
    SqlAnalysisEnhanceResult result = new SqlAnalysisEnhanceResult();
    IndexAdviceReport mergedIndex = copyIndexReport(ruleIndexReport);
    result.setIndexAdviceReport(mergedIndex);
    String rulePlanNarrative = buildLocalPlanNarrative(explainPlan, planInterpretation);
    String ruleOptimized = ruleRewriteResult == null ? sql : nullToEmpty(ruleRewriteResult.getRecommendedSql());
    if (!StringUtils.hasText(ruleOptimized)) {
      ruleOptimized = sql;
    }

    if (!sqlexplanProperties.getLlm().isEnabled()) {
      applyRuleFallback(
          result, sqlStructure, explainPlan, planInterpretation, rulePlanNarrative, ruleOptimized, ruleRewriteResult);
      result.setAiUsed(false);
      result.setAnalysisSource(AnalysisSourceEnum.LOCAL_RULES);
      result.setAiStatusNote(LlmConstants.DEGRADE_DISABLED);
      return result;
    }
    if (!llmClient.isInvocationReady()) {
      applyRuleFallback(
          result, sqlStructure, explainPlan, planInterpretation, rulePlanNarrative, ruleOptimized, ruleRewriteResult);
      result.setAiUsed(false);
      result.setAnalysisSource(AnalysisSourceEnum.LOCAL_RULES);
      result.setAiStatusNote(LlmConstants.DEGRADE_NO_API_KEY);
      return result;
    }

    String fingerprint = SqlFingerprintUtil.fingerprint(sql);
    String schemaVersion = SchemaMetaVersionUtil.computeVersion(tableMetadata);
    String cacheKey = LlmResponseCacheManager.buildCacheKey(fingerprint, schemaVersion);
    LlmStructuredAnalysis cached = llmResponseCacheManager.get(cacheKey);
    LlmStructuredAnalysis analysis = cached;
    boolean fromCache = cached != null;
    if (!fromCache) {
      try {
        String userMessage = llmPromptAssembler.buildUserMessage(
            sql,
            sqlStructure,
            tableMetadata,
            explainPlan,
            planInterpretation,
            ruleIndexReport,
            ruleRewriteResult);
        analysis = llmClient.analyze(llmPromptAssembler.getSystemPrompt(), userMessage);
        llmResponseCacheManager.put(cacheKey, analysis);
      } catch (BizException ex) {
        log.warn("LLM 增强失败，执行降级");
        applyRuleFallback(
            result, sqlStructure, explainPlan, planInterpretation, rulePlanNarrative, ruleOptimized, ruleRewriteResult);
        result.setAiUsed(false);
        result.setAnalysisSource(AnalysisSourceEnum.LOCAL_RULES);
        result.setAiStatusNote(resolveDegradeNote(ex.getMessage()));
        return result;
      }
    }

    result.setFromCache(fromCache);
    result.setAiUsed(true);
    result.setAnalysisSource(AnalysisSourceEnum.AI);
    result.setAiStatusNote("已使用 AI 增强");
    result.setSemantic(analysis.getSemantic() != null ? analysis.getSemantic() : new SqlSemanticInsight());
    result.setPlanNarrative(StringUtils.hasText(analysis.getPlanNarrative())
        ? analysis.getPlanNarrative()
        : rulePlanNarrative);
    mergeAiIndexAdvices(mergedIndex, analysis.getIndexAdvices());
    result.setRewriteExplanation(analysis.getRewriteExplanation());

    String candidate = StringUtils.hasText(analysis.getOptimizedSql())
        ? analysis.getOptimizedSql().trim()
        : "";
    if (!StringUtils.hasText(candidate)) {
      result.setOptimizedSql(ruleOptimized);
      result.setOptimizedSqlAccepted(true);
      result.setOptimizedValidation(null);
      return result;
    }
    var validation = aiOptimizedSqlValidator.validate(jdbcTemplate, sql, candidate);
    result.setOptimizedValidation(validation);
    if (validation.isPassed()) {
      result.setOptimizedSql(candidate);
      result.setOptimizedSqlAccepted(true);
    } else {
      result.setOptimizedSql(ruleOptimized);
      result.setOptimizedSqlAccepted(false);
      if (!StringUtils.hasText(result.getRewriteExplanation())) {
        result.setRewriteExplanation("AI 改写未通过校验已丢弃: " + validation.getFailureReason());
      } else {
        result.setRewriteExplanation(
            result.getRewriteExplanation() + "；未采纳改写: " + validation.getFailureReason());
      }
    }
    return result;
  }

  private String buildLocalPlanNarrative(ExplainPlan explainPlan, PlanInterpretation planInterpretation) {
    return planExplanationBuilder.build(explainPlan, planInterpretation);
  }

  private void applyRuleFallback(
      SqlAnalysisEnhanceResult result,
      SqlStructure sqlStructure,
      ExplainPlan explainPlan,
      PlanInterpretation planInterpretation,
      String rulePlanNarrative,
      String ruleOptimized,
      SqlRewriteResult ruleRewriteResult) {
    result.setSemantic(ruleBasedSemanticBuilder.build(sqlStructure));
    result.setPlanNarrative(rulePlanNarrative);
    if (!StringUtils.hasText(result.getPlanNarrative())) {
      result.setPlanNarrative(planExplanationBuilder.build(explainPlan, planInterpretation));
    }
    result.setOptimizedSql(ruleOptimized);
    result.setOptimizedSqlAccepted(true);
    if (ruleRewriteResult != null && StringUtils.hasText(ruleRewriteResult.getRecommendedSql())) {
      result.setRewriteExplanation("使用规则引擎改写建议");
    } else {
      result.setRewriteExplanation("保持原 SQL");
    }
  }

  private static void mergeAiIndexAdvices(IndexAdviceReport report, List<LlmIndexAdviceItem> aiItems) {
    if (report == null || aiItems == null || aiItems.isEmpty()) {
      return;
    }
    for (LlmIndexAdviceItem item : aiItems) {
      if (!StringUtils.hasText(item.getReason()) && !StringUtils.hasText(item.getDdl())) {
        continue;
      }
      IndexAdvice advice = new IndexAdvice();
      advice.setAdviceType(IndexAdviceTypeEnum.CREATE_INDEX);
      advice.setTableName(item.getTableName());
      advice.setCreateIndexDdl(item.getDdl());
      advice.setReason("[AI] " + nullToEmpty(item.getReason()));
      advice.setExpectedBenefit(item.getExpectedBenefit());
      advice.setWriteCostNote(item.getWriteCostNote());
      report.getAdvices().add(advice);
    }
    if (!StringUtils.hasText(report.getSummary())) {
      report.setSummary("含规则引擎与 AI 增强建议");
    }
  }

  private static IndexAdviceReport copyIndexReport(IndexAdviceReport source) {
    IndexAdviceReport copy = new IndexAdviceReport();
    if (source == null) {
      return copy;
    }
    copy.setSummary(source.getSummary());
    if (source.getAdvices() != null) {
      copy.getAdvices().addAll(source.getAdvices());
    }
    return copy;
  }

  private static String resolveDegradeNote(String message) {
    if (!StringUtils.hasText(message)) {
      return LlmConstants.DEGRADE_CALL_FAILED;
    }
    if (message.contains(LlmConstants.DEGRADE_PARSE_FAILED)
        || message.contains("解析")) {
      return LlmConstants.DEGRADE_PARSE_FAILED;
    }
    return LlmConstants.DEGRADE_CALL_FAILED;
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
