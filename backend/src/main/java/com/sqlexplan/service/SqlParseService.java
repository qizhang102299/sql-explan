package com.sqlexplan.service;

import com.sqlexplan.constant.SqlParseConstants;
import com.sqlexplan.exception.BizException;
import com.sqlexplan.model.domain.AggregateFunctionItem;
import com.sqlexplan.model.domain.CteClause;
import com.sqlexplan.model.domain.JoinClause;
import com.sqlexplan.model.domain.SelectColumnItem;
import com.sqlexplan.model.domain.SqlQueryBody;
import com.sqlexplan.model.domain.SqlStructure;
import com.sqlexplan.model.domain.SubqueryClause;
import com.sqlexplan.model.domain.TableReference;
import com.sqlexplan.model.domain.WherePredicate;
import com.sqlexplan.model.enums.JoinTypeEnum;
import com.sqlexplan.model.enums.SqlSetOperationEnum;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.Between;
import net.sf.jsqlparser.expression.operators.relational.ComparisonOperator;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.LikeExpression;
import net.sf.jsqlparser.expression.operators.relational.ExistsExpression;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.GroupByElement;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.Offset;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SetOperation;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.WithItem;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 基于 JSqlParser 的 SQL 语义结构解析服务。
 */
@Service
public class SqlParseService {

  /**
   * 解析单条 SELECT SQL，输出结构化语义信息。
   *
   * @param sql SQL 文本
   * @return 语义结构
   */
  public SqlStructure parse(String sql) {
    if (!StringUtils.hasText(sql)) {
      throw new BizException("SQL 不能为空");
    }
    Statements statements;
    try {
      statements = CCJSqlParserUtil.parseStatements(sql.trim());
    } catch (JSQLParserException ex) {
      throw new BizException(SqlParseConstants.REJECT_PARSE_FAILED + ": " + ex.getMessage());
    }
    if (statements.getStatements().size() != 1) {
      throw new BizException("仅支持解析单条 SQL 语句");
    }
    Statement statement = statements.getStatements().get(0);
    if (!(statement instanceof Select select)) {
      throw new BizException("仅支持 SELECT 语句");
    }
    SqlStructure structure = buildFromSelect(select);
    structure.setAllTables(collectAllTables(structure));
    return structure;
  }

  /**
   * 从语义结构中提取去重后的物理表名列表。
   *
   * @param structure 语义结构
   * @return 表名列表
   */
  public List<String> extractPhysicalTableNames(SqlStructure structure) {
    List<String> names = new ArrayList<>();
    if (structure == null || structure.getAllTables() == null) {
      return names;
    }
    for (TableReference ref : structure.getAllTables()) {
      if (ref.getTableName() != null && !names.contains(ref.getTableName())) {
        names.add(ref.getTableName());
      }
    }
    return names;
  }

  private SqlStructure buildFromSelect(Select select) {
    SqlStructure structure = new SqlStructure();
    List<WithItem> withItems = select.getWithItemsList();
    if (withItems != null && !withItems.isEmpty()) {
      for (WithItem withItem : withItems) {
        CteClause cte = new CteClause();
        if (withItem.getAlias() != null) {
          cte.setName(withItem.getAlias().getName());
        }
        if (withItem.getSelect() != null) {
          cte.setBody(buildFromSelect(withItem.getSelect()));
        }
        structure.getCtes().add(cte);
      }
    }
    fillSelectShape(select, structure);
    return structure;
  }

  private void fillSelectShape(Select select, SqlStructure structure) {
    if (select instanceof SetOperationList setOperationList) {
      structure.setSetOperation(mapSetOperation(setOperationList.getOperations()));
      for (Select child : setOperationList.getSelects()) {
        if (child instanceof ParenthesedSelect parenthesedSelect && parenthesedSelect.getSelect() != null) {
          structure.getUnionBranches().add(buildFromSelect(parenthesedSelect.getSelect()));
        } else {
          SqlStructure branch = new SqlStructure();
          fillSelectShape(child, branch);
          branch.setAllTables(collectAllTables(branch));
          structure.getUnionBranches().add(branch);
        }
      }
      return;
    }
    PlainSelect plainSelect = resolvePlainSelect(select);
    if (plainSelect == null) {
      throw new BizException(SqlParseConstants.REJECT_PARSE_FAILED + ": 无法识别的 SELECT 形态");
    }
    structure.setBody(parsePlainSelect(plainSelect, select));
  }

