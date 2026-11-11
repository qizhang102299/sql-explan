package com.sqlexplan.service;

import com.sqlexplan.config.SqlexplanProperties;
import com.sqlexplan.constant.ExplainConstants;
import com.sqlexplan.model.domain.ColumnMeta;
import com.sqlexplan.model.domain.ExplainPlan;
import com.sqlexplan.model.domain.JoinClause;
import com.sqlexplan.model.domain.PlanInterpretation;
import com.sqlexplan.model.domain.PlanIssue;
import com.sqlexplan.model.domain.PlanNode;
import com.sqlexplan.model.domain.SqlQueryBody;
import com.sqlexplan.model.domain.SqlStructure;
import com.sqlexplan.model.domain.SubqueryClause;
import com.sqlexplan.model.domain.TableReference;
import com.sqlexplan.model.domain.TableSchemaMeta;
import com.sqlexplan.model.domain.WherePredicate;
import com.sqlexplan.util.SqlNameParseUtil;
import com.sqlexplan.model.enums.PlanIssueSeverityEnum;
import com.sqlexplan.model.enums.PlanIssueTypeEnum;
import com.sqlexplan.model.enums.PlanNodeKindEnum;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 基于执行计划与 SQL 结构的规则解读。
 */
@Service
public class PlanInterpreter {

  private static final Pattern SIMPLE_IDENTIFIER = Pattern.compile("^[\\w.`]+$");

  private static final Pattern QUOTED_NUMERIC_LITERAL = Pattern.compile("^'\\d+(\\.\\d+)?'$");

  private static final Pattern UNQUOTED_NUMERIC_LITERAL = Pattern.compile("^\\d+(\\.\\d+)?$");

  private static final Pattern FUNCTION_CALL_ON_COLUMN =
      Pattern.compile("^[\\w.`$]+\\s*\\(", Pattern.CASE_INSENSITIVE);

  private static final long COUNT_STAR_LARGE_TABLE_ROWS = 100_000L;

  private final SqlexplanProperties sqlexplanProperties;

  private final ExplainService explainService;

  public PlanInterpreter(SqlexplanProperties sqlexplanProperties, ExplainService explainService) {
    this.sqlexplanProperties = sqlexplanProperties;
    this.explainService = explainService;
  }

  /**
   * 对执行计划与可选的 SQL 语义结构做规则解读。
   *
   * @param explainPlan 结构化执行计划
   * @param sqlStructure SQL 语义结构（可为 null，仅解读计划侧规则）
   * @return 问题列表与摘要
   */
  public PlanInterpretation interpret(ExplainPlan explainPlan, SqlStructure sqlStructure) {
    return interpret(explainPlan, sqlStructure, null);
  }

  /**
   * 对执行计划、SQL 结构与可选表元数据做规则解读。
   *
   * @param explainPlan 结构化执行计划
   * @param sqlStructure SQL 语义结构（可为 null）
   * @param tableMetadata 涉及表元数据（可为 null，用于大表 COUNT、JOIN 类型比对等）
   * @return 问题列表与摘要
   */
  public PlanInterpretation interpret(
      ExplainPlan explainPlan, SqlStructure sqlStructure, List<TableSchemaMeta> tableMetadata) {
    PlanInterpretation result = new PlanInterpretation();
    Set<String> dedupe = new HashSet<>();
    Map<String, TableSchemaMeta> metaByTable = buildMetaMap(tableMetadata);
    if (explainPlan != null && explainPlan.getRoot() != null) {
      interpretPlanTree(explainPlan.getRoot(), result.getIssues(), dedupe);
      List<PlanNode> tableNodes = explainService.collectTableNodes(explainPlan.getRoot());
      for (PlanNode table : tableNodes) {
        interpretTableAccess(table, result.getIssues(), dedupe);
      }
    }
    if (sqlStructure != null && sqlStructure.getBody() != null) {
      interpretSqlBody(sqlStructure.getBody(), result.getIssues(), dedupe, metaByTable, buildAliasMap(sqlStructure.getBody()));
    }
    if (result.getIssues().isEmpty() && (explainPlan == null || explainPlan.getRoot() == null)) {
      result.setSummary("无有效执行计划");
    } else {
      result.setSummary(buildSummary(result.getIssues()));
    }
    return result;
  }

