package com.sqlexplan.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sqlexplan.config.SqlexplanProperties;
import com.sqlexplan.model.domain.ColumnMeta;
import com.sqlexplan.model.domain.IndexAdvice;
import com.sqlexplan.model.domain.IndexAdviceReport;
import com.sqlexplan.model.domain.PlanInterpretation;
import com.sqlexplan.model.domain.PlanIssue;
import com.sqlexplan.model.domain.IndexColumnMeta;
import com.sqlexplan.model.domain.IndexMeta;
import com.sqlexplan.model.domain.SqlRewriteResult;
import com.sqlexplan.model.domain.SqlRewriteSuggestion;
import com.sqlexplan.model.domain.SqlStructure;
import com.sqlexplan.model.domain.TableSchemaMeta;
import com.sqlexplan.model.enums.IndexAdviceTypeEnum;
import com.sqlexplan.model.enums.PlanIssueTypeEnum;
import com.sqlexplan.model.enums.SqlRewriteRuleTypeEnum;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * 坏 SQL 用例集：断言索引建议与规则改写（至少 20 条）。
 */
class BadSqlOptimizationTest {

  private SqlParseService sqlParseService;

  private IndexAdvisor indexAdvisor;

  private SqlRewriter sqlRewriter;

  private PlanInterpreter planInterpreter;

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    sqlParseService = new SqlParseService();
    indexAdvisor = new IndexAdvisor();
    SqlexplanProperties properties = new SqlexplanProperties();
    properties.getExplain().setDeepPaginationOffsetThreshold(10_000L);
    sqlRewriter = new SqlRewriter(properties);
    planInterpreter = new PlanInterpreter(properties, new ExplainService(null));
    objectMapper = new ObjectMapper();
  }

  /**
   * 从 classpath 加载坏 SQL 用例并逐条断言。
   *
   * @return 动态测试列表
   */
  @TestFactory
  List<DynamicTest> badSqlCases() throws IOException {
    List<DynamicTest> tests = new ArrayList<>();
    try (InputStream in = getClass().getResourceAsStream("/bad-sql/bad_sql_cases.json")) {
      assertThat(in).as("bad_sql_cases.json 必须存在").isNotNull();
      JsonNode root = objectMapper.readTree(in);
      assertThat(root.isArray()).isTrue();
      assertThat(root.size()).isGreaterThanOrEqualTo(40);
      Iterator<JsonNode> it = root.elements();
      while (it.hasNext()) {
        JsonNode node = it.next();
        String id = node.get("id").asText();
        tests.add(
            DynamicTest.dynamicTest(id, () -> runCase(node)));
      }
    }
    return tests;
  }

  private void runCase(JsonNode node) {
    String sql = node.get("sql").asText();
    List<TableSchemaMeta> tables = buildTables(node.get("tables"));
    SqlStructure structure = sqlParseService.parse(sql);

    IndexAdviceReport indexReport = indexAdvisor.advise(structure, tables, null);
    assertIndexExpectations(node, indexReport);

    SqlRewriteResult rewriteResult = sqlRewriter.rewrite(sql, structure, tables);
    assertRewriteExpectations(node, rewriteResult);

    if (node.has("planIssueTypes")) {
      PlanInterpretation interpretation = planInterpreter.interpret(null, structure, tables);
      assertPlanIssueExpectations(node, interpretation);
    }
  }

  private void assertPlanIssueExpectations(JsonNode node, PlanInterpretation interpretation) {
    List<PlanIssueTypeEnum> types =
        interpretation.getIssues().stream().map(PlanIssue::getIssueType).toList();
    for (JsonNode typeNode : node.get("planIssueTypes")) {
      PlanIssueTypeEnum expected = PlanIssueTypeEnum.valueOf(typeNode.asText());
      assertThat(types)
          .as("用例 " + node.get("id").asText() + " 应命中计划问题 " + expected)
          .contains(expected);
    }
  }

  private void assertIndexExpectations(JsonNode node, IndexAdviceReport report) {
    if (node.has("indexDdlPatterns")) {
      List<String> ddls =
          report.getAdvices().stream()
              .map(IndexAdvice::getCreateIndexDdl)
              .filter(d -> d != null && !d.isBlank())
              .toList();
      for (JsonNode patternNode : node.get("indexDdlPatterns")) {
        String pattern = patternNode.asText();
        assertThat(ddls)
            .as("用例 " + node.get("id").asText() + " 应包含匹配 DDL: " + pattern)
            .anyMatch(ddl -> Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(ddl).find());
      }
    }
    if (node.has("indexAdviceTypes")) {
      List<IndexAdviceTypeEnum> types =
          report.getAdvices().stream().map(IndexAdvice::getAdviceType).toList();
      for (JsonNode typeNode : node.get("indexAdviceTypes")) {
        IndexAdviceTypeEnum expected =
            IndexAdviceTypeEnum.valueOf(typeNode.asText());
        assertThat(types)
            .as("用例 " + node.get("id").asText() + " 应含建议类型 " + expected)
            .contains(expected);
      }
    }
  }

  private void assertRewriteExpectations(JsonNode node, SqlRewriteResult result) {
    if (!node.has("rewriteRules") && !node.has("rewriteSqlPatterns")) {
      return;
    }
    List<SqlRewriteRuleTypeEnum> ruleTypes =
        result.getSuggestions().stream()
            .map(SqlRewriteSuggestion::getRuleType)
            .toList();
    if (node.has("rewriteRules")) {
      for (JsonNode ruleNode : node.get("rewriteRules")) {
        SqlRewriteRuleTypeEnum expected =
            SqlRewriteRuleTypeEnum.valueOf(ruleNode.asText());
        assertThat(ruleTypes)
            .as("用例 " + node.get("id").asText() + " 应触发改写规则 " + expected)
            .contains(expected);
      }
    }
    if (node.has("rewriteSqlPatterns")) {
      List<String> sqlTexts = new ArrayList<>();
      sqlTexts.add(result.getRecommendedSql());
      result.getSuggestions().stream()
          .map(SqlRewriteSuggestion::getRewrittenSql)
          .filter(s -> s != null && !s.isBlank())
          .forEach(sqlTexts::add);
      for (JsonNode patternNode : node.get("rewriteSqlPatterns")) {
        String pattern = patternNode.asText();
        assertThat(sqlTexts)
            .as("用例 " + node.get("id").asText() + " 改写 SQL 应匹配: " + pattern)
            .anyMatch(
                sql -> Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
                    .matcher(sql)
                    .find());
      }
    }
  }

  private List<TableSchemaMeta> buildTables(JsonNode tablesNode) {
    List<TableSchemaMeta> list = new ArrayList<>();
    if (tablesNode == null || !tablesNode.isArray()) {
      return list;
    }
    for (JsonNode tableNode : tablesNode) {
      TableSchemaMeta meta = new TableSchemaMeta();
      meta.setTableName(tableNode.get("tableName").asText());
      if (tableNode.has("estimatedRows")) {
        meta.setEstimatedRows(tableNode.get("estimatedRows").asLong());
      }
      if (tableNode.has("columns")) {
        int seq = 1;
        for (JsonNode col : tableNode.get("columns")) {
          ColumnMeta columnMeta = new ColumnMeta();
          if (col.isTextual()) {
            columnMeta.setColumnName(col.asText());
          } else {
            columnMeta.setColumnName(col.get("name").asText());
            if (col.has("dataType")) {
              columnMeta.setDataType(col.get("dataType").asText());
            }
          }
          columnMeta.setOrdinalPosition(seq++);
          meta.getColumns().add(columnMeta);
        }
      }
      if (tableNode.has("indexes")) {
        for (JsonNode idxNode : tableNode.get("indexes")) {
          IndexMeta indexMeta = new IndexMeta();
          indexMeta.setIndexName(idxNode.get("indexName").asText());
          indexMeta.setUnique("PRIMARY".equalsIgnoreCase(idxNode.get("indexName").asText()));
          int seq = 1;
          for (JsonNode col : idxNode.get("columns")) {
            IndexColumnMeta indexColumnMeta = new IndexColumnMeta();
            indexColumnMeta.setColumnName(col.asText());
            indexColumnMeta.setSeqInIndex(seq++);
            indexMeta.getColumns().add(indexColumnMeta);
          }
          meta.getIndexes().add(indexMeta);
        }
      }
      list.add(meta);
    }
    return list;
  }
}
