package com.sqlexplan.service;

import com.sqlexplan.constant.SqlSafetyConstants;
import com.sqlexplan.exception.BizException;
import com.sqlexplan.manager.TargetExplainProbe;
import com.sqlexplan.model.enums.SqlValidateErrorTypeEnum;
import com.sqlexplan.model.res.SqlSafetyCheckRes;
import com.sqlexplan.model.res.SqlValidateRes;
import com.sqlexplan.util.JsqlParserErrorUtil;
import com.sqlexplan.util.JsqlParserErrorUtil.SyntaxParseOutcome;
import com.sqlexplan.util.MySqlSemanticErrorUtil;
import com.sqlexplan.util.MySqlSemanticErrorUtil.SemanticErrorOutcome;
import com.sqlexplan.util.SqlTextUtil;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * SQL 三层校验：语法、安全、语义（可选 EXPLAIN）。
 */
@Service
public class SqlSyntaxValidator {

  private static final String EMPTY_SQL_MESSAGE = "SQL 不能为空";

  private final SqlSafetyChecker sqlSafetyChecker;

  private final TargetExplainProbe targetExplainProbe;

  public SqlSyntaxValidator(SqlSafetyChecker sqlSafetyChecker, TargetExplainProbe targetExplainProbe) {
    this.sqlSafetyChecker = sqlSafetyChecker;
    this.targetExplainProbe = targetExplainProbe;
  }

  /**
   * 校验 SQL，不连接目标库时仅执行语法与安全层。
   *
   * @param sql SQL
   * @return 校验结果
   */
  public SqlValidateRes validate(String sql) {
    return validateInternal(sql, null, null);
  }

  /**
   * 完整校验，可在传入 JdbcTemplate 时执行语义层。
   *
   * @param sql        SQL
   * @param jdbc       目标库只读模板，可为 null
   * @param schemaName 库名，语义层用于拉取表名列名
   * @return 校验结果
   */
  public SqlValidateRes validate(String sql, JdbcTemplate jdbc, String schemaName) {
    return validateInternal(sql, jdbc, schemaName);
  }

  /**
   * 校验并在失败时抛出携带结构化信息的业务异常。
   *
   * @param sql        SQL
   * @param jdbc       目标库
   * @param schemaName 库名
   */
  public void assertValid(String sql, JdbcTemplate jdbc, String schemaName) {
    SqlValidateRes res = validateInternal(sql, jdbc, schemaName);
    if (!Boolean.TRUE.equals(res.getValid())) {
      throw BizException.validationFailed(res);
    }
  }

  /**
   * 解析失败后，按优先级匹配可精确定位的常见语法错误。
   *
   * @param sql 规范化后的 SQL
   * @return 带位置的错误，未命中返回 null
   */
  private static JsqlParserErrorUtil.LocatedSyntaxError locateKnownSyntaxError(String sql) {
    JsqlParserErrorUtil.LocatedSyntaxError err = JsqlParserErrorUtil.locateUnbalanced(sql);
    if (err != null) {
      return err;
    }
    err = JsqlParserErrorUtil.locateTrailingCommaInSelectList(sql);
    if (err != null) {
      return err;
    }
    return JsqlParserErrorUtil.locateClauseMissingExpression(sql);
  }