  private void interpretPlanTree(PlanNode node, List<PlanIssue> issues, Set<String> dedupe) {
    if (node == null) {
      return;
    }
    if (node.getNodeKind() == PlanNodeKindEnum.ORDERING && hasExtraKeyword(node, "filesort")) {
      addIssue(
          issues,
          dedupe,
          PlanIssueTypeEnum.FILESORT,
          PlanIssueSeverityEnum.WARNING,
          "存在文件排序（filesort）",
          "排序无法利用索引顺序完成，MySQL 可能在内存或磁盘中对结果集做额外排序，数据量大时易成为瓶颈。",
          null,
          node.getOperationLabel(),
          null);
    }
    if ((node.getNodeKind() == PlanNodeKindEnum.GROUPING || node.getNodeKind() == PlanNodeKindEnum.MATERIALIZED)
        && hasExtraKeyword(node, "temporary")) {
      addIssue(
          issues,
          dedupe,
          PlanIssueTypeEnum.TEMPORARY_TABLE,
          PlanIssueSeverityEnum.WARNING,
          "使用临时表",
          "分组、去重或子查询物化可能创建临时表，占用内存或磁盘并增加额外 I/O。",
          null,
          node.getOperationLabel(),
          null);
    }
    for (PlanNode child : node.getChildren()) {
      interpretPlanTree(child, issues, dedupe);
    }
  }

  private void interpretTableAccess(PlanNode table, List<PlanIssue> issues, Set<String> dedupe) {
    String tableName = table.getTableName();
    String accessType = table.getAccessType();
    if (ExplainConstants.JSON_ACCESS_TYPE_ALL.equalsIgnoreCase(accessType)) {
      addIssue(
          issues,
          dedupe,
          PlanIssueTypeEnum.FULL_TABLE_SCAN,
          PlanIssueSeverityEnum.CRITICAL,
          "全表扫描",
          "访问类型为 ALL，将逐行扫描整张表，数据量增大时代价显著上升。",
          tableName,
          "access_type=ALL",
          null);
    }
    if (!table.getPossibleKeys().isEmpty() && !StringUtils.hasText(table.getIndexKey())) {
      addIssue(
          issues,
          dedupe,
          PlanIssueTypeEnum.INDEX_NOT_USED,
          PlanIssueSeverityEnum.WARNING,
          "存在候选索引但未选用",
          "优化器列出了可用索引（possible_keys），但实际未使用任何索引（key 为空），请检查条件是否能命中最左前缀。",
          tableName,
          "possible_keys=" + String.join(",", table.getPossibleKeys()),
          null);
    }
    if (hasExtraKeyword(table, "filesort")) {
      addIssue(
          issues,
          dedupe,
          PlanIssueTypeEnum.FILESORT,
          PlanIssueSeverityEnum.WARNING,
          "存在文件排序（filesort）",
          "该步骤需要额外排序，建议让 ORDER BY 列与索引顺序一致或缩小扫描行数。",
          tableName,
          ExplainConstants.EXTRA_USING_FILESORT,
          null);
    }
    if (hasExtraKeyword(table, "temporary")) {
      addIssue(
          issues,
          dedupe,
          PlanIssueTypeEnum.TEMPORARY_TABLE,
          PlanIssueSeverityEnum.WARNING,
          "使用临时表",
          "执行过程中使用临时表，可能由 GROUP BY、DISTINCT 或排序引起。",
          tableName,
          ExplainConstants.EXTRA_USING_TEMPORARY,
          null);
    }
    long threshold = sqlexplanProperties.getExplain().getLargeScanRowsThreshold();
    Long rows = table.getRowsExaminedPerScan();
    if (rows != null && rows >= threshold) {
      addIssue(
          issues,
          dedupe,
          PlanIssueTypeEnum.LARGE_ROWS_EXAMINED,
          PlanIssueSeverityEnum.WARNING,
          "单次扫描行数过大",
          "预估单次扫描约 " + rows + " 行，超过阈值 " + threshold + "，可能成为慢查询主因。",
          tableName,
          "rows_examined_per_scan=" + rows,
          null);
    }
  }

