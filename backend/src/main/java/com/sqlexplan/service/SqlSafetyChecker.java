package com.sqlexplan.service;

import com.sqlexplan.constant.SqlSafetyConstants;
import com.sqlexplan.model.res.SqlSafetyCheckRes;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.WithItem;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 目标库 SQL 安全校验：仅允许单条 SELECT，并拦截危险写法。
 */
@Service
public class SqlSafetyChecker {

  private static final Pattern FORBIDDEN_KEYWORD_PATTERN = Pattern.compile(
      "(?i)\\b(INSERT|UPDATE|DELETE|DROP|CREATE|ALTER|TRUNCATE|REPLACE|GRANT|REVOKE|CALL|"
          + "EXECUTE|EXEC|LOAD\\s+DATA|INTO\\s+OUTFILE|INTO\\s+DUMPFILE)\\b");

  private static final Pattern LOCK_IN_SHARE_MODE_PATTERN =
      Pattern.compile("(?i)\\bLOCK\\s+IN\\s+SHARE\\s+MODE\\b");

  private static final Pattern SELECT_INTO_USER_VAR_PATTERN =
      Pattern.compile("(?i)\\bSELECT\\b[\\s\\S]+\\bINTO\\s+@");

  private static final Pattern DANGEROUS_FUNCTION_CALL_PATTERN =
      Pattern.compile("(?i)\\b(SLEEP|BENCHMARK|LOAD_FILE)\\s*\\(");

  /**
   * 校验 SQL 是否可安全在目标只读库执行。
   *
   * @param sql 待校验 SQL
   * @return 校验结果
   */
  public SqlSafetyCheckRes check(String sql) {
    SqlSafetyCheckRes res = new SqlSafetyCheckRes();
    if (!StringUtils.hasText(sql)) {
      res.setPassed(false);
      res.setReason(SqlSafetyConstants.REJECT_EMPTY);
      return res;
    }
    String trimmed = sql.trim();
    String keywordReject = matchForbiddenKeywords(trimmed);
    if (keywordReject != null) {
      res.setPassed(false);
      res.setReason(keywordReject);
      return res;
    }
    if (LOCK_IN_SHARE_MODE_PATTERN.matcher(trimmed).find()) {
      res.setPassed(false);
      res.setReason(SqlSafetyConstants.REJECT_LOCK_IN_SHARE_MODE);
      return res;
    }
    if (SELECT_INTO_USER_VAR_PATTERN.matcher(trimmed).find()) {
      res.setPassed(false);
      res.setReason(SqlSafetyConstants.REJECT_INTO_VARIABLE);
      return res;
    }
    String dangerousFunction = matchDangerousFunctionCall(trimmed);
    if (dangerousFunction != null) {
      res.setPassed(false);
      res.setReason(dangerousFunction);
      return res;
    }
    Statements statements;
    try {
      statements = CCJSqlParserUtil.parseStatements(trimmed);
    } catch (JSQLParserException ex) {
      res.setPassed(false);
      res.setReason(SqlSafetyConstants.REJECT_PARSE_FAILED + ": " + ex.getMessage());
      return res;
    }
    List<Statement> statementList = statements.getStatements();
    if (statementList.size() != 1) {
      res.setPassed(false);
      res.setReason(SqlSafetyConstants.REJECT_MULTI_STATEMENT);
      return res;
    }
    Statement statement = statementList.get(0);
    if (!(statement instanceof Select select)) {
      res.setPassed(false);
      res.setReason(SqlSafetyConstants.REJECT_NOT_SELECT);
      return res;
    }
    String selectReject = validateSelectTree(select);
    if (selectReject != null) {
      res.setPassed(false);
      res.setReason(selectReject);
      return res;
    }
    res.setPassed(true);
    res.setReason(null);
    return res;
  }

  /**
   * 校验不通过时抛出业务异常（供编排层使用）。
   *
   * @param sql 待校验 SQL
   */
  public void assertSafe(String sql) {
    SqlSafetyCheckRes checkRes = check(sql);
    if (!Boolean.TRUE.equals(checkRes.getPassed())) {
      throw new com.sqlexplan.exception.BizException(
          checkRes.getReason() != null ? checkRes.getReason() : "SQL 安全校验未通过");
    }
  }

  private String matchForbiddenKeywords(String sql) {
    if (FORBIDDEN_KEYWORD_PATTERN.matcher(sql).find()) {
      return SqlSafetyConstants.REJECT_KEYWORD_PATTERN + "禁止的 DML/DDL 或文件导出写法";
    }
    return null;
  }

