package com.sqlexplan.service;

import com.sqlexplan.config.SqlexplanProperties;
import com.sqlexplan.constant.SqlParseConstants;
import com.sqlexplan.constant.SqlRewriterConstants;
import com.sqlexplan.model.domain.ColumnMeta;
import com.sqlexplan.model.domain.IndexMeta;
import com.sqlexplan.model.domain.SqlQueryBody;
import com.sqlexplan.model.domain.SqlRewriteResult;
import com.sqlexplan.model.domain.SqlRewriteSuggestion;
import com.sqlexplan.model.domain.SqlStructure;
import com.sqlexplan.model.domain.SubqueryClause;
import com.sqlexplan.model.domain.TableSchemaMeta;
import com.sqlexplan.model.domain.WherePredicate;
import com.sqlexplan.model.enums.PlanIssueSeverityEnum;
import com.sqlexplan.model.enums.SqlRewriteRuleTypeEnum;
import com.sqlexplan.util.SqlNameParseUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 基于规则的 SQL 改写（不依赖 LLM）。
 */
@Service
public class SqlRewriter {

  private static final Pattern OR_PREDICATE_SPLIT =
      Pattern.compile("(?i)\\s+OR\\s+");

  private static final Pattern SELECT_STAR_PATTERN = Pattern.compile("(?i)\\bSELECT\\s+\\*\\s+FROM\\b");

  private static final Pattern YEAR_EQUALS =
      Pattern.compile("(?i)YEAR\\s*\\(\\s*([\\w.`$]+)\\s*\\)\\s*=\\s*(\\d{4})");

  private static final long COUNT_STAR_LARGE_TABLE_ROWS = 100_000L;

  private final SqlexplanProperties sqlexplanProperties;

  public SqlRewriter(SqlexplanProperties sqlexplanProperties) {
    this.sqlexplanProperties = sqlexplanProperties;
  }

  /**
   * 对 SQL 生成规则改写建议及可选的推荐 SQL。
   *
   * @param sql 原始 SQL
   * @param sqlStructure 已解析结构
   * @param tableMetadata 表元数据（改写 SELECT * 等时使用，可为空）
   * @return 改写结果
   */
  public SqlRewriteResult rewrite(
      String sql, SqlStructure sqlStructure, List<TableSchemaMeta> tableMetadata) {
    SqlRewriteResult result = new SqlRewriteResult();
    result.setOriginalSql(sql);
    result.setRecommendedSql(sql);
    if (!StringUtils.hasText(sql) || sqlStructure == null || sqlStructure.getBody() == null) {
      return result;
    }
    SqlQueryBody body = sqlStructure.getBody();
    Map<String, TableSchemaMeta> metaMap = buildMetaMap(tableMetadata);

    applySelectStarRule(sql, body, metaMap, result);
    applyOrToUnionRule(sql, body, result);
    applyInSubqueryRule(sql, body, result);
    applyInSubqueryJoinRule(sql, body, result);
    applyNotInSubqueryRule(sql, body, result);
    applyDeepPaginationRule(sql, body, metaMap, result);
    applyDistinctRule(sql, body, result);
    applyRedundantOrderByRule(body, result);
    applyLikeLeadingWildcardRule(sql, body, result);
    applyFunctionOnColumnRule(sql, body, result);
    applyImplicitCastRule(sql, body, metaMap, result);
    applyLimitWithoutOrderByRule(sql, body, metaMap, result);
    applyCountStarRule(body, metaMap, result);
    applyCorrelatedSubqueryRule(sql, body, result);

    if (!result.getSuggestions().isEmpty()) {
      String applied = pickBestRewrittenSql(result);
      if (StringUtils.hasText(applied)) {
        result.setRecommendedSql(applied.trim());
      }
    }
    return result;
  }