  private void interpretSqlBody(
      SqlQueryBody body,
      List<PlanIssue> issues,
      Set<String> dedupe,
      Map<String, TableSchemaMeta> metaByTable,
      Map<String, String> aliasToTable) {
    long deepOffsetThreshold = sqlexplanProperties.getExplain().getDeepPaginationOffsetThreshold();
    Long offset = body.getOffset();
    if (offset != null && offset >= deepOffsetThreshold) {
      String tableName = body.getFromTables().isEmpty() ? null : body.getFromTables().get(0).getTableName();
      String pk = resolvePrimaryKey(metaByTable.get(tableKey(tableName)), "id");
      String remediation =
          "-- 延迟关联示例\n"
              + "SELECT t.* FROM "
              + SqlNameParseUtil.quoteTableName(tableName)
              + " t INNER JOIN (SELECT "
              + SqlNameParseUtil.quoteColumnIfNeeded(pk)
              + " FROM "
              + SqlNameParseUtil.quoteTableName(tableName)
              + " ORDER BY "
              + SqlNameParseUtil.quoteColumnIfNeeded(pk)
              + " LIMIT "
              + (body.getLimit() == null ? "20" : body.getLimit())
              + " OFFSET "
              + offset
              + ") page ON t."
              + SqlNameParseUtil.quoteColumnIfNeeded(pk)
              + " = page."
              + SqlNameParseUtil.quoteColumnIfNeeded(pk);
      addIssue(
          issues,
          dedupe,
          PlanIssueTypeEnum.DEEP_PAGINATION,
          PlanIssueSeverityEnum.WARNING,
          "深分页",
          "OFFSET 达到 " + offset + "，MySQL 通常仍需扫描并丢弃前序大量行，建议改用延迟关联或基于游标/上次主键翻页。",
          tableName,
          "offset=" + offset,
          remediation);
    }
    interpretLimitWithoutOrderBy(body, issues, dedupe);
    interpretCountStarLargeTable(body, issues, dedupe, metaByTable);
    interpretJoinTypeMismatch(body, issues, dedupe, metaByTable, aliasToTable);
    interpretCorrelatedSubqueries(body, issues, dedupe);
    if (body.getWherePredicates() == null) {
      return;
    }
    for (WherePredicate predicate : body.getWherePredicates()) {
      if (Boolean.TRUE.equals(predicate.getColumnWrappedByFunction())) {
        PlanIssueTypeEnum issueType =
            isFunctionCallOnColumn(predicate.getLeftExpression())
                ? PlanIssueTypeEnum.INDEX_COLUMN_FUNCTION
                : PlanIssueTypeEnum.INDEX_COLUMN_EXPRESSION;
        String remediation = buildFunctionOrExpressionRemediation(predicate);
        addIssue(
            issues,
            dedupe,
            issueType,
            PlanIssueSeverityEnum.WARNING,
            issueType == PlanIssueTypeEnum.INDEX_COLUMN_FUNCTION
                ? "WHERE 中索引列被函数包裹"
                : "WHERE 中索引列参与表达式",
            issueType == PlanIssueTypeEnum.INDEX_COLUMN_FUNCTION
                ? "对列施加函数后通常无法利用该列上的 B-Tree 索引，请改写为等价且可走索引的条件。"
                : "列参与算术表达式后无法直接利用 B-Tree 索引，建议改写为列单独参与比较的范围条件。",
            null,
            predicate.getPredicateText(),
            remediation);
      }
      if ("LIKE".equalsIgnoreCase(predicate.getOperator())
          && SqlNameParseUtil.isLeadingWildcardLike(predicate.getRightExpression())) {
        String[] parts = SqlNameParseUtil.parseQualifiedColumn(predicate.getLeftExpression());
        String col = parts[1] == null ? "col" : parts[1];
        String remediation =
            "-- B-Tree 无法支撑前导通配，可改为后缀匹配或全文检索\n"
                + "-- 原: "
                + predicate.getPredicateText()
                + "\n"
                + "CREATE FULLTEXT INDEX idx_"
                + col
                + "_ft ON <table> ("
                + SqlNameParseUtil.quoteColumnIfNeeded(col)
                + ");\n"
                + "-- MATCH("
                + SqlNameParseUtil.quoteColumnIfNeeded(col)
                + ") AGAINST('keyword' IN BOOLEAN MODE)";
        addIssue(
            issues,
            dedupe,
            PlanIssueTypeEnum.LIKE_LEADING_WILDCARD,
            PlanIssueSeverityEnum.WARNING,
            "LIKE 含前导通配符",
            "LIKE '%...' 无法使用普通 B-Tree 索引，易导致全表扫描。",
            null,
            predicate.getPredicateText(),
            remediation);
      }
      if (isNonSargableOperator(predicate.getOperator())) {
        addIssue(
            issues,
            dedupe,
            PlanIssueTypeEnum.NON_SARGABLE_OPERATOR,
            PlanIssueSeverityEnum.INFO,
            "不利于索引扫描的运算符",
            "NOT IN、!=、<> 往往只能区间扫描或全表扫描；NOT IN 遇到 NULL 语义也更复杂。",
            null,
            predicate.getPredicateText(),
            buildNonSargableRemediation(predicate));
      }
      if (isLikelyImplicitConversion(predicate, body, metaByTable, aliasToTable)) {
        String normalized = buildImplicitCastRemediation(predicate);
        addIssue(
            issues,
            dedupe,
            PlanIssueTypeEnum.IMPLICIT_TYPE_CONVERSION,
            PlanIssueSeverityEnum.INFO,
            "疑似隐式类型转换",
            "字符串列与未引号数字比较，或数字列与引号数字字面量比较时，可能触发转换并无法使用索引。",
            null,
            predicate.getPredicateText(),
            normalized);
      }
    }
  }

