package com.sqlexplan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sqlexplan.manager.TargetExplainProbe;
import com.sqlexplan.model.enums.SqlValidateErrorTypeEnum;
import com.sqlexplan.model.res.SqlValidateRes;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@link SqlSyntaxValidator} 单元测试。
 */
class SqlSyntaxValidatorTest {

  private SqlSyntaxValidator validator;

  private TargetExplainProbe explainProbe;

  @BeforeEach
  void setUp() {
    explainProbe = mock(TargetExplainProbe.class);
    validator = new SqlSyntaxValidator(new SqlSafetyChecker(), explainProbe);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidSyntaxCases")
  void rejectsSyntaxErrors(String name, String sql, String messagePart) {
    SqlValidateRes res = validator.validate(sql);
    assertThat(res.getValid()).isFalse();
    assertThat(res.getErrorType()).isEqualTo(SqlValidateErrorTypeEnum.SYNTAX);
    assertThat(res.getMessage()).as(name).contains(messagePart);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidSafetyCases")
  void rejectsSafetyViolations(String name, String sql) {
    SqlValidateRes res = validator.validate(sql);
    assertThat(res.getValid()).isFalse();
    assertThat(res.getErrorType()).isEqualTo(SqlValidateErrorTypeEnum.SAFETY);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("validSyntaxCases")
  void acceptsValidSelect(String name, String sql) {
    SqlValidateRes res = validator.validate(sql);
    assertThat(res.getValid()).as(name).isTrue();
    assertThat(res.getStatementType()).isEqualTo("SELECT");
  }

  @Test
  void keywordTypoSuggestsSelect() {
    SqlValidateRes res = validator.validate("SELEC * FROM users");
    assertThat(res.getValid()).isFalse();
    assertThat(res.getSuggestions().stream().anyMatch(s -> s.contains("SELECT"))).isTrue();
  }

  @Test
  void parserUnsupportedWhenExplainSucceeds() {
    String sql =
        "SELECT a FROM JSON_TABLE('[{\"a\":1}]', '$[*]' COLUMNS(a INT PATH '$.a')) AS t";
    when(explainProbe.probe(org.mockito.ArgumentMatchers.any(), anyString()))
        .thenReturn(TargetExplainProbe.ExplainProbeResult.ok());
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    SqlValidateRes res = validator.validate(sql, jdbc, "demo");
    assertThat(res.getValid()).isFalse();
    assertThat(res.getErrorType()).isEqualTo(SqlValidateErrorTypeEnum.PARSER_UNSUPPORTED);
  }

  @Test
  void semanticTableNotFound() {
    when(explainProbe.probe(org.mockito.ArgumentMatchers.any(), anyString()))
        .thenReturn(
            TargetExplainProbe.ExplainProbeResult.failed(
                new java.sql.SQLException("Table 'demo.nosuch' doesn't exist", "42S02", 1146),
                "Table 'demo.nosuch' doesn't exist"));
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForList(
            org.mockito.ArgumentMatchers.eq(
                "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = ?"),
            org.mockito.ArgumentMatchers.eq(String.class),
            org.mockito.ArgumentMatchers.eq("demo")))
        .thenReturn(java.util.List.of("users", "orders"));
    SqlValidateRes res = validator.validate("SELECT * FROM nosuch", jdbc, "demo");
    assertThat(res.getValid()).isFalse();
    assertThat(res.getErrorType()).isEqualTo(SqlValidateErrorTypeEnum.SEMANTIC);
    assertThat(res.getMysqlErrorCode()).isEqualTo(1146);
  }

  @Test
  void emptySqlRejected() {
    SqlValidateRes res = validator.validate("   ");
    assertThat(res.getValid()).isFalse();
    assertThat(res.getMessage()).contains("不能为空");
  }

  private static Stream<Arguments> invalidSyntaxCases() {
    return Stream.of(
        Arguments.of("关键字后缺表达式", "SELECT * FROM users WHERE", "WHERE"),
        Arguments.of("括号未闭合", "SELECT (id FROM users", "括号"),
        Arguments.of("引号未闭合", "SELECT 'x FROM users", "引号"),
        Arguments.of("末尾多余逗号", "SELECT id, FROM users", "多余"),
        Arguments.of("拼写错误", "SELEC 1", "语法错误"),
        Arguments.of("GROUP BY 缺表达式", "SELECT id FROM users GROUP BY", "GROUP BY"));
  }

  private static Stream<Arguments> invalidSafetyCases() {
    return Stream.of(
        Arguments.of("多语句", "SELECT 1; SELECT 2"),
        Arguments.of("INSERT", "INSERT INTO users(name) VALUES('a')"),
        Arguments.of("UPDATE", "UPDATE users SET name='x'"),
        Arguments.of("DELETE", "DELETE FROM users"),
        Arguments.of("DROP", "DROP TABLE users"),
        Arguments.of("SLEEP", "SELECT SLEEP(1)"),
        Arguments.of("多语句含 DDL", "SELECT 1; DROP TABLE users"));
  }

  private static Stream<Arguments> validSyntaxCases() {
    return Stream.of(
        Arguments.of("简单", "SELECT 1"),
        Arguments.of("带分号", "SELECT id FROM users;"),
        Arguments.of("CTE", "WITH c AS (SELECT 1 x) SELECT x FROM c"),
        Arguments.of("窗口函数", "SELECT ROW_NUMBER() OVER (ORDER BY id) rn FROM users"),
        Arguments.of("UNION", "SELECT id FROM users UNION SELECT id FROM orders"),
        Arguments.of("子查询", "SELECT * FROM users WHERE id IN (SELECT user_id FROM orders)"),
        Arguments.of("JOIN", "SELECT u.id FROM users u JOIN orders o ON u.id = o.user_id"));
  }
}
