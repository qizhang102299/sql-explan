package com.sqlexplan.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sqlexplan.manager.TargetExplainManager;
import com.sqlexplan.model.domain.ExplainPlan;
import com.sqlexplan.model.domain.PlanInterpretation;
import com.sqlexplan.model.domain.PlanIssue;
import com.sqlexplan.model.enums.PlanIssueSeverityEnum;
import com.sqlexplan.model.enums.PlanIssueTypeEnum;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.StreamUtils;

/**
 * {@link PlanExplanationBuilder} 单元测试。
 */
class PlanExplanationBuilderTest {

  private PlanExplanationBuilder builder;

  private ExplainService explainService;

  @BeforeEach
  void setUp() {
    explainService = new ExplainService(new TargetExplainManager());
    builder = new PlanExplanationBuilder(explainService);
  }

  @Test
  void narrateFullTableScanAndConclusion() throws IOException {
    ExplainPlan plan = explainService.parseExplainJson(loadJson("full_table_scan.json"));
    PlanInterpretation interpretation = new PlanInterpretation();
    PlanIssue issue = new PlanIssue();
    issue.setIssueType(PlanIssueTypeEnum.FULL_TABLE_SCAN);
    issue.setSeverity(PlanIssueSeverityEnum.CRITICAL);
    issue.setTitle("全表扫描");
    interpretation.getIssues().add(issue);
    interpretation.setSummary("共发现 1 个问题（严重 1，警告 0）");
    String narrative = builder.build(plan, interpretation);
    assertThat(narrative).contains("全表扫描");
    assertThat(narrative).contains("users");
    assertThat(narrative).contains("整体结论");
    assertThat(narrative).contains("改进建议");
  }

  @Test
  void narrateNestedLoopJoin() throws IOException {
    ExplainPlan plan = explainService.parseExplainJson(loadJson("nested_loop_join.json"));
    String narrative = builder.build(plan, new PlanInterpretation());
    assertThat(narrative).contains("嵌套循环");
    assertThat(narrative).contains("orders");
  }

  private String loadJson(String name) throws IOException {
    return StreamUtils.copyToString(
        getClass().getResourceAsStream("/explain/" + name),
        StandardCharsets.UTF_8);
  }
}