  private void applySelectStarRule(
      String sql,
      SqlQueryBody body,
      Map<String, TableSchemaMeta> metaMap,
      SqlRewriteResult result) {
    boolean hasStar =
        body.getSelectColumns().stream().anyMatch(c -> "*".equals(c.getExpression()));
    if (!hasStar && !SELECT_STAR_PATTERN.matcher(sql).find()) {
      return;
    }
    String tableName = body.getFromTables().isEmpty() ? null : body.getFromTables().get(0).getTableName();
    TableSchemaMeta meta = tableName == null ? null : metaMap.get(tableName.toLowerCase(Locale.ROOT));
    if (meta == null || meta.getColumns().isEmpty()) {
      SqlRewriteSuggestion suggestion = new SqlRewriteSuggestion();
      suggestion.setRuleType(SqlRewriteRuleTypeEnum.SELECT_STAR_NARROW);
      suggestion.setSeverity(PlanIssueSeverityEnum.INFO);
      suggestion.setDescription("避免 SELECT *，仅投影业务需要的列以降低 IO 与网络开销");
      result.getSuggestions().add(suggestion);
      return;
    }
    String columnList =
        meta.getColumns().stream()
            .map(ColumnMeta::getColumnName)
            .map(SqlNameParseUtil::quoteColumnIfNeeded)
            .collect(Collectors.joining(", "));
    String rewritten = SELECT_STAR_PATTERN.matcher(sql).replaceFirst(
        "SELECT " + columnList + " FROM");
    SqlRewriteSuggestion suggestion = new SqlRewriteSuggestion();
    suggestion.setRuleType(SqlRewriteRuleTypeEnum.SELECT_STAR_NARROW);
    suggestion.setSeverity(PlanIssueSeverityEnum.INFO);
    suggestion.setDescription("将 SELECT * 替换为表 " + tableName + " 的全部列名（若仅需部分列请手工删减）");
    suggestion.setRewrittenSql(rewritten);
    result.getSuggestions().add(suggestion);
  }

  private void applyOrToUnionRule(String sql, SqlQueryBody body, SqlRewriteResult result) {
    List<WherePredicate> orPredicates =
        body.getWherePredicates().stream()
            .filter(p -> "OR".equalsIgnoreCase(p.getOperator()))
            .collect(Collectors.toList());
    if (orPredicates.isEmpty()) {
      return;
    }
    String orText = orPredicates.get(0).getPredicateText();
    if (!StringUtils.hasText(orText)) {
      return;
    }
    String[] branches = OR_PREDICATE_SPLIT.split(orText.trim());
    if (branches.length < 2) {
      return;
    }
    String strippedSql = stripWhereClause(sql, orText);
    List<String> unionParts = new ArrayList<>();
    for (String branch : branches) {
      unionParts.add(insertAndPredicate(strippedSql, branch.trim()));
    }
    String rewritten = String.join(" UNION ALL ", unionParts);
    SqlRewriteSuggestion suggestion = new SqlRewriteSuggestion();
    suggestion.setRuleType(SqlRewriteRuleTypeEnum.OR_TO_UNION_ALL);
    suggestion.setSeverity(PlanIssueSeverityEnum.WARNING);
    suggestion.setDescription("将 OR 条件拆为 UNION ALL，便于各分支命中索引（需确认分支互斥性）");
    suggestion.setRewrittenSql(rewritten);
    result.getSuggestions().add(suggestion);
  }

  private void applyInSubqueryRule(String sql, SqlQueryBody body, SqlRewriteResult result) {
    boolean hasWhereInSubquery =
        body.getSubqueries().stream()
            .anyMatch(s -> SqlParseConstants.SUBQUERY_LOCATION_WHERE.equals(s.getLocation()));
    boolean hasInOperator =
        body.getWherePredicates().stream()
            .anyMatch(p -> "IN".equalsIgnoreCase(p.getOperator()));
    if (!hasWhereInSubquery || !hasInOperator) {
      return;
    }
    WherePredicate inPredicate =
        body.getWherePredicates().stream()
            .filter(p -> "IN".equalsIgnoreCase(p.getOperator()))
            .findFirst()
            .orElse(null);
    if (inPredicate == null || !StringUtils.hasText(inPredicate.getLeftExpression())) {
      return;
    }
    SubqueryClause sub =
        body.getSubqueries().stream()
            .filter(s -> SqlParseConstants.SUBQUERY_LOCATION_WHERE.equals(s.getLocation()))
            .findFirst()
            .orElse(null);
    if (sub == null || sub.getInnerStructure() == null || sub.getInnerStructure().getBody() == null) {
      SqlRewriteSuggestion hint = new SqlRewriteSuggestion();
      hint.setRuleType(SqlRewriteRuleTypeEnum.IN_SUBQUERY_TO_EXISTS);
      hint.setSeverity(PlanIssueSeverityEnum.WARNING);
      hint.setDescription("IN 子查询可改写为 EXISTS 或 INNER JOIN，减少对子查询结果物化");
      result.getSuggestions().add(hint);
      return;
    }
    String correlateColumn = guessCorrelateColumn(sub);
    if (!StringUtils.hasText(correlateColumn)) {
      SqlRewriteSuggestion hint = new SqlRewriteSuggestion();
      hint.setRuleType(SqlRewriteRuleTypeEnum.IN_SUBQUERY_TO_EXISTS);
      hint.setSeverity(PlanIssueSeverityEnum.WARNING);
      hint.setDescription("IN 子查询可改写为 EXISTS，并在外层与内层之间建立关联条件");
      result.getSuggestions().add(hint);
      return;
    }
    String column = SqlNameParseUtil.stripBackticks(inPredicate.getLeftExpression());
    String existsSql =
        sql.replaceFirst(
            "(?is)\\b" + Pattern.quote(inPredicate.getPredicateText()),
            "EXISTS (SELECT 1 FROM "
                + sub.getInnerStructure().getBody().getFromTables().stream()
                    .findFirst()
                    .map(t -> t.getTableName())
                    .orElse("inner_t")
                + " inner_t WHERE inner_t."
                + SqlNameParseUtil.quoteColumnIfNeeded(correlateColumn)
                + " = "
                + SqlNameParseUtil.quoteColumnIfNeeded(column)
                + ")");
    SqlRewriteSuggestion suggestion = new SqlRewriteSuggestion();
    suggestion.setRuleType(SqlRewriteRuleTypeEnum.IN_SUBQUERY_TO_EXISTS);
    suggestion.setSeverity(PlanIssueSeverityEnum.WARNING);
    suggestion.setDescription("将 IN (SELECT ...) 改写为 EXISTS，便于嵌套循环关联");
    suggestion.setRewrittenSql(existsSql);
    result.getSuggestions().add(suggestion);
  }

