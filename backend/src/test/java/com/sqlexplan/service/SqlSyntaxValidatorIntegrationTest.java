package com.sqlexplan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sqlexplan.config.SqlexplanProperties;
import com.sqlexplan.manager.TargetDataSourceManager;
import com.sqlexplan.manager.TargetExplainProbe;
import com.sqlexplan.model.enums.SqlValidateErrorTypeEnum;
import com.sqlexplan.model.res.SqlValidateRes;
import com.sqlexplan.support.DockerTestEnvironment;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 语义层集成测试（优先本机 3308 demo）。
 */
class SqlSyntaxValidatorIntegrationTest {

  static {
    DockerTestEnvironment.touch();
  }

  private static final String LOCAL_FALLBACK_HOST = "127.0.0.1";

  private static final int LOCAL_FALLBACK_PORT = 3308;

  private static JdbcTemplate jdbcTemplate;

  private static SqlSyntaxValidator validator;

  @BeforeAll
  static void init() {
    assumeTrue(resolveJdbcTemplate(), "无可用 MySQL demo 库（localhost:3308）");
    TargetExplainProbe probe = new TargetExplainProbe();
    validator = new SqlSyntaxValidator(new SqlSafetyChecker(), probe);
  }

  @Test
  void validSqlAgainstDemoUsers() {
    SqlValidateRes res = validator.validate("SELECT id, name FROM users LIMIT 5", jdbcTemplate, "demo");
    assertThat(res.getValid()).isTrue();
    assertThat(res.getInvolvedTables()).contains("users");
  }

  @Test
  void tableNotExistsWithSuggestion() {
    SqlValidateRes res = validator.validate("SELECT * FROM userz", jdbcTemplate, "demo");
    assertThat(res.getValid()).isFalse();
    assertThat(res.getErrorType()).isEqualTo(SqlValidateErrorTypeEnum.SEMANTIC);
    assertThat(res.getMysqlErrorCode()).isEqualTo(1146);
  }

  @Test
  void columnNotExists() {
    SqlValidateRes res = validator.validate("SELECT not_a_column FROM users", jdbcTemplate, "demo");
    assertThat(res.getValid()).isFalse();
    assertThat(res.getErrorType()).isEqualTo(SqlValidateErrorTypeEnum.SEMANTIC);
    assertThat(res.getMysqlErrorCode()).isEqualTo(1054);
  }

  @Test
  void parserUnsupportedJsonTableExplainOk() {
    String sql =
        "SELECT a FROM JSON_TABLE('[{\"a\":1}]', '$[*]' COLUMNS(a INT PATH '$.a')) AS t";
    SqlValidateRes res = validator.validate(sql, jdbcTemplate, "demo");
    assertThat(res.getValid()).isFalse();
    assertThat(res.getErrorType()).isEqualTo(SqlValidateErrorTypeEnum.PARSER_UNSUPPORTED);
  }

  private static boolean resolveJdbcTemplate() {
    String url =
        String.format(
            Locale.ROOT,
            "jdbc:mysql://%s:%d/demo?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
            LOCAL_FALLBACK_HOST,
            LOCAL_FALLBACK_PORT);
    try (Connection connection = DriverManager.getConnection(url, "root", "root")) {
      if (!connection.isValid(3)) {
        return false;
      }
      SqlexplanProperties properties = new SqlexplanProperties();
      TargetDataSourceManager manager = new TargetDataSourceManager(properties);
      jdbcTemplate =
          manager.createWritableJdbcTemplate(
              LOCAL_FALLBACK_HOST, LOCAL_FALLBACK_PORT, "demo", "root", "root");
      jdbcTemplate.setQueryTimeout(60);
      return true;
    } catch (Exception ex) {
      return false;
    }
  }
}