  private PlainSelect resolvePlainSelect(Select select) {
    if (select instanceof PlainSelect plainSelect) {
      return plainSelect;
    }
    if (select instanceof ParenthesedSelect parenthesedSelect) {
      if (parenthesedSelect.getPlainSelect() != null) {
        return parenthesedSelect.getPlainSelect();
      }
      if (parenthesedSelect.getSelect() != null) {
        return resolvePlainSelect(parenthesedSelect.getSelect());
      }
    }
    return null;
  }

  private SqlQueryBody parsePlainSelect(PlainSelect plainSelect, Select selectWithLimit) {
    SqlQueryBody body = new SqlQueryBody();
    if (plainSelect.getFromItem() != null) {
      collectFromItem(plainSelect.getFromItem(), body, SqlParseConstants.SUBQUERY_LOCATION_FROM);
    }
    if (plainSelect.getJoins() != null) {
      for (Join join : plainSelect.getJoins()) {
        body.getJoins().add(toJoinClause(join));
        if (join.getRightItem() instanceof ParenthesedSelect parenthesedSelect) {
          body.getSubqueries().add(toSubquery(
              SqlParseConstants.SUBQUERY_LOCATION_FROM,
              join.getRightItem().getAlias(),
              parenthesedSelect));
        }
      }
    }
    if (plainSelect.getSelectItems() != null) {
      for (SelectItem<?> item : plainSelect.getSelectItems()) {
        if (item == null) {
          continue;
        }
        SelectColumnItem columnItem = toSelectColumn(item);
        body.getSelectColumns().add(columnItem);
        if (item.getExpression() instanceof Function function
            && SqlParseConstants.isAggregateFunction(function.getName())) {
          body.getAggregateFunctions().add(toAggregateFunction(function, item.getAlias()));
        }
        if (item.getExpression() instanceof ParenthesedSelect parenthesedSelect) {
          body.getSubqueries().add(toSubquery(
              SqlParseConstants.SUBQUERY_LOCATION_SELECT,
              item.getAlias(),
              parenthesedSelect));
        }
      }
    }
    if (plainSelect.getWhere() != null) {
      collectWherePredicates(plainSelect.getWhere(), body);
      collectWhereSubqueries(plainSelect.getWhere(), body);
    }
    GroupByElement groupBy = plainSelect.getGroupBy();
    if (groupBy != null && groupBy.getGroupByExpressionList() != null) {
      groupBy.getGroupByExpressionList().forEach(expr -> body.getGroupByColumns().add(expr.toString()));
    }
    if (plainSelect.getOrderByElements() != null) {
      for (OrderByElement element : plainSelect.getOrderByElements()) {
        body.getOrderByColumns().add(element.getExpression().toString());
        body.getOrderByAscFlags().add(element.isAsc());
      }
    }
    applyLimitAndOffset(plainSelect, selectWithLimit, body);
    return body;
  }

  private void applyLimitAndOffset(PlainSelect plainSelect, Select selectWithLimit, SqlQueryBody body) {
    Limit limit = null;
    if (selectWithLimit != null) {
      limit = selectWithLimit.getLimit();
    }
    if (limit == null && plainSelect != null) {
      limit = plainSelect.getLimit();
    }
    if (limit == null) {
      return;
    }
    body.setLimit(parseLimitExpression(limit.getRowCount()));
    Expression offsetExpr = limit.getOffset();
    if (offsetExpr == null) {
      offsetExpr = resolveStandaloneOffsetExpression(selectWithLimit, plainSelect);
    }
    body.setOffset(parseLimitExpression(offsetExpr));
  }

  private Expression resolveStandaloneOffsetExpression(Select selectWithLimit, PlainSelect plainSelect) {
    Offset offset = null;
    if (selectWithLimit != null) {
      offset = selectWithLimit.getOffset();
    }
    if (offset == null && plainSelect != null) {
      offset = plainSelect.getOffset();
    }
    if (offset == null) {
      return null;
    }
    return offset.getOffset();
  }

