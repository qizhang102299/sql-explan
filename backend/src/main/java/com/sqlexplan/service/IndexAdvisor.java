package com.sqlexplan.service;

import com.sqlexplan.constant.IndexAdvisorConstants;
import com.sqlexplan.model.domain.IndexAdvice;
import com.sqlexplan.model.domain.IndexAdviceReport;
import com.sqlexplan.model.domain.IndexMeta;
import com.sqlexplan.model.domain.JoinClause;
import com.sqlexplan.model.domain.SelectColumnItem;
import com.sqlexplan.model.domain.PlanInterpretation;
import com.sqlexplan.model.domain.SqlQueryBody;
import com.sqlexplan.model.domain.SqlStructure;
import com.sqlexplan.model.domain.TableReference;
import com.sqlexplan.model.domain.TableSchemaMeta;
import com.sqlexplan.model.domain.WherePredicate;
import com.sqlexplan.model.enums.IndexAdviceTypeEnum;
import com.sqlexplan.model.enums.PlanIssueSeverityEnum;
import com.sqlexplan.util.SqlNameParseUtil;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 基于 SQL 结构、表元数据与可选计划解读的索引规则建议。
 */
@Service
public class IndexAdvisor {

  /**
   * 生成索引建议。
   *
   * @param sqlStructure 解析后的 SQL 结构
   * @param tableMetadata 涉及表的 schema 元数据
   * @param planInterpretation 执行计划解读（可为 null）
   * @return 索引建议报告
   */
  public IndexAdviceReport advise(
      SqlStructure sqlStructure,
      List<TableSchemaMeta> tableMetadata,
      PlanInterpretation planInterpretation) {
    IndexAdviceReport report = new IndexAdviceReport();
    if (sqlStructure == null || sqlStructure.getBody() == null) {
      report.setSummary("无法解析 SQL 主体，未生成索引建议");
      return report;
    }
    Map<String, TableSchemaMeta> metaByTable = buildMetaMap(tableMetadata);
    Map<String, String> aliasToTable = buildAliasMap(sqlStructure.getBody());
    Map<String, TableColumnPlan> plans = new LinkedHashMap<>();
    collectColumnUsage(sqlStructure.getBody(), aliasToTable, plans);
    detectRedundantIndexes(metaByTable, report);
    detectDuplicateIndexes(metaByTable, report);
    adviseNonSargablePredicates(sqlStructure.getBody(), report);
    for (Map.Entry<String, TableColumnPlan> entry : plans.entrySet()) {
      String tableName = entry.getKey();
      TableColumnPlan plan = entry.getValue();
      TableSchemaMeta meta = metaByTable.get(tableName.toLowerCase(Locale.ROOT));
      if (plan.isEmpty()) {
        continue;
      }
      List<String> orderedColumns = plan.buildOrderedColumnList();
      if (orderedColumns.isEmpty()) {
        continue;
      }
      IndexMeta reusable = findReusableIndex(meta, orderedColumns);
      if (reusable != null && indexCoversPrefix(reusable, orderedColumns)) {
        IndexAdvice reuse = new IndexAdvice();
        reuse.setSeverity(PlanIssueSeverityEnum.INFO);
        reuse.setAdviceType(IndexAdviceTypeEnum.REUSE_EXISTING_INDEX);
        reuse.setTableName(tableName);
        reuse.setRelatedIndexName(reusable.getIndexName());
        reuse.setReason(
            "已有索引 " + reusable.getIndexName() + " 的最左前缀可覆盖当前 WHERE/JOIN/排序列");
        reuse.setExpectedBenefit("优先确认优化器选用索引 " + reusable.getIndexName() + "，避免重复建索引");
        reuse.setWriteCostNote("无额外写入开销");
        report.getAdvices().add(reuse);
        continue;
      }
      List<String> coveringColumns = plan.buildCoveringColumnList();
      boolean covering = isCoveringIndex(plan, coveringColumns);
      List<String> indexColumns = covering ? coveringColumns : orderedColumns;
      String ddl = buildCreateIndexDdl(tableName, indexColumns);
      IndexAdvice advice = new IndexAdvice();
      advice.setSeverity(covering ? PlanIssueSeverityEnum.INFO : PlanIssueSeverityEnum.WARNING);
      advice.setAdviceType(
          covering ? IndexAdviceTypeEnum.COVERING_INDEX : IndexAdviceTypeEnum.CREATE_INDEX);
      advice.setTableName(tableName);
      advice.setCreateIndexDdl(ddl);
      advice.setReason(buildReason(plan, planInterpretation, covering, indexColumns));
      advice.setExpectedBenefit(buildExpectedBenefit(plan, meta));
      advice.setWriteCostNote(buildWriteCostNote(meta, orderedColumns.size()));
      report.getAdvices().add(advice);
    }
    report.setSummary(buildSummary(report));
    return report;
  }