  private void applyInSubqueryJoinRule(String sql, SqlQueryBody body, SqlRewriteResult result) {
    WherePredicate inPredicate =
        body.getWherePredicates().stream()
            .filter(p -> "IN".equalsIgnoreCase(p.getOperator()))
            .findFirst()
            .orElse(null);
    SubqueryClause sub =
        body.getSubqueries().stream()
            .filter(s -> SqlParseConstants.SUBQUERY_LOCATION_WHERE.equals(s.getLocation()))
            .findFirst()
            .orElse(null);
    if (inPredicate == null || sub == null || sub.getInnerStructure() == null || sub.getInnerStructure().getBody() == null) {
      return;
    }
    String correlateColumn = guessCorrelateColumn(sub);
    if (!StringUtils.hasText(correlateColumn) || !StringUtils.hasText(inPredicate.getLeftExpression())) {
      return;
    }
    String innerTable =
        sub.getInnerStructure().getBody().getFromTables().stream()
            .findFirst()
            .map(t -> t.getTableName() == null ? "inner_t" : t.getTableName())
            .orElse("inner_t");
    String outerCol = SqlNameParseUtil.stripBackticks(inPredicate.getLeftExpression());
    String joinSql =
        sql.replaceFirst(
            "(?is)\\bWHERE\\b\\s+" + Pattern.quote(inPredicate.getPredicateText()),
            "INNER JOIN "
                + SqlNameParseUtil.quoteTableName(innerTable)
                + " inner_t ON inner_t."
                + SqlNameParseUtil.quoteColumnIfNeeded(correlateColumn)
                + " = "
                + SqlNameParseUtil.quoteColumnIfNeeded(outerCol));
    SqlRewriteSuggestion suggestion = new SqlRewriteSuggestion();
    suggestion.setRuleType(SqlRewriteRuleTypeEnum.IN_SUBQUERY_TO_JOIN);
    suggestion.setSeverity(PlanIssueSeverityEnum.WARNING);
    suggestion.setDescription("将 IN 子查询改为 INNER JOIN，避免子查询物化");
    suggestion.setRewrittenSql(joinSql);
    result.getSuggestions().add(suggestion);
  }