  private void collectFromItem(FromItem fromItem, SqlQueryBody body, String location) {
    if (fromItem instanceof Table table) {
      body.getFromTables().add(toTableReference(table));
      return;
    }
    if (fromItem instanceof ParenthesedSelect parenthesedSelect) {
      body.getSubqueries().add(toSubquery(location, fromItem.getAlias(), parenthesedSelect));
    }
  }

  private SubqueryClause toSubquery(String location, Alias alias, ParenthesedSelect parenthesedSelect) {
    SubqueryClause clause = new SubqueryClause();
    clause.setLocation(location);
    if (alias != null) {
      clause.setAlias(alias.getName());
    }
    if (parenthesedSelect.getSelect() != null) {
      clause.setInnerStructure(buildFromSelect(parenthesedSelect.getSelect()));
    }
    return clause;
  }

  private TableReference toTableReference(Table table) {
    TableReference ref = new TableReference();
    ref.setTableName(table.getName());
    ref.setSchemaName(table.getSchemaName());
    if (table.getAlias() != null) {
      ref.setAlias(table.getAlias().getName());
    }
    if (table.getSchemaName() != null && table.getName() != null) {
      ref.setTableName(table.getSchemaName() + "." + table.getName());
    }
    return ref;
  }

  private JoinClause toJoinClause(Join join) {
    JoinClause clause = new JoinClause();
    clause.setJoinType(resolveJoinType(join));
    if (join.getRightItem() instanceof Table table) {
      clause.setRightTableName(table.getName());
      if (table.getSchemaName() != null) {
        clause.setRightTableName(table.getSchemaName() + "." + table.getName());
      }
      if (table.getAlias() != null) {
        clause.setRightTableAlias(table.getAlias().getName());
      }
    }
    if (join.getOnExpressions() != null && !join.getOnExpressions().isEmpty()) {
      clause.setOnCondition(join.getOnExpressions().iterator().next().toString());
    } else if (join.getOnExpression() != null) {
      clause.setOnCondition(join.getOnExpression().toString());
    }
    return clause;
  }

  private JoinTypeEnum resolveJoinType(Join join) {
    if (join.isCross()) {
      return JoinTypeEnum.CROSS;
    }
    if (join.isNatural()) {
      return JoinTypeEnum.NATURAL;
    }
    if (join.isFull()) {
      return JoinTypeEnum.FULL;
    }
    if (join.isLeft()) {
      return JoinTypeEnum.LEFT;
    }
    if (join.isRight()) {
      return JoinTypeEnum.RIGHT;
    }
    if (join.isSimple()) {
      return JoinTypeEnum.SIMPLE;
    }
    return JoinTypeEnum.INNER;
  }

  private SelectColumnItem toSelectColumn(SelectItem<?> item) {
    SelectColumnItem columnItem = new SelectColumnItem();
    Expression expression = item.getExpression();
    if (expression instanceof AllColumns) {
      columnItem.setExpression("*");
    } else if (expression instanceof AllTableColumns allTableColumns) {
      columnItem.setExpression(allTableColumns.toString());
      columnItem.setTableQualifier(allTableColumns.getTable().getName());
    } else if (expression != null) {
      columnItem.setExpression(expression.toString());
      if (expression instanceof Column column) {
        if (column.getTable() != null) {
          columnItem.setTableQualifier(column.getTable().getName());
        }
      }
    }
    if (item.getAlias() != null) {
      columnItem.setAlias(item.getAlias().getName());
    }
    return columnItem;
  }

  private AggregateFunctionItem toAggregateFunction(Function function, Alias alias) {
    AggregateFunctionItem item = new AggregateFunctionItem();
    item.setFunctionName(function.getName().toUpperCase(Locale.ROOT));
    if (function.getParameters() != null) {
      item.setArgumentsText(function.getParameters().toString());
    }
    if (alias != null) {
      item.setAlias(alias.getName());
    }
    return item;
  }

