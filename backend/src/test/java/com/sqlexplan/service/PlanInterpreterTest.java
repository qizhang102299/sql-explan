package com.sqlexplan.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sqlexplan.config.SqlexplanProperties;
import com.sqlexplan.manager.TargetExplainManager;
import com.sqlexplan.model.domain.ColumnMeta;
import com.sqlexplan.model.domain.PlanInterpretation;
import com.sqlexplan.model.domain.SqlStructure;
import com.sqlexplan.model.domain.TableSchemaMeta;
import com.sqlexplan.model.enums.PlanIssueSeverityEnum;
import com.sqlexplan.model.enums.PlanIssueTypeEnum;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.StreamUtils;

/**
 * {@link PlanInterpreter} 单元测试。
 */
class PlanInterpreterTest {

  private ExplainService explainService;

  private PlanInterpreter planInterpreter;

  private SqlParseService sqlParseService;

  @BeforeEach
  void setUp() {
    SqlexplanProperties properties = new SqlexplanProperties();
    explainService = new ExplainService(new TargetExplainManager());
    planInterpreter = new PlanInterpreter(properties, explainService);
    sqlParseService = new SqlParseService();
  }

  @Test
  void detectsFullTableScanAndLargeRows() throws IOException {
    var plan = explainService.parseExplainJson(loadJson("full_table_scan.json"));
    PlanInterpretation interpretation = planInterpreter.interpret(plan, null);
    assertThat(interpretation.getIssues())
        .extracting("issueType")
        .contains(PlanIssueTypeEnum.FULL_TABLE_SCAN, PlanIssueTypeEnum.LARGE_ROWS_EXAMINED);
  }

  @Test
  void detectsIndexNotUsed() throws IOException {
    var plan = explainService.parseExplainJson(loadJson("index_not_used.json"));
    PlanInterpretation interpretation = planInterpreter.interpret(plan, null);
    assertThat(interpretation.getIssues())
        .anyMatch(i -> i.getIssueType() == PlanIssueTypeEnum.INDEX_NOT_USED
            || i.getIssueType() == PlanIssueTypeEnum.FULL_TABLE_SCAN);
    assertThat(interpretation.getIssues())
        .anyMatch(i -> i.getIssueType() == PlanIssueTypeEnum.INDEX_NOT_USED);
  }

  @Test
  void detectsFilesort() throws IOException {
    var plan = explainService.parseExplainJson(loadJson("filesort.json"));
    PlanInterpretation interpretation = planInterpreter.interpret(plan, null);
    assertThat(interpretation.getIssues())
        .anyMatch(i -> i.getIssueType() == PlanIssueTypeEnum.FILESORT);
  }

  @Test
  void detectsTemporaryTable() throws IOException {
    var plan = explainService.parseExplainJson(loadJson("temporary_table.json"));
    PlanInterpretation interpretation = planInterpreter.interpret(plan, null);
    assertThat(interpretation.getIssues())
        .anyMatch(i -> i.getIssueType() == PlanIssueTypeEnum.TEMPORARY_TABLE);
  }

  @Test
  void detectsDeepPaginationFromSqlStructure() {
    String sql = "SELECT id FROM users WHERE status = 1 ORDER BY id LIMIT 20 OFFSET 50000";
    SqlStructure structure = sqlParseService.parse(sql);
    PlanInterpretation interpretation = planInterpreter.interpret(null, structure);
    assertThat(interpretation.getIssues())
        .anyMatch(i -> i.getIssueType() == PlanIssueTypeEnum.DEEP_PAGINATION);
  }

  @Test
  void detectsFunctionOnColumnAndImplicitConversion() {
    String sql = """
        SELECT id FROM users
        WHERE YEAR(created_at) = 2024 AND id = '12345'
        """;
    SqlStructure structure = sqlParseService.parse(sql);
    TableSchemaMeta users = usersMeta();
    PlanInterpretation interpretation = planInterpreter.interpret(null, structure, List.of(users));
    assertThat(interpretation.getIssues())
        .anyMatch(i -> i.getIssueType() == PlanIssueTypeEnum.INDEX_COLUMN_FUNCTION);
    assertThat(interpretation.getIssues())
        .anyMatch(i -> i.getIssueType() == PlanIssueTypeEnum.IMPLICIT_TYPE_CONVERSION);
  }