  private String matchDangerousFunctionCall(String sql) {
    if (DANGEROUS_FUNCTION_CALL_PATTERN.matcher(sql).find()) {
      return SqlSafetyConstants.REJECT_DANGEROUS_FUNCTION + "SLEEP/BENCHMARK/LOAD_FILE";
    }
    return null;
  }

  private String validateSelectTree(Select select) {
    if (select.getForClause() != null) {
      return SqlSafetyConstants.REJECT_FOR_UPDATE;
    }
    List<WithItem> withItems = select.getWithItemsList();
    if (withItems != null) {
      for (WithItem withItem : withItems) {
        if (withItem.getSelect() != null) {
          String cteReject = validateSelectTree(withItem.getSelect());
          if (cteReject != null) {
            return cteReject;
          }
        }
      }
    }
    String shapeReject = validateSelectShape(select);
    if (shapeReject != null) {
      return shapeReject;
    }
    try {
      TablesNamesFinder finder = new TablesNamesFinder();
      finder.getTables((Statement) select);
    } catch (Exception ex) {
      return SqlSafetyConstants.REJECT_PARSE_FAILED + ": " + ex.getMessage();
    }
    return null;
  }

  private String validateSelectShape(Select select) {
    if (select instanceof PlainSelect plainSelect) {
      return validatePlainSelect(plainSelect);
    }
    if (select instanceof SetOperationList setOperationList) {
      for (Select child : setOperationList.getSelects()) {
        String childReject = validateSelectTree(child);
        if (childReject != null) {
          return childReject;
        }
      }
      return null;
    }
    if (select instanceof ParenthesedSelect parenthesedSelect) {
      if (parenthesedSelect.getSelect() != null) {
        return validateSelectTree(parenthesedSelect.getSelect());
      }
      PlainSelect plainSelect = parenthesedSelect.getPlainSelect();
      if (plainSelect != null) {
        return validatePlainSelect(plainSelect);
      }
      return null;
    }
    return SqlSafetyConstants.REJECT_NOT_SELECT;
  }

  private String validatePlainSelect(PlainSelect plainSelect) {
    if (plainSelect.getIntoTables() != null && !plainSelect.getIntoTables().isEmpty()) {
      return SqlSafetyConstants.REJECT_INTO_OUTFILE;
    }
    if (plainSelect.getIntoTempTable() != null) {
      return SqlSafetyConstants.REJECT_INTO_OUTFILE;
    }
    DangerousFunctionVisitor visitor = new DangerousFunctionVisitor(this);
    if (plainSelect.getWhere() != null) {
      plainSelect.getWhere().accept(visitor);
    }
    if (plainSelect.getHaving() != null) {
      plainSelect.getHaving().accept(visitor);
    }
    if (plainSelect.getSelectItems() != null) {
      for (SelectItem<?> item : plainSelect.getSelectItems()) {
        if (item != null && item.getExpression() != null) {
          item.getExpression().accept(visitor);
        }
      }
    }
    return visitor.getRejectReason();
  }

  private static final class DangerousFunctionVisitor extends ExpressionVisitorAdapter {

    private final SqlSafetyChecker checker;

    private String rejectReason;

    private DangerousFunctionVisitor(SqlSafetyChecker checker) {
      this.checker = checker;
    }

    @Override
    public void visit(ParenthesedSelect parenthesedSelect) {
      String nestedReject = checker.validateSelectTree(parenthesedSelect);
      if (nestedReject != null) {
        rejectReason = nestedReject;
        return;
      }
      super.visit(parenthesedSelect);
    }

    @Override
    public void visit(EqualsTo equalsTo) {
      if (equalsTo.getLeftExpression() != null) {
        equalsTo.getLeftExpression().accept(this);
      }
      if (rejectReason == null && equalsTo.getRightExpression() != null) {
        equalsTo.getRightExpression().accept(this);
      }
    }

    @Override
    public void visit(Function function) {
      if (function.getName() == null) {
        return;
      }
      String upper = function.getName().toUpperCase(Locale.ROOT);
      if (SqlSafetyConstants.DANGEROUS_FUNCTION_NAMES.contains(upper)) {
        rejectReason = SqlSafetyConstants.REJECT_DANGEROUS_FUNCTION + function.getName();
      }
      super.visit(function);
    }

    String getRejectReason() {
      return rejectReason;
    }
  }
}