  private void detectRedundantIndexes(Map<String, TableSchemaMeta> metaByTable, IndexAdviceReport report) {
    for (TableSchemaMeta meta : metaByTable.values()) {
      if (meta.getIndexes() == null || meta.getIndexes().size() < 2) {
        continue;
      }
      List<IndexMeta> nonPrimary =
          meta.getIndexes().stream()
              .filter(i -> i.getIndexName() != null && !"PRIMARY".equalsIgnoreCase(i.getIndexName()))
              .collect(Collectors.toList());
      for (int i = 0; i < nonPrimary.size(); i++) {
        for (int j = 0; j < nonPrimary.size(); j++) {
          if (i == j) {
            continue;
          }
          IndexMeta shorter = nonPrimary.get(i);
          IndexMeta longer = nonPrimary.get(j);
          if (isStrictIndexPrefix(shorter, longer)) {
            IndexAdvice redundant = new IndexAdvice();
            redundant.setSeverity(PlanIssueSeverityEnum.INFO);
            redundant.setAdviceType(IndexAdviceTypeEnum.REDUNDANT_INDEX);
            redundant.setTableName(meta.getTableName());
            redundant.setRelatedIndexName(shorter.getIndexName());
            redundant.setReason(
                "索引 "
                    + shorter.getIndexName()
                    + " 的列序是 "
                    + longer.getIndexName()
                    + " 的最左前缀，前者可能冗余");
            redundant.setExpectedBenefit("删除冗余索引 "
                + shorter.getIndexName()
                + " 可降低写入开销，查询可继续使用 "
                + longer.getIndexName());
            redundant.setWriteCostNote("删除前需在影子库验证执行计划无回归");
            report.getAdvices().add(redundant);
          }
        }
      }
    }
  }

  private void detectDuplicateIndexes(Map<String, TableSchemaMeta> metaByTable, IndexAdviceReport report) {
    for (TableSchemaMeta meta : metaByTable.values()) {
      if (meta.getIndexes() == null || meta.getIndexes().size() < 2) {
        continue;
      }
      List<IndexMeta> nonPrimary =
          meta.getIndexes().stream()
              .filter(i -> i.getIndexName() != null && !"PRIMARY".equalsIgnoreCase(i.getIndexName()))
              .collect(Collectors.toList());
      for (int i = 0; i < nonPrimary.size(); i++) {
        for (int j = i + 1; j < nonPrimary.size(); j++) {
          IndexMeta a = nonPrimary.get(i);
          IndexMeta b = nonPrimary.get(j);
          if (sameIndexColumns(a, b)) {
            IndexAdvice duplicate = new IndexAdvice();
            duplicate.setSeverity(PlanIssueSeverityEnum.WARNING);
            duplicate.setAdviceType(IndexAdviceTypeEnum.DUPLICATE_INDEX);
            duplicate.setTableName(meta.getTableName());
            duplicate.setRelatedIndexName(a.getIndexName());
            duplicate.setReason(
                "索引 "
                    + a.getIndexName()
                    + " 与 "
                    + b.getIndexName()
                    + " 列序完全相同，属于重复索引");
            duplicate.setExpectedBenefit(
                "保留 "
                    + b.getIndexName()
                    + "，删除 "
                    + a.getIndexName()
                    + " 以减少维护成本");
            duplicate.setWriteCostNote("DROP INDEX " + a.getIndexName() + " ON " + meta.getTableName() + ";");
            duplicate.setCreateIndexDdl("DROP INDEX " + a.getIndexName() + " ON " + SqlNameParseUtil.quoteTableName(meta.getTableName()));
            report.getAdvices().add(duplicate);
          }
        }
      }
    }
  }

