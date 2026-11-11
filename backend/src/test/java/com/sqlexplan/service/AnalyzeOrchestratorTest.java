package com.sqlexplan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.sqlexplan.manager.TargetJdbcResolver;
import com.sqlexplan.model.domain.AiOptimizedSqlValidation;
import com.sqlexplan.model.domain.BenchmarkReport;
import com.sqlexplan.model.domain.ExplainPlan;
import com.sqlexplan.model.domain.IndexAdviceReport;
import com.sqlexplan.model.domain.PlanInterpretation;
import com.sqlexplan.model.domain.ResultEquivalenceReport;
import com.sqlexplan.model.domain.SqlAnalysisEnhanceResult;
import com.sqlexplan.model.domain.SqlRewriteResult;
import com.sqlexplan.model.domain.SqlSemanticInsight;
import com.sqlexplan.model.domain.SqlStructure;
import com.sqlexplan.model.enums.AnalysisSourceEnum;
import com.sqlexplan.model.req.SqlAnalyzeReq;
import com.sqlexplan.model.res.SqlAnalyzeRes;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@link AnalyzeOrchestrator} 端到端编排单测（Mock 依赖）。
 */
@ExtendWith(MockitoExtension.class)
class AnalyzeOrchestratorTest {

  @Mock
  private SqlSyntaxValidator sqlSyntaxValidator;

  @Mock
  private SqlParseService sqlParseService;

  @Mock
  private SchemaMetaService schemaMetaService;

  @Mock
  private ExplainService explainService;

  @Mock
  private PlanInterpreter planInterpreter;

  @Mock
  private IndexAdvisor indexAdvisor;

  @Mock
  private SqlRewriter sqlRewriter;

  @Mock
  private RewriteCandidateValidator rewriteCandidateValidator;

  @Mock
  private LlmEnhanceService llmEnhanceService;

  @Mock
  private BenchmarkService benchmarkService;

  @Mock
  private JdbcTemplate jdbcTemplate;

  private AnalyzeOrchestrator analyzeOrchestrator;

  @BeforeEach
  void setUp() {
    analyzeOrchestrator = new AnalyzeOrchestrator(
        sqlSyntaxValidator,
        sqlParseService,
        schemaMetaService,
        explainService,
        planInterpreter,
        indexAdvisor,
        sqlRewriter,
        rewriteCandidateValidator,
        llmEnhanceService,
        benchmarkService);
  }

  @Test
  void orchestrateFullPipeline() {
    org.mockito.Mockito.doNothing().when(sqlSyntaxValidator).assertValid(anyString(), any(), anyString());

    SqlStructure structure = new SqlStructure();
    when(sqlParseService.parse(anyString())).thenReturn(structure);
    when(schemaMetaService.loadTableMetadata(any(), anyString(), any(), anyList()))
        .thenReturn(Collections.emptyList());
    when(explainService.explain(any(), anyString())).thenReturn(new ExplainPlan());
    when(planInterpreter.interpret(any(), any(), anyList())).thenReturn(new PlanInterpretation());
    when(indexAdvisor.advise(any(), anyList(), any())).thenReturn(new IndexAdviceReport());
    SqlRewriteResult rewrite = new SqlRewriteResult();
    rewrite.setRecommendedSql("SELECT id FROM t WHERE id = 1");
    when(sqlRewriter.rewrite(anyString(), any(), anyList())).thenReturn(rewrite);
    when(rewriteCandidateValidator.validatePassedOnly(any(), anyString(), any()))
        .thenReturn(Collections.emptyList());
    when(rewriteCandidateValidator.pickBestOptimizedSql(anyString(), any(), anyList()))
        .thenReturn("SELECT id FROM t WHERE id = 1");

    SqlAnalysisEnhanceResult enhance = new SqlAnalysisEnhanceResult();
    enhance.setAnalysisSource(AnalysisSourceEnum.LOCAL_RULES);
    enhance.setSemantic(new SqlSemanticInsight());
    enhance.setPlanNarrative("计划说明");
    enhance.setIndexAdviceReport(new IndexAdviceReport());
    enhance.setOptimizedSql("SELECT id FROM t WHERE id = 1");
    enhance.setOptimizedSqlAccepted(true);
    when(llmEnhanceService.enhance(any(), anyString(), any(), anyList(), any(), any(), any(), any()))
        .thenReturn(enhance);

    AiOptimizedSqlValidation validation = new AiOptimizedSqlValidation();
    validation.setPassed(true);
    validation.setEquivalencePassed(true);
    ResultEquivalenceReport eq = new ResultEquivalenceReport();
    eq.setEquivalent(true);
    validation.setEquivalenceReport(eq);
    when(rewriteCandidateValidator.validateFinal(any(), anyString(), anyString())).thenReturn(validation);

    BenchmarkReport bench = new BenchmarkReport();
    bench.setExecuted(true);
    when(benchmarkService.benchmark(any(), anyString(), anyString(), anyInt(), eq(false), any(), any()))
        .thenReturn(bench);

    SqlAnalyzeReq req = new SqlAnalyzeReq();
    req.setDataSourceId(1L);
    req.setSql("SELECT * FROM t");
    req.setEnableBenchmark(true);
    req.setVerifyIndexBenefit(false);

    TargetJdbcResolver.TargetConnectionContext ctx = new TargetJdbcResolver.TargetConnectionContext();
    ctx.setDataSourceId(1L);
    ctx.setJdbcTemplate(jdbcTemplate);
    ctx.setDbName("demo");

    SqlAnalyzeRes res = analyzeOrchestrator.analyze(req, ctx);
    assertThat(res.getAnalysisSource()).isEqualTo(AnalysisSourceEnum.LOCAL_RULES);
    assertThat(res.getPlan().getNarrative()).isEqualTo("计划说明");
    assertThat(res.getBenchmark()).isNotNull();
    assertThat(res.getCreatedTime()).isNotNull();
  }
}