  private void collectWherePredicates(Expression expression, SqlQueryBody body) {
    if (expression instanceof AndExpression andExpression) {
      collectWherePredicates(andExpression.getLeftExpression(), body);
      collectWherePredicates(andExpression.getRightExpression(), body);
      return;
    }
    if (expression instanceof OrExpression orExpression) {
      WherePredicate predicate = new WherePredicate();
      predicate.setOperator("OR");
      predicate.setPredicateText(expression.toString());
      body.getWherePredicates().add(predicate);
      return;
    }
    WherePredicate predicate = new WherePredicate();
    predicate.setPredicateText(expression.toString());
    if (expression instanceof Between between) {
      predicate.setOperator("BETWEEN");
      if (between.getLeftExpression() != null) {
        predicate.setLeftExpression(between.getLeftExpression().toString());
      }
      predicate.setColumnWrappedByFunction(isColumnWrappedByFunction(between.getLeftExpression()));
    } else if (expression instanceof LikeExpression likeExpression) {
      predicate.setOperator("LIKE");
      if (likeExpression.getLeftExpression() != null) {
        predicate.setLeftExpression(likeExpression.getLeftExpression().toString());
      }
      if (likeExpression.getRightExpression() != null) {
        predicate.setRightExpression(likeExpression.getRightExpression().toString());
      }
      predicate.setColumnWrappedByFunction(isColumnWrappedByFunction(likeExpression.getLeftExpression()));
    } else if (expression instanceof ComparisonOperator comparisonOperator) {
      predicate.setOperator(comparisonOperator.getStringExpression());
      if (comparisonOperator.getLeftExpression() != null) {
        predicate.setLeftExpression(comparisonOperator.getLeftExpression().toString());
      }
      if (comparisonOperator.getRightExpression() != null) {
        predicate.setRightExpression(comparisonOperator.getRightExpression().toString());
      }
      predicate.setColumnWrappedByFunction(isColumnWrappedByFunction(comparisonOperator.getLeftExpression()));
    } else if (expression instanceof InExpression inExpression) {
      predicate.setOperator(inExpression.isNot() ? "NOT IN" : "IN");
      if (inExpression.getLeftExpression() != null) {
        predicate.setLeftExpression(inExpression.getLeftExpression().toString());
      }
      predicate.setColumnWrappedByFunction(isColumnWrappedByFunction(inExpression.getLeftExpression()));
    } else if (expression instanceof ExistsExpression existsExpression) {
      predicate.setOperator(existsExpression.isNot() ? "NOT EXISTS" : "EXISTS");
    } else if (expression instanceof EqualsTo equalsTo) {
      predicate.setOperator("=");
      if (equalsTo.getLeftExpression() != null) {
        predicate.setLeftExpression(equalsTo.getLeftExpression().toString());
      }
      if (equalsTo.getRightExpression() != null) {
        predicate.setRightExpression(equalsTo.getRightExpression().toString());
      }
      predicate.setColumnWrappedByFunction(isColumnWrappedByFunction(equalsTo.getLeftExpression()));
    }
    body.getWherePredicates().add(predicate);
  }

  private void collectWhereSubqueries(Expression expression, SqlQueryBody body) {
    if (expression instanceof ParenthesedSelect parenthesedSelect) {
      body.getSubqueries().add(toSubquery(SqlParseConstants.SUBQUERY_LOCATION_WHERE, null, parenthesedSelect));
      return;
    }
    if (expression instanceof AndExpression andExpression) {
      collectWhereSubqueries(andExpression.getLeftExpression(), body);
      collectWhereSubqueries(andExpression.getRightExpression(), body);
      return;
    }
    if (expression instanceof OrExpression orExpression) {
      collectWhereSubqueries(orExpression.getLeftExpression(), body);
      collectWhereSubqueries(orExpression.getRightExpression(), body);
      return;
    }
    if (expression instanceof InExpression inExpression
        && inExpression.getRightExpression() instanceof ParenthesedSelect parenthesedSelect) {
      body.getSubqueries().add(toSubquery(SqlParseConstants.SUBQUERY_LOCATION_WHERE, null, parenthesedSelect));
      return;
    }
    if (expression instanceof ExistsExpression existsExpression
        && existsExpression.getRightExpression() instanceof ParenthesedSelect existsSelect) {
      body.getSubqueries().add(toSubquery(SqlParseConstants.SUBQUERY_LOCATION_WHERE, null, existsSelect));
    }
  }