  private boolean sameIndexColumns(IndexMeta a, IndexMeta b) {
    List<String> colsA = indexColumnNames(a);
    List<String> colsB = indexColumnNames(b);
    if (colsA.isEmpty() || colsA.size() != colsB.size()) {
      return false;
    }
    for (int i = 0; i < colsA.size(); i++) {
      if (!colsA.get(i).equalsIgnoreCase(colsB.get(i))) {
        return false;
      }
    }
    return true;
  }

  private void adviseNonSargablePredicates(SqlQueryBody body, IndexAdviceReport report) {
    if (body == null || body.getWherePredicates() == null) {
      return;
    }
    for (WherePredicate predicate : body.getWherePredicates()) {
      if (predicate == null || predicate.getOperator() == null) {
        continue;
      }
      String op = predicate.getOperator().toUpperCase(Locale.ROOT);
      if (!("NOT IN".equals(op) || "!=".equals(predicate.getOperator()) || "<>".equals(predicate.getOperator()))) {
        continue;
      }
      IndexAdvice hint = new IndexAdvice();
      hint.setSeverity(PlanIssueSeverityEnum.INFO);
      hint.setAdviceType(IndexAdviceTypeEnum.NON_SARGABLE_OPERATOR_HINT);
      hint.setReason("运算符 " + op + " 难以直接利用 B-Tree 等值查找：" + predicate.getPredicateText());
      hint.setExpectedBenefit("建议改写为 NOT EXISTS、拆分 OR 范围，或接受索引范围扫描");
      hint.setWriteCostNote("无索引 DDL；优先 SQL 改写");
      if ("NOT IN".equals(op) && StringUtils.hasText(predicate.getLeftExpression())) {
        String col = SqlNameParseUtil.stripBackticks(predicate.getLeftExpression());
        hint.setCreateIndexDdl(
            "-- 推荐改写\nSELECT ... WHERE NOT EXISTS (SELECT 1 FROM t2 WHERE t2."
                + SqlNameParseUtil.quoteColumnIfNeeded(col)
                + " = outer."
                + SqlNameParseUtil.quoteColumnIfNeeded(col)
                + ")");
      } else if (StringUtils.hasText(predicate.getLeftExpression())) {
        String col = SqlNameParseUtil.stripBackticks(predicate.getLeftExpression());
        hint.setCreateIndexDdl(
            "SELECT ... WHERE "
                + SqlNameParseUtil.quoteColumnIfNeeded(col)
                + " < "
                + predicate.getRightExpression()
                + " OR "
                + SqlNameParseUtil.quoteColumnIfNeeded(col)
                + " > "
                + predicate.getRightExpression());
      }
      report.getAdvices().add(hint);
    }
  }

  private boolean isStrictIndexPrefix(IndexMeta prefix, IndexMeta full) {
    List<String> prefixCols = indexColumnNames(prefix);
    List<String> fullCols = indexColumnNames(full);
    if (prefixCols.isEmpty() || fullCols.size() <= prefixCols.size()) {
      return false;
    }
    for (int i = 0; i < prefixCols.size(); i++) {
      if (!prefixCols.get(i).equalsIgnoreCase(fullCols.get(i))) {
        return false;
      }
    }
    return true;
  }