  private void interpretLimitWithoutOrderBy(SqlQueryBody body, List<PlanIssue> issues, Set<String> dedupe) {
    if (body.getLimit() == null || body.getLimit() <= 0) {
      return;
    }
    if (!body.getOrderByColumns().isEmpty()) {
      return;
    }
    String tableName = body.getFromTables().isEmpty() ? null : body.getFromTables().get(0).getTableName();
    addIssue(
        issues,
        dedupe,
        PlanIssueTypeEnum.LIMIT_WITHOUT_ORDER_BY,
        PlanIssueSeverityEnum.INFO,
        "LIMIT 未配合 ORDER BY",
        "无 ORDER BY 时 LIMIT 返回的行顺序不确定，翻页或对比基准时结果可能抖动。",
        tableName,
        "limit=" + body.getLimit(),
        "-- 建议增加稳定排序键\nALTER: ... ORDER BY id LIMIT " + body.getLimit());
  }

  private void interpretCountStarLargeTable(
      SqlQueryBody body,
      List<PlanIssue> issues,
      Set<String> dedupe,
      Map<String, TableSchemaMeta> metaByTable) {
  boolean hasCountStar =
        body.getAggregateFunctions().stream()
            .anyMatch(
                a ->
                    a != null
                        && "COUNT".equalsIgnoreCase(a.getFunctionName())
                        && (a.getArgumentsText() == null
                            || a.getArgumentsText().isBlank()
                            || "*".equals(a.getArgumentsText().trim())));
    if (!hasCountStar) {
      return;
    }
    TableSchemaMeta largest = findLargestFromTable(body, metaByTable);
    if (largest == null
        || largest.getEstimatedRows() == null
        || largest.getEstimatedRows() < COUNT_STAR_LARGE_TABLE_ROWS) {
      return;
    }
    addIssue(
        issues,
        dedupe,
        PlanIssueTypeEnum.COUNT_STAR_LARGE_TABLE,
        PlanIssueSeverityEnum.WARNING,
        "大表 COUNT(*)",
        "表 "
            + largest.getTableName()
            + " 预估约 "
            + largest.getEstimatedRows()
            + " 行，COUNT(*) 通常需全索引或全表扫描。",
        largest.getTableName(),
        "COUNT(*)",
        "-- 近似计数或维护计数表\n"
            + "SELECT table_rows FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = '"
            + largest.getTableName()
            + "';\n"
            + "-- 或 SELECT cnt FROM table_row_stats WHERE table_name = '"
            + largest.getTableName()
            + "'");
  }

