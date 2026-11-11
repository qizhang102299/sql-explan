package com.sqlexplan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sqlexplan.manager.TargetExplainManager;
import com.sqlexplan.model.domain.ExplainPlan;
import com.sqlexplan.model.domain.PlanNode;
import com.sqlexplan.model.enums.PlanNodeKindEnum;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.StreamUtils;

/**
 * {@link ExplainService} 单元测试（样例 JSON，无数据库）。
 */
class ExplainServiceTest {

  private ExplainService explainService;

  @BeforeEach
  void setUp() {
    explainService = new ExplainService(new TargetExplainManager());
  }

  @Test
  void parseFullTableScan() throws IOException {
    ExplainPlan plan = explainService.parseExplainJson(loadJson("full_table_scan.json"));
    List<PlanNode> tables = explainService.collectTableNodes(plan.getRoot());
    assertThat(tables).hasSize(1);
    assertThat(tables.get(0).getTableName()).isEqualTo("users");
    assertThat(tables.get(0).getAccessType()).isEqualTo("ALL");
    assertThat(tables.get(0).getRowsExaminedPerScan()).isEqualTo(500_000L);
    assertThat(tables.get(0).getFilteredPercent()).isEqualTo(10.0);
  }

  @Test
  void parseIndexNotUsedShape() throws IOException {
    ExplainPlan plan = explainService.parseExplainJson(loadJson("index_not_used.json"));
    PlanNode table = explainService.collectTableNodes(plan.getRoot()).get(0);
    assertThat(table.getPossibleKeys()).containsExactly("idx_user_id", "idx_status");
    assertThat(table.getIndexKey()).isNull();
  }

  @Test
  void parseFilesortOperation() throws IOException {
    ExplainPlan plan = explainService.parseExplainJson(loadJson("filesort.json"));
    assertThat(plan.getRoot().getNodeKind()).isEqualTo(PlanNodeKindEnum.ORDERING);
    assertThat(plan.getRoot().getExtras()).contains("Using filesort");
    assertThat(plan.getRoot().getChildren()).hasSize(1);
    assertThat(plan.getRoot().getChildren().get(0).getTableName()).isEqualTo("orders");
  }

  @Test
  void parseTemporaryTableOperation() throws IOException {
    ExplainPlan plan = explainService.parseExplainJson(loadJson("temporary_table.json"));
    assertThat(plan.getRoot().getNodeKind()).isEqualTo(PlanNodeKindEnum.GROUPING);
    assertThat(plan.getRoot().getExtras()).contains("Using temporary table");
  }

  @Test
  void parseNestedLoopJoin() throws IOException {
    ExplainPlan plan = explainService.parseExplainJson(loadJson("nested_loop_join.json"));
    assertThat(plan.getRoot().getNodeKind()).isEqualTo(PlanNodeKindEnum.NESTED_LOOP);
    List<PlanNode> tables = explainService.collectTableNodes(plan.getRoot());
    assertThat(tables).hasSize(2);
    assertThat(tables.get(1).getAccessType()).isEqualTo("ALL");
  }

  @Test
  void parseRejectsInvalidJson() {
    assertThatThrownBy(() -> explainService.parseExplainJson("{not json"))
        .hasMessageContaining("解析失败");
  }

  private String loadJson(String name) throws IOException {
    return StreamUtils.copyToString(
        getClass().getResourceAsStream("/explain/" + name),
        StandardCharsets.UTF_8);
  }
}