  private List<String> indexColumnNames(IndexMeta index) {
    if (index == null || index.getColumns() == null) {
      return List.of();
    }
    return index.getColumns().stream()
        .sorted((a, b) -> Integer.compare(
            a.getSeqInIndex() == null ? 0 : a.getSeqInIndex(),
            b.getSeqInIndex() == null ? 0 : b.getSeqInIndex()))
        .map(c -> SqlNameParseUtil.stripBackticks(c.getColumnName()))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  private void collectColumnUsage(
      SqlQueryBody body, Map<String, String> aliasToTable, Map<String, TableColumnPlan> plans) {
    if (body == null) {
      return;
    }
    for (WherePredicate predicate : body.getWherePredicates()) {
      if ("OR".equalsIgnoreCase(predicate.getOperator())) {
        extractColumnsFromOrPredicate(predicate, aliasToTable, body, plans);
        continue;
      }
      if (Boolean.TRUE.equals(predicate.getColumnWrappedByFunction())) {
        continue;
      }
      String[] parts = SqlNameParseUtil.parseQualifiedColumn(predicate.getLeftExpression());
      String tableRef = parts[0];
      String column = parts[1];
      if (!StringUtils.hasText(column)) {
        continue;
      }
      String tableName = resolveTableName(tableRef, aliasToTable, body);
      if (tableName == null) {
        continue;
      }
      TableColumnPlan plan = plans.computeIfAbsent(tableName, k -> new TableColumnPlan());
      if (IndexAdvisorConstants.isEqualityOperator(predicate.getOperator())) {
        plan.addEquality(column);
      } else if (IndexAdvisorConstants.isRangeOperator(predicate.getOperator())) {
        if (IndexAdvisorConstants.OPERATOR_LIKE.equalsIgnoreCase(predicate.getOperator())
            && SqlNameParseUtil.isLeadingWildcardLike(predicate.getRightExpression())) {
          continue;
        }
        plan.addRange(column);
      }
    }
    for (JoinClause join : body.getJoins()) {
      String rightTable =
          join.getRightTableName() != null
              ? join.getRightTableName()
              : aliasToTable.get(
                  join.getRightTableAlias() == null
                      ? ""
                      : join.getRightTableAlias().toLowerCase(Locale.ROOT));
      for (String[] eq : SqlNameParseUtil.parseJoinEqualities(join.getOnCondition())) {
        registerJoinColumn(plans, aliasToTable, body, eq[0], eq[1]);
        registerJoinColumn(plans, aliasToTable, body, eq[2], eq[3]);
        if (rightTable != null && SqlNameParseUtil.sameIdentifier(eq[2], join.getRightTableAlias())) {
          plans.computeIfAbsent(rightTable, k -> new TableColumnPlan()).addJoin(eq[3]);
        } else if (rightTable != null && SqlNameParseUtil.sameIdentifier(eq[2], rightTable)) {
          plans.computeIfAbsent(rightTable, k -> new TableColumnPlan()).addJoin(eq[3]);
        }
      }
    }
    for (String groupCol : body.getGroupByColumns()) {
      addSortOrGroupColumn(plans, aliasToTable, body, groupCol, true);
    }
    if (body.getOrderByColumns().size() == body.getOrderByAscFlags().size()) {
      for (int i = 0; i < body.getOrderByColumns().size(); i++) {
        addSortOrGroupColumn(plans, aliasToTable, body, body.getOrderByColumns().get(i), false);
      }
    } else {
      for (String orderCol : body.getOrderByColumns()) {
        addSortOrGroupColumn(plans, aliasToTable, body, orderCol, false);
      }
    }
    for (SelectColumnItem item : body.getSelectColumns()) {
      if (item == null || "*".equals(item.getExpression())) {
        continue;
      }
      String[] parts = SqlNameParseUtil.parseQualifiedColumn(item.getExpression());
      String tableName = resolveTableName(parts[0], aliasToTable, body);
      if (tableName != null && StringUtils.hasText(parts[1])) {
        TableColumnPlan plan = plans.computeIfAbsent(tableName, k -> new TableColumnPlan());
        plan.addSelectColumn(parts[1]);
      } else if (body.getFromTables().size() == 1 && StringUtils.hasText(parts[1])) {
        String only = body.getFromTables().get(0).getTableName();
        plans.computeIfAbsent(only, k -> new TableColumnPlan()).addSelectColumn(parts[1]);
      }
    }
  }

  private static final Pattern OR_BRANCH_EQUALITY =
      Pattern.compile(
          "([`]?[\\w$]+[`]?(?:\\.[`]?[\\w$]+[`]?)?)\\s*=\\s*",
          Pattern.CASE_INSENSITIVE);

  private void extractColumnsFromOrPredicate(
      WherePredicate orPredicate,
      Map<String, String> aliasToTable,
      SqlQueryBody body,
      Map<String, TableColumnPlan> plans) {
    if (!StringUtils.hasText(orPredicate.getPredicateText())) {
      return;
    }
    java.util.regex.Matcher matcher = OR_BRANCH_EQUALITY.matcher(orPredicate.getPredicateText());
    while (matcher.find()) {
      String[] parts = SqlNameParseUtil.parseQualifiedColumn(matcher.group(1));
      String tableName = resolveTableName(parts[0], aliasToTable, body);
      if (tableName != null && StringUtils.hasText(parts[1])) {
        plans.computeIfAbsent(tableName, k -> new TableColumnPlan()).addEquality(parts[1]);
      }
    }
  }

  private void registerJoinColumn(
      Map<String, TableColumnPlan> plans,
      Map<String, String> aliasToTable,
      SqlQueryBody body,
      String tableRef,
      String column) {
    String tableName = resolveTableName(tableRef, aliasToTable, body);
    if (tableName != null && StringUtils.hasText(column)) {
      plans.computeIfAbsent(tableName, k -> new TableColumnPlan()).addJoin(column);
    }
  }

  private void addSortOrGroupColumn(
      Map<String, TableColumnPlan> plans,
      Map<String, String> aliasToTable,
      SqlQueryBody body,
      String expression,
      boolean groupBy) {
    String[] parts = SqlNameParseUtil.parseQualifiedColumn(expression);
    String tableName = resolveTableName(parts[0], aliasToTable, body);
    if (tableName == null || !StringUtils.hasText(parts[1])) {
      return;
    }
    TableColumnPlan plan = plans.computeIfAbsent(tableName, k -> new TableColumnPlan());
    if (groupBy) {
      plan.addGroupBy(parts[1]);
    } else {
      plan.addOrderBy(parts[1]);
    }
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

  private Map<String, TableSchemaMeta> buildMetaMap(List<TableSchemaMeta> tableMetadata) {
    Map<String, TableSchemaMeta> map = new LinkedHashMap<>();
    if (tableMetadata == null) {
      return map;
    }
    for (TableSchemaMeta meta : tableMetadata) {
      if (meta.getTableName() != null) {
        map.put(meta.getTableName().toLowerCase(Locale.ROOT), meta);
      }
    }
    return map;
  }

  private Map<String, String> buildAliasMap(SqlQueryBody body) {
    Map<String, String> map = new LinkedHashMap<>();
    for (TableReference ref : body.getFromTables()) {
      registerTableRef(map, ref);
    }
    for (JoinClause join : body.getJoins()) {
      if (join.getRightTableName() != null) {
        String table = join.getRightTableName();
        if (join.getRightTableAlias() != null) {
          map.put(join.getRightTableAlias().toLowerCase(Locale.ROOT), table);
        }
        map.put(table.toLowerCase(Locale.ROOT), table);
      }
    }
    return map;
  }

  private void registerTableRef(Map<String, String> map, TableReference ref) {
    if (ref.getTableName() == null) {
      return;
    }
    String table = SqlNameParseUtil.stripBackticks(ref.getTableName());
    map.put(table.toLowerCase(Locale.ROOT), table);
    if (ref.getAlias() != null) {
      map.put(ref.getAlias().toLowerCase(Locale.ROOT), table);
    }
  }

  private IndexMeta findReusableIndex(TableSchemaMeta meta, List<String> neededColumns) {
    if (meta == null || meta.getIndexes() == null || neededColumns.isEmpty()) {
      return null;
    }
    IndexMeta best = null;
    int bestMatch = 0;
    for (IndexMeta index : meta.getIndexes()) {
      List<String> cols = indexColumnNames(index);
      int match = 0;
      for (String needed : neededColumns) {
        if (match < cols.size() && cols.get(match).equalsIgnoreCase(needed)) {
          match++;
        } else {
          break;
        }
      }
      if (match == neededColumns.size() && match > bestMatch) {
        best = index;
        bestMatch = match;
      }
    }
    return best;
  }

  private boolean indexCoversPrefix(IndexMeta index, List<String> neededColumns) {
    List<String> cols = indexColumnNames(index);
    if (cols.size() < neededColumns.size()) {
      return false;
    }
    for (int i = 0; i < neededColumns.size(); i++) {
      if (!cols.get(i).equalsIgnoreCase(neededColumns.get(i))) {
        return false;
      }
    }
    return true;
  }

  private String buildCreateIndexDdl(String tableName, List<String> columns) {
    String suffix = String.join("_", columns).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
    String indexName = IndexAdvisorConstants.INDEX_NAME_PREFIX + tableName + "_" + suffix;
    if (indexName.length() > IndexAdvisorConstants.MAX_INDEX_NAME_LENGTH) {
      indexName = indexName.substring(0, IndexAdvisorConstants.MAX_INDEX_NAME_LENGTH);
    }
    String colList =
        columns.stream()
            .map(SqlNameParseUtil::quoteColumnIfNeeded)
            .collect(Collectors.joining(", "));
    StringBuilder ddl = new StringBuilder();
    ddl.append("CREATE INDEX ").append(indexName);
    ddl.append(" ON ").append(SqlNameParseUtil.quoteTableName(tableName));
    ddl.append(" (").append(colList).append(")");
    return ddl.toString();
  }

  private boolean isCoveringIndex(TableColumnPlan plan, List<String> indexColumns) {
    if (plan.getSelectColumns().size() < 2) {
      return false;
    }
    Set<String> indexSet =
        indexColumns.stream()
            .map(c -> c == null ? "" : c.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());
    return plan.getSelectColumns().stream()
        .allMatch(
            col ->
                col != null
                    && indexSet.contains(SqlNameParseUtil.stripBackticks(col).toLowerCase(Locale.ROOT)));
  }

  private String buildReason(
      TableColumnPlan plan, PlanInterpretation interpretation, boolean covering, List<String> indexColumns) {
    List<String> parts = new ArrayList<>();
    if (!plan.getEqualityColumns().isEmpty()) {
      parts.add("等值条件列 " + String.join(", ", plan.getEqualityColumns()));
    }
    if (!plan.getRangeColumns().isEmpty()) {
      parts.add("范围条件列 " + String.join(", ", plan.getRangeColumns()));
    }
    if (!plan.getJoinColumns().isEmpty()) {
      parts.add("JOIN 关联列 " + String.join(", ", plan.getJoinColumns()));
    }
    if (!plan.getOrderByColumns().isEmpty()) {
      parts.add("排序列 " + String.join(", ", plan.getOrderByColumns()));
    }
    if (!plan.getGroupByColumns().isEmpty()) {
      parts.add("分组列 " + String.join(", ", plan.getGroupByColumns()));
    }
    String base = "按等值→范围→排序/分组顺序建议联合索引：" + String.join("；", parts);
    if (covering) {
      base =
          base
              + "。覆盖索引：索引列 ("
              + String.join(", ", indexColumns)
              + ") 包含查询投影全部字段 ("
              + String.join(", ", plan.getSelectColumns())
              + ")";
    }
    if (interpretation != null && interpretation.getSummary() != null) {
      return base + "。执行计划侧：" + interpretation.getSummary();
    }
    return base;
  }

  private String buildExpectedBenefit(TableColumnPlan plan, TableSchemaMeta meta) {
    long rows = meta != null && meta.getEstimatedRows() != null ? meta.getEstimatedRows() : 0L;
    if (rows > 100_000) {
      return "大表（约 " + rows + " 行）上索引命中可显著降低扫描行数";
    }
    if (!plan.getOrderByColumns().isEmpty() || !plan.getGroupByColumns().isEmpty()) {
      return "有助于避免 filesort/临时表，稳定排序与分组性能";
    }
    return "缩小扫描范围，加速点查与范围查询";
  }

  private String buildWriteCostNote(TableSchemaMeta meta, int columnCount) {
    long rows = meta != null && meta.getEstimatedRows() != null ? meta.getEstimatedRows() : 0L;
    String sizeHint = rows > 500_000 ? "较高" : "中等";
    return "每多 "
        + columnCount
        + " 列索引会增加 INSERT/UPDATE/DELETE 维护成本（写入开销预估："
        + sizeHint
        + "）";
  }

  private String buildSummary(IndexAdviceReport report) {
    if (report.getAdvices().isEmpty()) {
      return "未发现可改进的索引建议";
    }
    long createCount =
        report.getAdvices().stream()
            .filter(a -> a.getAdviceType() == IndexAdviceTypeEnum.CREATE_INDEX
                || a.getAdviceType() == IndexAdviceTypeEnum.COVERING_INDEX)
            .count();
    long reuseCount =
        report.getAdvices().stream()
            .filter(a -> a.getAdviceType() == IndexAdviceTypeEnum.REUSE_EXISTING_INDEX)
            .count();
    long redundantCount =
        report.getAdvices().stream()
            .filter(a -> a.getAdviceType() == IndexAdviceTypeEnum.REDUNDANT_INDEX)
            .count();
    long duplicateCount =
        report.getAdvices().stream()
            .filter(a -> a.getAdviceType() == IndexAdviceTypeEnum.DUPLICATE_INDEX)
            .count();
    return "共 "
        + report.getAdvices().size()
        + " 条建议：新建/覆盖 "
        + createCount
        + "，可复用已有 "
        + reuseCount
        + "，冗余 "
        + redundantCount
        + "，重复 "
        + duplicateCount;
  }

  private static final class TableColumnPlan {

    private final LinkedHashSet<String> equalityColumns = new LinkedHashSet<>();

    private final LinkedHashSet<String> rangeColumns = new LinkedHashSet<>();

    private final LinkedHashSet<String> joinColumns = new LinkedHashSet<>();

    private final LinkedHashSet<String> orderByColumns = new LinkedHashSet<>();

    private final LinkedHashSet<String> groupByColumns = new LinkedHashSet<>();

    private final LinkedHashSet<String> selectColumns = new LinkedHashSet<>();

    void addEquality(String column) {
      equalityColumns.add(normalize(column));
    }

    void addRange(String column) {
      rangeColumns.add(normalize(column));
    }

    void addJoin(String column) {
      joinColumns.add(normalize(column));
    }

    void addOrderBy(String column) {
      orderByColumns.add(normalize(column));
    }

    void addGroupBy(String column) {
      groupByColumns.add(normalize(column));
    }

    void addSelectColumn(String column) {
      selectColumns.add(normalize(column));
    }

    boolean isEmpty() {
      return equalityColumns.isEmpty()
          && rangeColumns.isEmpty()
          && joinColumns.isEmpty()
          && orderByColumns.isEmpty()
          && groupByColumns.isEmpty();
    }

    List<String> buildOrderedColumnList() {
      LinkedHashSet<String> ordered = new LinkedHashSet<>();
      ordered.addAll(equalityColumns);
      ordered.addAll(joinColumns);
      ordered.addAll(rangeColumns);
      ordered.addAll(groupByColumns);
      ordered.addAll(orderByColumns);
      return new ArrayList<>(ordered);
    }

    List<String> buildCoveringColumnList() {
      LinkedHashSet<String> ordered = new LinkedHashSet<>(buildOrderedColumnList());
      ordered.addAll(selectColumns);
      return new ArrayList<>(ordered);
    }

    LinkedHashSet<String> getEqualityColumns() {
      return equalityColumns;
    }

    LinkedHashSet<String> getRangeColumns() {
      return rangeColumns;
    }

    LinkedHashSet<String> getJoinColumns() {
      return joinColumns;
    }

    LinkedHashSet<String> getOrderByColumns() {
      return orderByColumns;
    }

    LinkedHashSet<String> getGroupByColumns() {
      return groupByColumns;
    }

    LinkedHashSet<String> getSelectColumns() {
      return selectColumns;
    }

    private String normalize(String column) {
      return SqlNameParseUtil.stripBackticks(column);
    }
  }
}