  private boolean isColumnWrappedByFunction(Expression expression) {
    if (expression == null || expression instanceof Column) {
      return false;
    }
    if (expression instanceof Function function && function.getParameters() != null) {
      for (Object param : function.getParameters().getExpressions()) {
        if (param instanceof Column) {
          return true;
        }
        if (param instanceof Expression inner && expressionReferencesColumn(inner)) {
          return true;
        }
      }
      return false;
    }
    if (expression instanceof BinaryExpression) {
      return expressionReferencesColumn(expression);
    }
    return expressionReferencesColumn(expression);
  }

  private boolean expressionReferencesColumn(Expression expression) {
    if (expression instanceof Column) {
      return true;
    }
    if (expression instanceof BinaryExpression binary) {
      return expressionReferencesColumn(binary.getLeftExpression())
          || expressionReferencesColumn(binary.getRightExpression());
    }
    if (expression instanceof Function function && function.getParameters() != null) {
      for (Object param : function.getParameters().getExpressions()) {
        if (param instanceof Expression inner && expressionReferencesColumn(inner)) {
          return true;
        }
      }
    }
    return false;
  }

  private SqlSetOperationEnum mapSetOperation(List<SetOperation> operations) {
    if (operations == null || operations.isEmpty()) {
      return SqlSetOperationEnum.UNION;
    }
    SetOperation op = operations.get(0);
    String name = op.toString().toUpperCase(Locale.ROOT);
    if (name.contains("ALL")) {
      if (name.contains("UNION")) {
        return SqlSetOperationEnum.UNION_ALL;
      }
    }
    return switch (name) {
      case "UNION" -> SqlSetOperationEnum.UNION;
      case "INTERSECT" -> SqlSetOperationEnum.INTERSECT;
      case "EXCEPT" -> SqlSetOperationEnum.EXCEPT;
      default -> SqlSetOperationEnum.UNION_ALL;
    };
  }

  private Long parseLimitExpression(Expression expression) {
    if (expression instanceof LongValue longValue) {
      return longValue.getValue();
    }
    if (expression == null) {
      return null;
    }
    try {
      return Long.parseLong(expression.toString());
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private List<TableReference> collectAllTables(SqlStructure structure) {
    Map<String, TableReference> map = new LinkedHashMap<>();
    mergeTables(structure, map);
    return new ArrayList<>(map.values());
  }

  private void mergeTables(SqlStructure structure, Map<String, TableReference> map) {
    if (structure == null) {
      return;
    }
    if (structure.getCtes() != null) {
      for (CteClause cte : structure.getCtes()) {
        if (cte.getBody() != null) {
          mergeTables(cte.getBody(), map);
        }
      }
    }
    if (structure.getBody() != null) {
      mergeBodyTables(structure.getBody(), map);
    }
    if (structure.getUnionBranches() != null) {
      for (SqlStructure branch : structure.getUnionBranches()) {
        mergeTables(branch, map);
      }
    }
  }

  private void mergeBodyTables(SqlQueryBody body, Map<String, TableReference> map) {
    if (body.getFromTables() != null) {
      for (TableReference ref : body.getFromTables()) {
        putTableRef(map, ref);
      }
    }
    if (body.getJoins() != null) {
      for (JoinClause join : body.getJoins()) {
        if (join.getRightTableName() != null) {
          TableReference ref = new TableReference();
          ref.setTableName(join.getRightTableName());
          ref.setAlias(join.getRightTableAlias());
          putTableRef(map, ref);
        }
      }
    }
    if (body.getSubqueries() != null) {
      for (SubqueryClause subquery : body.getSubqueries()) {
        if (subquery.getInnerStructure() != null) {
          mergeTables(subquery.getInnerStructure(), map);
        }
      }
    }
  }

  private void putTableRef(Map<String, TableReference> map, TableReference ref) {
    if (ref == null || !StringUtils.hasText(ref.getTableName())) {
      return;
    }
    String key = ref.getTableName() + "|" + Objects.toString(ref.getAlias(), "");
    map.putIfAbsent(key, ref);
  }
}