  private void interpretJoinTypeMismatch(
      SqlQueryBody body,
      List<PlanIssue> issues,
      Set<String> dedupe,
      Map<String, TableSchemaMeta> metaByTable,
      Map<String, String> aliasToTable) {
    for (JoinClause join : body.getJoins()) {
      for (String[] eq : SqlNameParseUtil.parseJoinEqualities(join.getOnCondition())) {
        ColumnMeta leftCol = resolveColumnMeta(metaByTable, aliasToTable, body, eq[0], eq[1]);
        ColumnMeta rightCol = resolveColumnMeta(metaByTable, aliasToTable, body, eq[2], eq[3]);
        if (leftCol == null || rightCol == null) {
          continue;
        }
        if (!columnTypesCompatible(leftCol, rightCol)) {
          addIssue(
              issues,
              dedupe,
              PlanIssueTypeEnum.JOIN_COLLATION_MISMATCH,
              PlanIssueSeverityEnum.WARNING,
              "JOIN 列类型不一致",
              "关联列 "
                  + eq[1]
                  + "（"
                  + leftCol.getDataType()
                  + "）与 "
                  + eq[3]
                  + "（"
                  + rightCol.getDataType()
                  + "）类型不一致，可能触发隐式转换。",
              join.getRightTableName(),
              join.getOnCondition(),
              "ON CAST("
                  + SqlNameParseUtil.quoteColumnIfNeeded(eq[0] + "." + eq[1])
                  + " AS "
                  + promotionType(leftCol, rightCol)
                  + ") = "
                  + SqlNameParseUtil.quoteColumnIfNeeded(eq[2] + "." + eq[3]));
        }
      }
    }
  }

  private void interpretCorrelatedSubqueries(SqlQueryBody body, List<PlanIssue> issues, Set<String> dedupe) {
    if (body.getSubqueries() == null) {
      return;
    }
    for (SubqueryClause sub : body.getSubqueries()) {
      if (sub.getInnerStructure() == null || sub.getInnerStructure().getBody() == null) {
        continue;
      }
      SqlQueryBody inner = sub.getInnerStructure().getBody();
      boolean correlated =
          inner.getWherePredicates().stream()
              .anyMatch(
                  p ->
                      p != null
                          && StringUtils.hasText(p.getPredicateText())
                          && p.getPredicateText().contains("."));
      if (!correlated) {
        continue;
      }
      addIssue(
          issues,
          dedupe,
          PlanIssueTypeEnum.CORRELATED_SUBQUERY,
          PlanIssueSeverityEnum.WARNING,
          "关联子查询",
          "WHERE 中的关联子查询可能对驱动表每一行执行一次，建议改写为 JOIN 或 EXISTS。",
          null,
          sub.getLocation(),
          "-- 建议改写为 INNER JOIN 或 EXISTS，参见 SqlRewriter 规则输出");
    }
  }