  private void applyNotInSubqueryRule(String sql, SqlQueryBody body, SqlRewriteResult result) {
    WherePredicate notIn =
        body.getWherePredicates().stream()
            .filter(p -> "NOT IN".equalsIgnoreCase(p.getOperator()))
            .findFirst()
            .orElse(null);
    if (notIn == null || !StringUtils.hasText(notIn.getLeftExpression())) {
      return;
    }
    SubqueryClause sub =
        body.getSubqueries().stream()
            .filter(s -> SqlParseConstants.SUBQUERY_LOCATION_WHERE.equals(s.getLocation()))
            .findFirst()
            .orElse(null);
    String innerTable =
        sub != null && sub.getInnerStructure() != null && sub.getInnerStructure().getBody() != null
            ? sub.getInnerStructure().getBody().getFromTables().stream()
                .findFirst()
                .map(t -> t.getTableName() == null ? "t2" : t.getTableName())
                .orElse("t2")
            : "t2";
    String col = SqlNameParseUtil.stripBackticks(notIn.getLeftExpression());
    String correlate = sub == null ? null : guessCorrelateColumn(sub);
    if (!StringUtils.hasText(correlate)) {
      correlate = col;
    }
    String rewritten =
        sql.replaceFirst(
            "(?is)\\b" + Pattern.quote(notIn.getPredicateText()),
            "NOT EXISTS (SELECT 1 FROM "
                + SqlNameParseUtil.quoteTableName(innerTable)
                + " t2 WHERE t2."
                + SqlNameParseUtil.quoteColumnIfNeeded(correlate)
                + " = "
                + SqlNameParseUtil.quoteColumnIfNeeded(col)
                + ")");
    SqlRewriteSuggestion suggestion = new SqlRewriteSuggestion();
    suggestion.setRuleType(SqlRewriteRuleTypeEnum.NOT_IN_TO_NOT_EXISTS);
    suggestion.setSeverity(PlanIssueSeverityEnum.WARNING);
    suggestion.setDescription("NOT IN 改写为 NOT EXISTS，规避 NULL 陷阱并利于半连接优化");
    suggestion.setRewrittenSql(rewritten);
    result.getSuggestions().add(suggestion);
  }

  private String guessCorrelateColumn(SubqueryClause sub) {
    if (sub == null || sub.getInnerStructure() == null || sub.getInnerStructure().getBody() == null) {
      return null;
    }
    SqlQueryBody inner = sub.getInnerStructure().getBody();
    if (inner.getWherePredicates().isEmpty()) {
      return null;
    }
    String[] parts =
        SqlNameParseUtil.parseQualifiedColumn(inner.getWherePredicates().get(0).getLeftExpression());
    return parts[1];
  }

  private void applyDeepPaginationRule(
      String sql,
      SqlQueryBody body,
      Map<String, TableSchemaMeta> metaMap,
      SqlRewriteResult result) {
    Long offset = body.getOffset();
    long threshold = sqlexplanProperties.getExplain().getDeepPaginationOffsetThreshold();
    if (offset == null || offset < threshold) {
      return;
    }
    if (body.getFromTables().isEmpty()) {
      return;
    }
    String tableName = body.getFromTables().get(0).getTableName();
    TableSchemaMeta meta = metaMap.get(tableName.toLowerCase(Locale.ROOT));
    String pk = resolvePrimaryKeyColumn(meta);
    if (!StringUtils.hasText(pk)) {
      pk = "id";
    }
    Long limit = body.getLimit() == null ? SqlRewriterConstants.DEFERRED_JOIN_INNER_LIMIT : body.getLimit();
    String orderCol =
        body.getOrderByColumns().isEmpty() ? pk : body.getOrderByColumns().get(0);
    String orderColumn = SqlNameParseUtil.stripBackticks(orderCol);
    String alias = body.getFromTables().get(0).getAlias();
    String tableRef = alias != null ? alias : tableName;
    String inner =
        "(SELECT "
            + SqlNameParseUtil.quoteColumnIfNeeded(pk)
            + " FROM "
            + SqlNameParseUtil.quoteTableName(tableName)
            + " ORDER BY "
            + SqlNameParseUtil.quoteColumnIfNeeded(orderColumn)
            + " LIMIT "
            + limit
            + " OFFSET "
            + offset
            + ") page_ids";
    String rewritten =
        "SELECT "
            + tableRef
            + ".* FROM "
            + SqlNameParseUtil.quoteTableName(tableName)
            + " "
            + tableRef
            + " INNER JOIN "
            + inner
            + " ON "
            + tableRef
            + "."
            + SqlNameParseUtil.quoteColumnIfNeeded(pk)
            + " = page_ids."
            + SqlNameParseUtil.quoteColumnIfNeeded(pk);
    SqlRewriteSuggestion suggestion = new SqlRewriteSuggestion();
    suggestion.setRuleType(SqlRewriteRuleTypeEnum.DEEP_PAGINATION_DEFERRED_JOIN);
    suggestion.setSeverity(PlanIssueSeverityEnum.WARNING);
    suggestion.setDescription(
        "深分页 OFFSET "
            + offset
            + " 建议延迟关联：子查询仅拉取主键再回表，降低大偏移扫描");
    suggestion.setRewrittenSql(rewritten);
    result.getSuggestions().add(suggestion);
  }