  private SqlValidateRes validateInternal(String sql, JdbcTemplate jdbc, String schemaName) {
    SqlValidateRes res = new SqlValidateRes();
    if (!StringUtils.hasText(sql)) {
      return failure(res, SqlValidateErrorTypeEnum.SYNTAX, EMPTY_SQL_MESSAGE, 1, 1, null, null, null);
    }
    String normalized = SqlTextUtil.stripTrailingSemicolons(sql.trim());

    Statement statement;
    try {
      Statements statements = CCJSqlParserUtil.parseStatements(normalized);
      if (statements.getStatements().isEmpty()) {
        return failure(res, SqlValidateErrorTypeEnum.SYNTAX, "未解析到有效 SQL 语句", 1, 1, null, null, null);
      }
      statement = statements.getStatements().get(0);
    } catch (JSQLParserException ex) {
      JsqlParserErrorUtil.LocatedSyntaxError located = locateKnownSyntaxError(normalized);
      if (located != null) {
        failure(
            res,
            SqlValidateErrorTypeEnum.SYNTAX,
            located.message(),
            located.line(),
            located.column(),
            null,
            null,
            null);
        res.setEndLine(located.endLine());
        res.setEndColumn(located.endColumn());
        return res;
      }
      if (jdbc != null) {
        TargetExplainProbe.ExplainProbeResult probe = targetExplainProbe.probe(jdbc, normalized);
        if (probe.isSuccess()) {
          return failure(
              res,
              SqlValidateErrorTypeEnum.PARSER_UNSUPPORTED,
              "语法在 MySQL 中合法，但本系统解析器暂不支持，无法安全分析",
              null,
              null,
              null,
              null,
              null);
        }
      }
      SyntaxParseOutcome outcome = JsqlParserErrorUtil.describeSyntaxFailure(normalized, ex);
      failure(
          res,
          SqlValidateErrorTypeEnum.SYNTAX,
          outcome.message(),
          outcome.line(),
          outcome.column(),
          outcome.token(),
          null,
          outcome.suggestions());
      if (outcome.token() != null) {
        res.setEndLine(outcome.line());
        res.setEndColumn(outcome.column() + outcome.token().length());
      }
      return res;
    }

    SqlSafetyCheckRes safety = sqlSafetyChecker.check(normalized);
    if (!Boolean.TRUE.equals(safety.getPassed())) {
      String reason = safety.getReason() != null ? safety.getReason() : "SQL 安全校验未通过";
      if (reason.startsWith(SqlSafetyConstants.REJECT_PARSE_FAILED)) {
        return failure(res, SqlValidateErrorTypeEnum.SYNTAX, "SQL 语法无法解析", 1, 1, null, null, null);
      }
      return failure(res, SqlValidateErrorTypeEnum.SAFETY, reason, null, null, null, null, null);
    }

    res.setStatementType(statement.getClass().getSimpleName().toUpperCase(Locale.ROOT));
    if (statement instanceof Select) {
      res.setStatementType("SELECT");
    }
    res.setInvolvedTables(extractTableNames(statement));

    if (jdbc == null) {
      return success(res);
    }

    TargetExplainProbe.ExplainProbeResult probe = targetExplainProbe.probe(jdbc, normalized);
    if (probe.isSuccess()) {
      return success(res);
    }

    SQLException sqlEx = MySqlSemanticErrorUtil.findSqlException(probe.getError());
    if (sqlEx == null) {
      return failure(
          res,
          SqlValidateErrorTypeEnum.SEMANTIC,
          probe.getMessage() != null ? probe.getMessage() : "EXPLAIN 执行失败",
          null,
          null,
          null,
          null,
          null);
    }
    List<String> tables = listSchemaTables(jdbc, schemaName);
    List<String> columns = listColumnsForInvolvedTables(jdbc, schemaName, res.getInvolvedTables());
    SemanticErrorOutcome mapped = MySqlSemanticErrorUtil.map(sqlEx, tables, columns);
    SqlValidateErrorTypeEnum type = sqlEx.getErrorCode() == 1064
        ? SqlValidateErrorTypeEnum.SYNTAX
        : SqlValidateErrorTypeEnum.SEMANTIC;
    return failure(
        res,
        type,
        mapped.message(),
        null,
        null,
        null,
        mapped.mysqlErrorCode(),
        mapped.suggestions());
  }

  private SqlValidateRes success(SqlValidateRes res) {
    res.setValid(true);
    res.setErrorType(null);
    res.setMessage(null);
    return res;
  }

  private SqlValidateRes failure(
      SqlValidateRes res,
      SqlValidateErrorTypeEnum errorType,
      String message,
      Integer line,
      Integer column,
      String token,
      Integer mysqlErrorCode,
      List<String> suggestions) {
    res.setValid(false);
    res.setErrorType(errorType);
    res.setMessage(message);
    res.setLine(line);
    res.setColumn(column);
    res.setEndLine(line);
    res.setEndColumn(column);
    res.setToken(token);
    res.setMysqlErrorCode(mysqlErrorCode);
    if (suggestions != null) {
      res.setSuggestions(new ArrayList<>(suggestions));
    }
    return res;
  }

  private List<String> extractTableNames(Statement statement) {
    try {
      TablesNamesFinder finder = new TablesNamesFinder();
      return new ArrayList<>(finder.getTables(statement));
    } catch (Exception ex) {
      return new ArrayList<>();
    }
  }

  private List<String> listSchemaTables(JdbcTemplate jdbc, String schemaName) {
    if (jdbc == null || !StringUtils.hasText(schemaName)) {
      return List.of();
    }
    try {
      return jdbc.queryForList(
          "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = ?",
          String.class,
          schemaName);
    } catch (Exception ex) {
      return List.of();
    }
  }

  private List<String> listColumnsForInvolvedTables(
      JdbcTemplate jdbc, String schemaName, List<String> tables) {
    if (jdbc == null || !StringUtils.hasText(schemaName) || tables == null || tables.isEmpty()) {
      return List.of();
    }
    List<String> cols = new ArrayList<>();
    for (String table : tables) {
      if (!StringUtils.hasText(table)) {
        continue;
      }
      try {
        cols.addAll(
            jdbc.queryForList(
                "SELECT COLUMN_NAME FROM information_schema.COLUMNS "
                    + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?",
                String.class,
                schemaName,
                table));
      } catch (Exception ignored) {
        // 单表失败不影响其余建议
      }
    }
    return cols;
  }
}
