package com.sqlexplan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sqlexplan.config.SqlexplanProperties;
import com.sqlexplan.constant.LlmConstants;
import com.sqlexplan.manager.LlmClient;
import com.sqlexplan.manager.LlmPromptAssembler;
import com.sqlexplan.manager.LlmResponseCacheManager;
import com.sqlexplan.model.domain.AiOptimizedSqlValidation;
import com.sqlexplan.model.domain.ExplainPlan;
import com.sqlexplan.model.domain.IndexAdviceReport;
import com.sqlexplan.model.domain.LlmStructuredAnalysis;
import com.sqlexplan.model.domain.PlanInterpretation;
import com.sqlexplan.model.domain.SqlQueryBody;
import com.sqlexplan.model.domain.SqlRewriteResult;
import com.sqlexplan.manager.TargetExplainManager;
import com.sqlexplan.model.domain.SqlAnalysisEnhanceResult;
import com.sqlexplan.model.domain.SqlSemanticInsight;
import com.sqlexplan.model.domain.SqlStructure;
import com.sqlexplan.model.enums.AnalysisSourceEnum;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@link LlmEnhanceService} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class LlmEnhanceServiceTest {

  @Mock
  private LlmClient llmClient;

  @Mock
  private LlmPromptAssembler llmPromptAssembler;

  @Mock
  private LlmResponseCacheManager llmResponseCacheManager;

  @Mock
  private AiOptimizedSqlValidator aiOptimizedSqlValidator;

  @Mock
  private JdbcTemplate jdbcTemplate;

  private LlmEnhanceService llmEnhanceService;

  private SqlexplanProperties properties;

  @BeforeEach
  void setUp() {
    properties = new SqlexplanProperties();
    ExplainService explainService = new ExplainService(new TargetExplainManager());
    RuleBasedSemanticBuilder semanticBuilder = new RuleBasedSemanticBuilder(properties);
    PlanExplanationBuilder planExplanationBuilder = new PlanExplanationBuilder(explainService);
    llmEnhanceService = new LlmEnhanceService(
        properties,
        llmClient,
        llmPromptAssembler,
        llmResponseCacheManager,
        semanticBuilder,
        planExplanationBuilder,
        aiOptimizedSqlValidator);
  }

  @Test
  void degradeWhenLlmDisabled() {
    properties.getLlm().setEnabled(false);
    SqlStructure structure = minimalStructure();
    SqlAnalysisEnhanceResult result = llmEnhanceService.enhance(
        jdbcTemplate,
        "SELECT id FROM t",
        structure,
        Collections.emptyList(),
        new ExplainPlan(),
        new PlanInterpretation(),
        new IndexAdviceReport(),
        new SqlRewriteResult());
    assertThat(result.isAiUsed()).isFalse();
    assertThat(result.getAnalysisSource()).isEqualTo(AnalysisSourceEnum.LOCAL_RULES);
    assertThat(result.getAiStatusNote()).isEqualTo(LlmConstants.DEGRADE_DISABLED);
    verify(llmClient, never()).analyze(anyString(), anyString());
  }

  @Test
  void acceptAiSqlWhenValidationPasses() {
    properties.getLlm().setEnabled(true);
    when(llmClient.isInvocationReady()).thenReturn(true);
    when(llmResponseCacheManager.get(anyString())).thenReturn(null);
    when(llmPromptAssembler.getSystemPrompt()).thenReturn("sys");
    when(llmPromptAssembler.buildUserMessage(
        anyString(),
        any(),
        any(),
        any(),
        any(),
        any(),
        any())).thenReturn("user");
    LlmStructuredAnalysis analysis = new LlmStructuredAnalysis();
    SqlSemanticInsight semantic = new SqlSemanticInsight();
    semantic.setIntentSummary("ai");
    analysis.setSemantic(semantic);
    analysis.setOptimizedSql("SELECT id FROM t WHERE id = 1");
    when(llmClient.analyze(anyString(), anyString())).thenReturn(analysis);
    AiOptimizedSqlValidation validation = new AiOptimizedSqlValidation();
    validation.setPassed(true);
    when(aiOptimizedSqlValidator.validate(any(), anyString(), anyString())).thenReturn(validation);
    SqlAnalysisEnhanceResult result = llmEnhanceService.enhance(
        jdbcTemplate,
        "SELECT * FROM t",
        minimalStructure(),
        Collections.emptyList(),
        new ExplainPlan(),
        new PlanInterpretation(),
        new IndexAdviceReport(),
        new SqlRewriteResult());
    assertThat(result.isAiUsed()).isTrue();
    assertThat(result.getAnalysisSource()).isEqualTo(AnalysisSourceEnum.AI);
    assertThat(result.isOptimizedSqlAccepted()).isTrue();
    assertThat(result.getOptimizedSql()).isEqualTo("SELECT id FROM t WHERE id = 1");
  }

  @Test
  void discardAiSqlWhenValidationFails() {
    properties.getLlm().setEnabled(true);
    when(llmClient.isInvocationReady()).thenReturn(true);
    when(llmResponseCacheManager.get(anyString())).thenReturn(null);
    when(llmPromptAssembler.getSystemPrompt()).thenReturn("sys");
    when(llmPromptAssembler.buildUserMessage(
        anyString(),
        any(),
        any(),
        any(),
        any(),
        any(),
        any())).thenReturn("user");
    LlmStructuredAnalysis analysis = new LlmStructuredAnalysis();
    analysis.setSemantic(new SqlSemanticInsight());
    analysis.setOptimizedSql("SELECT bad");
    when(llmClient.analyze(anyString(), anyString())).thenReturn(analysis);
    SqlRewriteResult rewrite = new SqlRewriteResult();
    rewrite.setRecommendedSql("SELECT id FROM t");
    rewrite.setOriginalSql("SELECT * FROM t");
    AiOptimizedSqlValidation validation = new AiOptimizedSqlValidation();
    validation.setPassed(false);
    validation.setFailureReason("不等价");
    when(aiOptimizedSqlValidator.validate(any(), anyString(), anyString())).thenReturn(validation);
    SqlAnalysisEnhanceResult result = llmEnhanceService.enhance(
        jdbcTemplate,
        "SELECT * FROM t",
        minimalStructure(),
        Collections.emptyList(),
        new ExplainPlan(),
        new PlanInterpretation(),
        new IndexAdviceReport(),
        rewrite);
    assertThat(result.isOptimizedSqlAccepted()).isFalse();
    assertThat(result.getOptimizedSql()).isEqualTo("SELECT id FROM t");
    assertThat(result.getRewriteExplanation()).contains("不等价");
  }

  private static SqlStructure minimalStructure() {
    SqlStructure structure = new SqlStructure();
    SqlQueryBody body = new SqlQueryBody();
    structure.setBody(body);
    return structure;
  }
}