  private void applyDistinctRule(String sql, SqlQueryBody body, SqlRewriteResult result) {
    if (!Pattern.compile(SqlRewriterConstants.SELECT_DISTINCT_PATTERN).matcher(sql).find()) {
      return;
    }
    if (!body.getGroupByColumns().isEmpty()) {
      return;
    }
    String rewritten = sql.replaceFirst("(?i)\\bDISTINCT\\b", "").replaceAll("\\s+", " ").trim();
    SqlRewriteSuggestion suggestion = new SqlRewriteSuggestion();
    suggestion.setRuleType(SqlRewriteRuleTypeEnum.REMOVE_REDUNDANT_DISTINCT);
    suggestion.setSeverity(PlanIssueSeverityEnum.INFO);
    suggestion.setDescription("无 GROUP BY 时若结果本无重复，可去掉 DISTINCT 以减少去重开销");
    suggestion.setRewrittenSql(rewritten);
    result.getSuggestions().add(suggestion);
  }

  private void applyRedundantOrderByRule(SqlQueryBody body, SqlRewriteResult result) {
    if (body.getOrderByColumns().isEmpty()) {
      return;
    }
    if (body.getLimit() != null || body.getOffset() != null) {
      return;
    }
    if (!body.getGroupByColumns().isEmpty() || !body.getAggregateFunctions().isEmpty()) {
      return;
    }
    SqlRewriteSuggestion suggestion = new SqlRewriteSuggestion();
    suggestion.setRuleType(SqlRewriteRuleTypeEnum.REMOVE_REDUNDANT_ORDER_BY);
    suggestion.setSeverity(PlanIssueSeverityEnum.INFO);
    suggestion.setDescription(
        "无 LIMIT 且无聚合时，排序不改变结果集，可去掉 ORDER BY 避免 filesort");
    result.getSuggestions().add(suggestion);
  }

  private void applyLikeLeadingWildcardRule(String sql, SqlQueryBody body, SqlRewriteResult result) {
    for (WherePredicate predicate : body.getWherePredicates()) {
      if (!"LIKE".equalsIgnoreCase(predicate.getOperator())
          || !SqlNameParseUtil.isLeadingWildcardLike(predicate.getRightExpression())) {
        continue;
      }
      String tableName =
          body.getFromTables().isEmpty() ? "tbl" : body.getFromTables().get(0).getTableName();
      String[] parts = SqlNameParseUtil.parseQualifiedColumn(predicate.getLeftExpression());
      String col = parts[1] == null ? "col" : parts[1];
      SqlRewriteSuggestion suggestion = new SqlRewriteSuggestion();
      suggestion.setRuleType(SqlRewriteRuleTypeEnum.LIKE_LEADING_WILDCARD);
      suggestion.setSeverity(PlanIssueSeverityEnum.WARNING);
      suggestion.setDescription("LIKE 前导 % 无法走 B-Tree，可尝试全文索引或业务侧倒排");
      suggestion.setRewrittenSql(
          "CREATE FULLTEXT INDEX idx_"
              + col
              + "_ft ON "
              + SqlNameParseUtil.quoteTableName(tableName)
              + " ("
              + SqlNameParseUtil.quoteColumnIfNeeded(col)
              + ");\n"
              + sql.replace(
                  predicate.getPredicateText(),
                  "MATCH("
                      + SqlNameParseUtil.quoteColumnIfNeeded(col)
                      + ") AGAINST('keyword' IN BOOLEAN MODE)"));
      result.getSuggestions().add(suggestion);
      break;
    }
  }

  private void applyFunctionOnColumnRule(String sql, SqlQueryBody body, SqlRewriteResult result) {
    for (WherePredicate predicate : body.getWherePredicates()) {
      if (!Boolean.TRUE.equals(predicate.getColumnWrappedByFunction())
          || !StringUtils.hasText(predicate.getPredicateText())) {
        continue;
      }
      Matcher yearMatcher = YEAR_EQUALS.matcher(predicate.getPredicateText());
      if (!yearMatcher.find()) {
        continue;
      }
      String col = yearMatcher.group(1);
      String year = yearMatcher.group(2);
      String rangeCond =
          col
              + " >= '"
              + year
              + "-01-01' AND "
              + col
              + " < '"
              + (Integer.parseInt(year) + 1)
              + "-01-01'";
      String rewritten = sql.replace(predicate.getPredicateText(), rangeCond);
      SqlRewriteSuggestion suggestion = new SqlRewriteSuggestion();
      suggestion.setRuleType(SqlRewriteRuleTypeEnum.FUNCTION_ON_COLUMN_TO_RANGE);
      suggestion.setSeverity(PlanIssueSeverityEnum.WARNING);
      suggestion.setDescription("将 YEAR(列) 等函数条件改为列上的范围比较以利用索引");
      suggestion.setRewrittenSql(rewritten);
      result.getSuggestions().add(suggestion);
      break;
    }
  }