  private boolean isLikelyImplicitConversion(
      WherePredicate predicate,
      SqlQueryBody body,
      Map<String, TableSchemaMeta> metaByTable,
      Map<String, String> aliasToTable) {
    if (predicate == null || !"=".equals(predicate.getOperator())) {
      return false;
    }
    String left = predicate.getLeftExpression();
    String right = predicate.getRightExpression();
    if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
      return false;
    }
    String l = left.trim();
    String r = right.trim();
    boolean colLeft = SIMPLE_IDENTIFIER.matcher(l).matches();
    boolean colRight = SIMPLE_IDENTIFIER.matcher(r).matches();
    ColumnMeta columnMeta = null;
    boolean literalQuotedNumeric = false;
    boolean literalUnquotedNumeric = false;
    if (colLeft && QUOTED_NUMERIC_LITERAL.matcher(r).matches()) {
      String[] parts = SqlNameParseUtil.parseQualifiedColumn(l);
      columnMeta = resolveColumnMeta(metaByTable, aliasToTable, body, parts[0], parts[1]);
      literalQuotedNumeric = true;
    } else if (colLeft && UNQUOTED_NUMERIC_LITERAL.matcher(r).matches()) {
      String[] parts = SqlNameParseUtil.parseQualifiedColumn(l);
      columnMeta = resolveColumnMeta(metaByTable, aliasToTable, body, parts[0], parts[1]);
      literalUnquotedNumeric = true;
    } else if (colRight && QUOTED_NUMERIC_LITERAL.matcher(l).matches()) {
      String[] parts = SqlNameParseUtil.parseQualifiedColumn(r);
      columnMeta = resolveColumnMeta(metaByTable, aliasToTable, body, parts[0], parts[1]);
      literalQuotedNumeric = true;
    } else if (colRight && UNQUOTED_NUMERIC_LITERAL.matcher(l).matches()) {
      String[] parts = SqlNameParseUtil.parseQualifiedColumn(r);
      columnMeta = resolveColumnMeta(metaByTable, aliasToTable, body, parts[0], parts[1]);
      literalUnquotedNumeric = true;
    } else {
      return false;
    }
    if (columnMeta == null || !StringUtils.hasText(columnMeta.getDataType())) {
      return false;
    }
    String type = normalizeDataType(columnMeta.getDataType());
    if (type == null) {
      return false;
    }
    boolean numericCol = isNumericType(type);
    boolean stringCol = isStringType(type);
    if (numericCol && literalUnquotedNumeric) {
      return false;
    }
    if (stringCol && literalQuotedNumeric) {
      return false;
    }
    if (stringCol && literalUnquotedNumeric) {
      return true;
    }
    if (numericCol && literalQuotedNumeric) {
      return true;
    }
    return false;
  }

  private static boolean isNonSargableOperator(String operator) {
    if (operator == null) {
      return false;
    }
    String upper = operator.toUpperCase(Locale.ROOT);
    return "NOT IN".equals(upper) || "!=".equals(operator) || "<>".equals(operator);
  }

  private static boolean isFunctionCallOnColumn(String leftExpression) {
    return StringUtils.hasText(leftExpression)
        && FUNCTION_CALL_ON_COLUMN.matcher(leftExpression.trim()).find();
  }

  private static String buildFunctionOrExpressionRemediation(WherePredicate predicate) {
    if (predicate == null || !StringUtils.hasText(predicate.getPredicateText())) {
      return null;
    }
    String text = predicate.getPredicateText();
    Matcher yearMatcher =
        Pattern.compile("(?i)YEAR\\s*\\(\\s*([\\w.`$]+)\\s*\\)\\s*=\\s*(\\d{4})").matcher(text);
    if (yearMatcher.find()) {
      String col = yearMatcher.group(1);
      String year = yearMatcher.group(2);
      return col
          + " >= '"
          + year
          + "-01-01' AND "
          + col
          + " < '"
          + (Integer.parseInt(year) + 1)
          + "-01-01'";
    }
    Matcher exprMatcher =
        Pattern.compile("(?i)([`\\w$.]+)\\s*([+\\-])\\s*(\\d+)\\s*=\\s*(\\d+)").matcher(text);
    if (exprMatcher.find()) {
      String col = exprMatcher.group(1);
      String op = exprMatcher.group(2);
      int delta = Integer.parseInt(exprMatcher.group(3));
      int rhs = Integer.parseInt(exprMatcher.group(4));
      int target = "+".equals(op) ? rhs - delta : rhs + delta;
      return col + " = " + target;
    }
    return "-- 改写为不使用函数/表达式的谓词，使列单独出现在比较一侧";
  }

  private static String buildNonSargableRemediation(WherePredicate predicate) {
    if (predicate == null) {
      return null;
    }
  String upper = predicate.getOperator() == null ? "" : predicate.getOperator().toUpperCase(Locale.ROOT);
    if ("NOT IN".equals(upper) && StringUtils.hasText(predicate.getLeftExpression())) {
      String col = SqlNameParseUtil.stripBackticks(predicate.getLeftExpression());
      return "WHERE NOT EXISTS (SELECT 1 FROM <derived> d WHERE d."
          + SqlNameParseUtil.quoteColumnIfNeeded(col)
          + " = "
          + SqlNameParseUtil.quoteColumnIfNeeded(col)
          + ")";
    }
    if (("!=".equals(predicate.getOperator()) || "<>".equals(predicate.getOperator()))
        && StringUtils.hasText(predicate.getLeftExpression())) {
      String col = SqlNameParseUtil.stripBackticks(predicate.getLeftExpression());
      return "WHERE "
          + SqlNameParseUtil.quoteColumnIfNeeded(col)
          + " < "
          + predicate.getRightExpression()
          + " OR "
          + SqlNameParseUtil.quoteColumnIfNeeded(col)
          + " > "
          + predicate.getRightExpression();
    }
    return null;
  }

  private static String buildImplicitCastRemediation(WherePredicate predicate) {
    if (predicate == null) {
      return null;
    }
    String left = predicate.getLeftExpression() == null ? "" : predicate.getLeftExpression().trim();
    String right = predicate.getRightExpression() == null ? "" : predicate.getRightExpression().trim();
    if (SIMPLE_IDENTIFIER.matcher(left).matches() && QUOTED_NUMERIC_LITERAL.matcher(right).matches()) {
      String numeric = right.substring(1, right.length() - 1);
      return left + " = " + numeric;
    }
    if (SIMPLE_IDENTIFIER.matcher(right).matches() && QUOTED_NUMERIC_LITERAL.matcher(left).matches()) {
      String numeric = left.substring(1, left.length() - 1);
      return SqlNameParseUtil.stripBackticks(right) + " = " + numeric;
    }
    return left + " = " + right.replace("'", "");
  }

  private Map<String, TableSchemaMeta> buildMetaMap(List<TableSchemaMeta> tableMetadata) {
    Map<String, TableSchemaMeta> map = new HashMap<>();
    if (tableMetadata == null) {
      return map;
    }
    for (TableSchemaMeta meta : tableMetadata) {
      if (meta.getTableName() != null) {
        map.put(tableKey(meta.getTableName()), meta);
      }
    }
    return map;
  }

  private Map<String, String> buildAliasMap(SqlQueryBody body) {
    Map<String, String> map = new LinkedHashMap<>();
    for (TableReference ref : body.getFromTables()) {
      if (ref.getTableName() != null) {
        map.put(ref.getTableName().toLowerCase(Locale.ROOT), ref.getTableName());
        if (ref.getAlias() != null) {
          map.put(ref.getAlias().toLowerCase(Locale.ROOT), ref.getTableName());
        }
      }
    }
    for (JoinClause join : body.getJoins()) {
      if (join.getRightTableName() != null) {
        map.put(join.getRightTableName().toLowerCase(Locale.ROOT), join.getRightTableName());
        if (join.getRightTableAlias() != null) {
          map.put(join.getRightTableAlias().toLowerCase(Locale.ROOT), join.getRightTableName());
        }
      }
    }
    return map;
  }

  private static String tableKey(String tableName) {
    return tableName == null ? "" : tableName.toLowerCase(Locale.ROOT);
  }

  private TableSchemaMeta findLargestFromTable(SqlQueryBody body, Map<String, TableSchemaMeta> metaByTable) {
    TableSchemaMeta largest = null;
    for (TableReference ref : body.getFromTables()) {
      TableSchemaMeta meta = metaByTable.get(tableKey(ref.getTableName()));
      if (meta == null) {
        continue;
      }
      if (largest == null
          || (meta.getEstimatedRows() != null
              && (largest.getEstimatedRows() == null
                  || meta.getEstimatedRows() > largest.getEstimatedRows()))) {
        largest = meta;
      }
    }
    return largest;
  }

  private ColumnMeta resolveColumnMeta(
      Map<String, TableSchemaMeta> metaByTable,
      Map<String, String> aliasToTable,
      SqlQueryBody body,
      String tableRef,
      String column) {
    String tableName = resolveTableName(tableRef, aliasToTable, body);
    if (tableName == null || column == null) {
      return null;
    }
    TableSchemaMeta meta = metaByTable.get(tableKey(tableName));
    if (meta == null || meta.getColumns() == null) {
      return null;
    }
    String colKey = SqlNameParseUtil.stripBackticks(column);
    return meta.getColumns().stream()
        .filter(c -> colKey != null && colKey.equalsIgnoreCase(SqlNameParseUtil.stripBackticks(c.getColumnName())))
        .findFirst()
        .orElse(null);
  }

  private String resolveTableName(String tableRef, Map<String, String> aliasToTable, SqlQueryBody body) {
    if (StringUtils.hasText(tableRef)) {
      String key = tableRef.toLowerCase(Locale.ROOT);
      if (aliasToTable.containsKey(key)) {
        return aliasToTable.get(key);
      }
      return SqlNameParseUtil.stripBackticks(tableRef);
    }
    if (body.getFromTables().size() == 1) {
      return body.getFromTables().get(0).getTableName();
    }
    return null;
  }

  private static boolean columnTypesCompatible(ColumnMeta left, ColumnMeta right) {
    String l = normalizeDataType(left.getDataType());
    String r = normalizeDataType(right.getDataType());
    if (l == null || r == null) {
      return true;
    }
    if (l.equals(r)) {
      return true;
    }
    boolean lNumeric = isNumericType(l);
    boolean rNumeric = isNumericType(r);
    boolean lString = isStringType(l);
    boolean rString = isStringType(r);
    return (lNumeric && rNumeric) || (lString && rString);
  }

  private static String normalizeDataType(String dataType) {
    if (!StringUtils.hasText(dataType)) {
      return null;
    }
    return dataType.toLowerCase(Locale.ROOT).split("\\(")[0].trim();
  }

  private static boolean isNumericType(String type) {
    return type.contains("int")
        || type.contains("decimal")
        || type.contains("numeric")
        || "bigint".equals(type)
        || "float".equals(type)
        || "double".equals(type);
  }

  private static boolean isStringType(String type) {
    return type.contains("char") || "text".equals(type) || type.contains("blob");
  }

  private static String promotionType(ColumnMeta left, ColumnMeta right) {
    if (isNumericType(normalizeDataType(left.getDataType()))) {
      return normalizeDataType(right.getDataType());
    }
    return normalizeDataType(left.getDataType());
  }

  private static String resolvePrimaryKey(TableSchemaMeta meta, String fallback) {
    if (meta == null || meta.getIndexes() == null) {
      return fallback;
    }
    return meta.getIndexes().stream()
        .filter(i -> "PRIMARY".equalsIgnoreCase(i.getIndexName()))
        .filter(i -> i.getColumns() != null && !i.getColumns().isEmpty())
        .map(i -> i.getColumns().get(0).getColumnName())
        .findFirst()
        .orElse(fallback);
  }

  private static boolean hasExtraKeyword(PlanNode node, String keyword) {
    if (node.getExtras() == null) {
      return false;
    }
    String lower = keyword.toLowerCase(Locale.ROOT);
    for (String extra : node.getExtras()) {
      if (extra != null && extra.toLowerCase(Locale.ROOT).contains(lower)) {
        return true;
      }
    }
    return false;
  }

  private void addIssue(
      List<PlanIssue> issues,
      Set<String> dedupe,
      PlanIssueTypeEnum type,
      PlanIssueSeverityEnum severity,
      String title,
      String description,
      String tableName,
      String evidence,
      String remediationSql) {
    String key = type.name() + "|" + (tableName == null ? "" : tableName) + "|" + (evidence == null ? "" : evidence);
    if (!dedupe.add(key)) {
      return;
    }
    PlanIssue issue = new PlanIssue();
    issue.setIssueType(type);
    issue.setSeverity(severity);
    issue.setTitle(title);
    issue.setDescription(description);
    issue.setRelatedTable(tableName);
    issue.setEvidence(evidence);
    issue.setRemediationSql(remediationSql);
    issues.add(issue);
  }

  private String buildSummary(List<PlanIssue> issues) {
    if (issues.isEmpty()) {
      return "未发现明显性能风险点";
    }
    long critical = issues.stream().filter(i -> i.getSeverity() == PlanIssueSeverityEnum.CRITICAL).count();
    long warning = issues.stream().filter(i -> i.getSeverity() == PlanIssueSeverityEnum.WARNING).count();
    return String.format(Locale.ROOT, "共发现 %d 个问题（严重 %d，警告 %d）", issues.size(), critical, warning);
  }
}