  @Test
  void noImplicitConversionWhenNumericColumnComparedToUnquotedLiteral() {
    String sql = "SELECT id FROM users WHERE user_id = 123 AND status = 1";
    SqlStructure structure = sqlParseService.parse(sql);
    PlanInterpretation interpretation = planInterpreter.interpret(null, structure, List.of(usersMeta()));
    assertThat(interpretation.getIssues())
        .noneMatch(i -> i.getIssueType() == PlanIssueTypeEnum.IMPLICIT_TYPE_CONVERSION);
  }

  @Test
  void noImplicitConversionWithoutTableMetadata() {
    String sql = "SELECT id FROM users WHERE status = 1";
    SqlStructure structure = sqlParseService.parse(sql);
    PlanInterpretation interpretation = planInterpreter.interpret(null, structure);
    assertThat(interpretation.getIssues())
        .noneMatch(i -> i.getIssueType() == PlanIssueTypeEnum.IMPLICIT_TYPE_CONVERSION);
  }

  @Test
  void detectsImplicitConversionWhenVarcharComparedToUnquotedNumeric() {
    String sql = "SELECT id FROM users WHERE account_no = 10001";
    SqlStructure structure = sqlParseService.parse(sql);
    ColumnMeta accountNo = new ColumnMeta();
    accountNo.setColumnName("account_no");
    accountNo.setDataType("varchar");
    TableSchemaMeta users = usersMeta(accountNo);
    PlanInterpretation interpretation = planInterpreter.interpret(null, structure, List.of(users));
    assertThat(interpretation.getIssues())
        .anyMatch(i -> i.getIssueType() == PlanIssueTypeEnum.IMPLICIT_TYPE_CONVERSION);
  }

  @Test
  void nestedLoopFlagsWorstTable() throws IOException {
    var plan = explainService.parseExplainJson(loadJson("nested_loop_join.json"));
    PlanInterpretation interpretation = planInterpreter.interpret(plan, null);
    assertThat(interpretation.getIssues())
        .filteredOn(i -> i.getRelatedTable() != null && i.getRelatedTable().equals("orders"))
        .extracting("issueType")
        .contains(PlanIssueTypeEnum.FULL_TABLE_SCAN, PlanIssueTypeEnum.INDEX_NOT_USED);
  }

  @Test
  void criticalIssuesElevateSummary() throws IOException {
    var plan = explainService.parseExplainJson(loadJson("full_table_scan.json"));
    PlanInterpretation interpretation = planInterpreter.interpret(plan, null);
    assertThat(interpretation.getIssues()).isNotEmpty();
    assertThat(interpretation.getIssues())
        .anyMatch(i -> i.getSeverity() == PlanIssueSeverityEnum.CRITICAL);
    assertThat(interpretation.getSummary()).contains("严重");
  }

  private String loadJson(String name) throws IOException {
    return StreamUtils.copyToString(
        getClass().getResourceAsStream("/explain/" + name),
        StandardCharsets.UTF_8);
  }

  private static TableSchemaMeta usersMeta(ColumnMeta... extras) {
    TableSchemaMeta meta = new TableSchemaMeta();
    meta.setTableName("users");
    List<ColumnMeta> columns = new ArrayList<>();
    ColumnMeta id = new ColumnMeta();
    id.setColumnName("id");
    id.setDataType("bigint");
    columns.add(id);
    ColumnMeta userId = new ColumnMeta();
    userId.setColumnName("user_id");
    userId.setDataType("bigint");
    columns.add(userId);
    ColumnMeta status = new ColumnMeta();
    status.setColumnName("status");
    status.setDataType("int");
    columns.add(status);
    for (ColumnMeta extra : extras) {
      columns.add(extra);
    }
    meta.setColumns(columns);
    return meta;
  }
}