  private void applyImplicitCastRule(
      String sql, SqlQueryBody body, Map<String, TableSchemaMeta> metaMap, SqlRewriteResult result) {
    Map<String, String> aliasToTable = buildAliasMap(body);
    for (WherePredicate predicate : body.getWherePredicates()) {
      if (!"=".equals(predicate.getOperator())) {
        continue;
      }
      String left = predicate.getLeftExpression();
      String right = predicate.getRightExpression();
      if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
        continue;
      }
      String l = left.trim();
      String r = right.trim();
      String rewrittenPredicate =
          buildImplicitCastRewrite(body, metaMap, aliasToTable, l, r, predicate.getOperator());
      if (rewrittenPredicate == null) {
        continue;
      }
      String rewritten = sql.replace(predicate.getPredicateText(), rewrittenPredicate);
      SqlRewriteSuggestion suggestion = new SqlRewriteSuggestion();
      suggestion.setRuleType(SqlRewriteRuleTypeEnum.IMPLICIT_CAST_NORMALIZE);
      suggestion.setSeverity(PlanIssueSeverityEnum.INFO);
      suggestion.setDescription("统一字面量类型，避免隐式类型转换导致索引失效");
      suggestion.setRewrittenSql(rewritten);
      result.getSuggestions().add(suggestion);
      break;
    }
  }

  private String buildImplicitCastRewrite(
      SqlQueryBody body,
      Map<String, TableSchemaMeta> metaMap,
      Map<String, String> aliasToTable,
      String left,
      String right,
      String operator) {
    boolean colLeft = left.matches("^[\\w.`$]+$");
    boolean colRight = right.matches("^[\\w.`$]+$");
    boolean quotedNumRight = right.matches("^'\\d+(\\.\\d+)?'$");
    boolean quotedNumLeft = left.matches("^'\\d+(\\.\\d+)?'$");
    boolean unquotedNumRight = right.matches("^\\d+(\\.\\d+)?$");
    boolean unquotedNumLeft = left.matches("^\\d+(\\.\\d+)?$");
    ColumnMeta columnMeta = null;
    String literalSide = null;
    boolean literalQuoted = false;
    if (colLeft && (quotedNumRight || unquotedNumRight)) {
      String[] parts = SqlNameParseUtil.parseQualifiedColumn(left);
      columnMeta = resolveColumnMeta(metaMap, aliasToTable, body, parts[0], parts[1]);
      literalSide = right;
      literalQuoted = quotedNumRight;
    } else if (colRight && (quotedNumLeft || unquotedNumLeft)) {
      String[] parts = SqlNameParseUtil.parseQualifiedColumn(right);
      columnMeta = resolveColumnMeta(metaMap, aliasToTable, body, parts[0], parts[1]);
      literalSide = left;
      literalQuoted = quotedNumLeft;
    } else {
      return null;
    }
    if (columnMeta == null || !StringUtils.hasText(columnMeta.getDataType())) {
      return null;
    }
    String type = normalizeDataType(columnMeta.getDataType());
    if (type == null) {
      return null;
    }
    String columnExpr = colLeft ? left : right;
    if (isNumericType(type)) {
      if (!literalQuoted) {
        return null;
      }
      String numeric = literalSide.substring(1, literalSide.length() - 1);
      return columnExpr + " " + operator + " " + numeric;
    }
    if (isStringType(type)) {
      if (literalQuoted) {
        return null;
      }
      return columnExpr + " " + operator + " '" + literalSide + "'";
    }
    return null;
  }

  private ColumnMeta resolveColumnMeta(
      Map<String, TableSchemaMeta> metaMap,
      Map<String, String> aliasToTable,
      SqlQueryBody body,
      String tableRef,
      String column) {
    String tableName = resolveTableName(tableRef, aliasToTable, body);
    if (tableName == null || column == null) {
      return null;
    }
    TableSchemaMeta meta = metaMap.get(tableName.toLowerCase(Locale.ROOT));
    if (meta == null || meta.getColumns() == null) {
      return null;
    }
    String colKey = SqlNameParseUtil.stripBackticks(column);
    return meta.getColumns().stream()
        .filter(
            c ->
                colKey != null
                    && colKey.equalsIgnoreCase(SqlNameParseUtil.stripBackticks(c.getColumnName())))
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

  private Map<String, String> buildAliasMap(SqlQueryBody body) {
    Map<String, String> aliasToTable = new java.util.LinkedHashMap<>();
    for (var ref : body.getFromTables()) {
      if (ref.getTableName() != null) {
        String table = SqlNameParseUtil.stripBackticks(ref.getTableName());
        aliasToTable.put(table.toLowerCase(Locale.ROOT), table);
        if (ref.getAlias() != null) {
          aliasToTable.put(ref.getAlias().toLowerCase(Locale.ROOT), table);
        }
      }
    }
    for (var join : body.getJoins()) {
      if (join.getRightTableAlias() != null && join.getRightTableName() != null) {
        aliasToTable.put(join.getRightTableAlias().toLowerCase(Locale.ROOT), join.getRightTableName());
      }
      if (join.getRightTableName() != null) {
        aliasToTable.put(join.getRightTableName().toLowerCase(Locale.ROOT), join.getRightTableName());
      }
    }
    return aliasToTable;
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

  private void applyLimitWithoutOrderByRule(
      String sql, SqlQueryBody body, Map<String, TableSchemaMeta> metaMap, SqlRewriteResult result) {
    if (body.getLimit() == null || body.getLimit() <= 0 || !body.getOrderByColumns().isEmpty()) {
      return;
    }
    String tableName = body.getFromTables().isEmpty() ? null : body.getFromTables().get(0).getTableName();
    TableSchemaMeta meta = tableName == null ? null : metaMap.get(tableName.toLowerCase(Locale.ROOT));
    String pk = resolvePrimaryKeyColumn(meta);
    if (!StringUtils.hasText(pk)) {
      pk = "id";
    }
    String rewritten =
        sql.trim()
            .replaceFirst(
                "(?i)\\bLIMIT\\s+" + Pattern.quote(String.valueOf(body.getLimit())),
                "ORDER BY " + SqlNameParseUtil.quoteColumnIfNeeded(pk) + " LIMIT " + body.getLimit());
    SqlRewriteSuggestion suggestion = new SqlRewriteSuggestion();
    suggestion.setRuleType(SqlRewriteRuleTypeEnum.LIMIT_ADD_ORDER_BY);
    suggestion.setSeverity(PlanIssueSeverityEnum.INFO);
    suggestion.setDescription("LIMIT 无 ORDER BY 时补上稳定排序键，避免结果顺序漂移");
    suggestion.setRewrittenSql(rewritten);
    result.getSuggestions().add(suggestion);
  }

  private void applyCountStarRule(
      SqlQueryBody body, Map<String, TableSchemaMeta> metaMap, SqlRewriteResult result) {
    boolean hasCountStar =
        body.getAggregateFunctions().stream()
            .anyMatch(
                a ->
                    a != null
                        && "COUNT".equalsIgnoreCase(a.getFunctionName())
                        && (a.getArgumentsText() == null
                            || a.getArgumentsText().isBlank()
                            || "*".equals(a.getArgumentsText().trim())));
    if (!hasCountStar || body.getFromTables().isEmpty()) {
      return;
    }
    String tableName = body.getFromTables().get(0).getTableName();
    TableSchemaMeta meta = metaMap.get(tableName.toLowerCase(Locale.ROOT));
    if (meta == null
        || meta.getEstimatedRows() == null
        || meta.getEstimatedRows() < COUNT_STAR_LARGE_TABLE_ROWS) {
      return;
    }
    SqlRewriteSuggestion suggestion = new SqlRewriteSuggestion();
    suggestion.setRuleType(SqlRewriteRuleTypeEnum.COUNT_STAR_LARGE_TABLE_HINT);
    suggestion.setSeverity(PlanIssueSeverityEnum.WARNING);
    suggestion.setDescription("大表 COUNT(*) 建议使用 information_schema 近似行数或维护计数表");
    suggestion.setRewrittenSql(
        "SELECT table_rows FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = '"
            + tableName
            + "'");
    result.getSuggestions().add(suggestion);
  }

  private void applyCorrelatedSubqueryRule(String sql, SqlQueryBody body, SqlRewriteResult result) {
    for (SubqueryClause sub : body.getSubqueries()) {
      if (sub.getInnerStructure() == null || sub.getInnerStructure().getBody() == null) {
        continue;
      }
      SqlQueryBody inner = sub.getInnerStructure().getBody();
      String correlateCond =
          inner.getWherePredicates().stream()
              .map(WherePredicate::getPredicateText)
              .filter(t -> t != null && t.contains("."))
              .findFirst()
              .orElse(null);
      if (!StringUtils.hasText(correlateCond)) {
        continue;
      }
      Matcher matcher =
          Pattern.compile(
                  "([\\w.`$]+)\\s*=\\s*([\\w.`$]+\\.[\\w.`$]+)",
                  Pattern.CASE_INSENSITIVE)
              .matcher(correlateCond);
      if (!matcher.find()) {
        continue;
      }
      String innerTable =
          inner.getFromTables().stream()
              .findFirst()
              .map(t -> t.getTableName() == null ? "inner_t" : t.getTableName())
              .orElse("inner_t");
      String innerCol = matcher.group(1);
      String outerRef = matcher.group(2);
      String joinSql =
          "SELECT outer_t.* FROM "
              + (body.getFromTables().isEmpty() ? "outer_table" : body.getFromTables().get(0).getTableName())
              + " outer_t INNER JOIN "
              + SqlNameParseUtil.quoteTableName(innerTable)
              + " inner_t ON inner_t."
              + SqlNameParseUtil.quoteColumnIfNeeded(innerCol)
              + " = "
              + outerRef.replace("`", "");
      SqlRewriteSuggestion suggestion = new SqlRewriteSuggestion();
      suggestion.setRuleType(SqlRewriteRuleTypeEnum.CORRELATED_SUBQUERY_TO_JOIN);
      suggestion.setSeverity(PlanIssueSeverityEnum.WARNING);
      suggestion.setDescription("将关联子查询改写为 JOIN，避免对驱动表逐行执行子查询");
      suggestion.setRewrittenSql(joinSql);
      result.getSuggestions().add(suggestion);
      break;
    }
  }

  private String resolvePrimaryKeyColumn(TableSchemaMeta meta) {
    if (meta == null || meta.getIndexes() == null) {
      return null;
    }
    for (IndexMeta index : meta.getIndexes()) {
      if ("PRIMARY".equalsIgnoreCase(index.getIndexName())
          && index.getColumns() != null
          && !index.getColumns().isEmpty()) {
        return index.getColumns().get(0).getColumnName();
      }
    }
    return null;
  }

  private Map<String, TableSchemaMeta> buildMetaMap(List<TableSchemaMeta> tableMetadata) {
    if (tableMetadata == null) {
      return Map.of();
    }
    return tableMetadata.stream()
        .filter(m -> m.getTableName() != null)
        .collect(
            Collectors.toMap(
                m -> m.getTableName().toLowerCase(Locale.ROOT),
                m -> m,
                (a, b) -> a));
  }

  private String stripWhereClause(String sql, String orPredicateText) {
    Matcher matcher = Pattern.compile("(?is)\\bWHERE\\b\\s+" + Pattern.quote(orPredicateText)).matcher(sql);
    if (matcher.find()) {
      return sql.substring(0, matcher.start()) + sql.substring(matcher.end());
    }
    return sql.replaceFirst("(?is)\\bWHERE\\b\\s+.*", "");
  }

  private String insertAndPredicate(String sqlWithoutOrWhere, String predicate) {
    String trimmed = sqlWithoutOrWhere.trim();
    if (Pattern.compile("(?i)\\bWHERE\\b").matcher(trimmed).find()) {
      return trimmed + " AND " + predicate;
    }
    return trimmed + " WHERE " + predicate;
  }

  private String pickBestRewrittenSql(SqlRewriteResult result) {
    for (SqlRewriteRuleTypeEnum priority :
        List.of(
            SqlRewriteRuleTypeEnum.DEEP_PAGINATION_DEFERRED_JOIN,
            SqlRewriteRuleTypeEnum.IN_SUBQUERY_TO_EXISTS,
            SqlRewriteRuleTypeEnum.SELECT_STAR_NARROW,
            SqlRewriteRuleTypeEnum.OR_TO_UNION_ALL,
            SqlRewriteRuleTypeEnum.REMOVE_REDUNDANT_DISTINCT,
            SqlRewriteRuleTypeEnum.FUNCTION_ON_COLUMN_TO_RANGE,
            SqlRewriteRuleTypeEnum.IMPLICIT_CAST_NORMALIZE,
            SqlRewriteRuleTypeEnum.IN_SUBQUERY_TO_JOIN,
            SqlRewriteRuleTypeEnum.NOT_IN_TO_NOT_EXISTS)) {
      for (SqlRewriteSuggestion suggestion : result.getSuggestions()) {
        if (suggestion.getRuleType() == priority && StringUtils.hasText(suggestion.getRewrittenSql())) {
          return suggestion.getRewrittenSql();
        }
      }
    }
    return result.getOriginalSql();
  }
}
