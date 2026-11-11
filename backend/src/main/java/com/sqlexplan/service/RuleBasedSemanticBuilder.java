package com.sqlexplan.service;

import com.sqlexplan.config.SqlexplanProperties;
import com.sqlexplan.model.domain.AggregateFunctionItem;
import com.sqlexplan.model.domain.CteClause;
import com.sqlexplan.model.domain.JoinClause;
import com.sqlexplan.model.domain.SqlQueryBody;
import com.sqlexplan.model.domain.SqlSemanticInsight;
import com.sqlexplan.model.domain.SqlStructure;
import com.sqlexplan.model.domain.SubqueryClause;
import com.sqlexplan.model.domain.TableReference;
import com.sqlexplan.model.domain.WherePredicate;
import com.sqlexplan.model.enums.JoinTypeEnum;
import com.sqlexplan.model.enums.SqlSetOperationEnum;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * 在 LLM 关闭或不可用时，基于 {@link SqlStructure} 用模板生成中文语义分析。
 */
@Component
public class RuleBasedSemanticBuilder {

  private static final int MAX_PREDICATE_SAMPLES = 8;

  private static final int MAX_SUBQUERY_SAMPLES = 6;

  private final SqlexplanProperties sqlexplanProperties;

  public RuleBasedSemanticBuilder(SqlexplanProperties sqlexplanProperties) {
    this.sqlexplanProperties = sqlexplanProperties;
  }

  /**
   * 根据 SQL 结构生成规则语义分析。
   *
   * @param sqlStructure 解析结构
   * @return 语义分析
   */
  public SqlSemanticInsight build(SqlStructure sqlStructure) {
    SqlSemanticInsight insight = new SqlSemanticInsight();
    if (sqlStructure == null) {
      insight.setIntentSummary("无法解析 SQL 结构");
      return insight;
    }
    if (sqlStructure.getSetOperation() != null
        && !CollectionUtils.isEmpty(sqlStructure.getUnionBranches())) {
      buildUnionInsight(insight, sqlStructure);
      return insight;
    }
    SqlQueryBody body = sqlStructure.getBody();
    if (body == null) {
      insight.setIntentSummary("无法解析查询主体");
      return insight;
    }
    insight.setIntentSummary(buildIntentSummary(sqlStructure, body));
    insight.setInvolvedTables(collectInvolvedTables(sqlStructure, body));
    insight.setFiltersDescription(describeFilters(body));
    insight.setJoinsDescription(describeJoins(body));
    insight.setAggregationDescription(describeAggregation(body));
    insight.setOrderingDescription(describeOrdering(body));
    insight.setPaginationDescription(describePagination(body));
    insight.setSubqueryCteDescription(describeSubqueryAndCte(sqlStructure, body));
    insight.setRiskNotes(collectRisks(sqlStructure, body));
    return insight;
  }

  private void buildUnionInsight(SqlSemanticInsight insight, SqlStructure sqlStructure) {
    SqlSetOperationEnum op = sqlStructure.getSetOperation();
    int branchCount = sqlStructure.getUnionBranches().size();
    String opLabel = op == null ? "集合运算" : op.name().replace('_', ' ');
    insight.setIntentSummary(
        String.format(Locale.ROOT, "将 %d 条 SELECT 分支以 %s 合并为单一结果集", branchCount, opLabel));
    Set<String> tables = new LinkedHashSet<>();
    List<String> branchNotes = new ArrayList<>();
    int index = 1;
    for (SqlStructure branch : sqlStructure.getUnionBranches()) {
      if (branch != null && !CollectionUtils.isEmpty(branch.getAllTables())) {
        for (TableReference ref : branch.getAllTables()) {
          String label = tableLabel(ref);
          if (StringUtils.hasText(label)) {
            tables.add(label);
          }
        }
      }
      branchNotes.add("分支 " + index + "：" + summarizeBranch(branch));
      index++;
    }
    insight.setInvolvedTables(new ArrayList<>(tables));
    insight.setFiltersDescription("各 UNION 分支拥有独立 WHERE，请在分支内分别查看过滤条件");
    insight.setJoinsDescription("各分支 JOIN 结构独立，见下方子查询/结构说明");
    insight.setAggregationDescription("各分支可含独立 GROUP BY 与聚合");
    insight.setOrderingDescription("UNION 外层排序以最终 SELECT 为准（若存在）");
    if (sqlStructure.getBody() != null) {
      insight.setPaginationDescription(describePagination(sqlStructure.getBody()));
      insight.setOrderingDescription(describeOrdering(sqlStructure.getBody()));
    } else {
      insight.setPaginationDescription("未检测到顶层 LIMIT/OFFSET");
    }
    List<String> cteParts = new ArrayList<>();
    if (!CollectionUtils.isEmpty(sqlStructure.getCtes())) {
      cteParts.add(describeCtes(sqlStructure.getCtes()));
    }
    cteParts.add("UNION 分支概要：" + String.join("；", branchNotes));
    insight.setSubqueryCteDescription(String.join("\n", cteParts));
    insight.setRiskNotes(collectUnionRisks(sqlStructure));
  }

  private static String summarizeBranch(SqlStructure branch) {
    if (branch == null || branch.getBody() == null) {
      return "结构未解析";
    }
    SqlQueryBody body = branch.getBody();
    int tableCount = body.getFromTables().size() + body.getJoins().size();
    int filterCount = body.getWherePredicates() == null ? 0 : body.getWherePredicates().size();
    return String.format(Locale.ROOT, "约 %d 张表，%d 个 WHERE 谓词", tableCount, filterCount);
  }

  private String buildIntentSummary(SqlStructure structure, SqlQueryBody body) {
    boolean hasAgg = !CollectionUtils.isEmpty(body.getAggregateFunctions())
        || !CollectionUtils.isEmpty(body.getGroupByColumns());
    boolean hasJoin = !CollectionUtils.isEmpty(body.getJoins());
    boolean hasCte = !CollectionUtils.isEmpty(structure.getCtes());
    boolean hasSub = !CollectionUtils.isEmpty(body.getSubqueries());
    List<String> parts = new ArrayList<>(4);
    if (hasCte) {
      parts.add("先通过 CTE 准备中间结果");
    }
    if (hasJoin) {
      parts.add("多表关联查询");
    } else {
      parts.add("单表或简单查询");
    }
    if (hasAgg) {
      parts.add("含分组或聚合统计");
    } else if (body.getSelectColumns() != null && body.getSelectColumns().size() == 1) {
      parts.add("返回少量列");
    } else {
      parts.add("返回明细或投影列");
    }
    if (hasSub) {
      parts.add("依赖子查询");
    }
    return "本查询为：" + String.join("，", parts) + "（本地规则解读）";
  }

  private static List<String> collectInvolvedTables(SqlStructure structure, SqlQueryBody body) {
    Set<String> ordered = new LinkedHashSet<>();
    if (!CollectionUtils.isEmpty(structure.getAllTables())) {
      for (TableReference ref : structure.getAllTables()) {
        String label = tableLabel(ref);
        if (StringUtils.hasText(label)) {
          ordered.add(label);
        }
      }
    }
    if (ordered.isEmpty() && !CollectionUtils.isEmpty(body.getFromTables())) {
      for (TableReference ref : body.getFromTables()) {
        String label = tableLabel(ref);
        if (StringUtils.hasText(label)) {
          ordered.add(label);
        }
      }
      for (JoinClause join : body.getJoins()) {
        if (join == null) {
          continue;
        }
        String name = join.getRightTableName();
        if (!StringUtils.hasText(name)) {
          continue;
        }
        if (StringUtils.hasText(join.getRightTableAlias())) {
          ordered.add(name + " AS " + join.getRightTableAlias());
        } else {
          ordered.add(name);
        }
      }
    }
    return new ArrayList<>(ordered);
  }

  private static String describeFilters(SqlQueryBody body) {
    if (CollectionUtils.isEmpty(body.getWherePredicates())) {
      return "无 WHERE 过滤条件（可能全表扫描，需结合执行计划判断）";
    }
    List<String> samples = body.getWherePredicates().stream()
        .limit(MAX_PREDICATE_SAMPLES)
        .map(RuleBasedSemanticBuilder::predicateText)
        .collect(Collectors.toList());
    int total = body.getWherePredicates().size();
    String joined = String.join("；", samples);
    if (total > MAX_PREDICATE_SAMPLES) {
      return String.format(Locale.ROOT, "共 %d 个谓词，示例：%s …", total, joined);
    }
    return String.format(Locale.ROOT, "共 %d 个谓词：%s", total, joined);
  }

  private static String predicateText(WherePredicate p) {
    if (p == null) {
      return "";
    }
    if (StringUtils.hasText(p.getPredicateText())) {
      return p.getPredicateText().trim();
    }
    String left = p.getLeftExpression() == null ? "" : p.getLeftExpression().trim();
    String op = p.getOperator() == null ? "" : p.getOperator().trim();
    String right = p.getRightExpression() == null ? "" : p.getRightExpression().trim();
    return (left + " " + op + " " + right).trim();
  }

  private static String describeJoins(SqlQueryBody body) {
    if (CollectionUtils.isEmpty(body.getJoins())) {
      if (CollectionUtils.isEmpty(body.getFromTables())) {
        return "未识别 FROM 表";
      }
      if (body.getFromTables().size() == 1) {
        return "无 JOIN，仅访问主表 " + tableLabel(body.getFromTables().get(0));
      }
      return "无显式 JOIN，FROM 含 " + body.getFromTables().size() + " 个表引用";
    }
    List<String> lines = new ArrayList<>(body.getJoins().size());
    for (JoinClause join : body.getJoins()) {
      if (join == null) {
        continue;
      }
      String type = join.getJoinType() == null ? "JOIN" : join.getJoinType().name();
      if (join.getJoinType() == JoinTypeEnum.INNER) {
        type = "INNER JOIN";
      }
      String right = join.getRightTableName();
      if (StringUtils.hasText(join.getRightTableAlias())) {
        right = right + " AS " + join.getRightTableAlias();
      }
      String on = StringUtils.hasText(join.getOnCondition()) ? join.getOnCondition().trim() : "（无 ON 条件）";
      lines.add(String.format(Locale.ROOT, "%s %s ON %s", type, right, on));
    }
    return String.join("\n", lines);
  }

  private static String describeAggregation(SqlQueryBody body) {
    boolean hasGroup = !CollectionUtils.isEmpty(body.getGroupByColumns());
    boolean hasAgg = !CollectionUtils.isEmpty(body.getAggregateFunctions());
    if (!hasGroup && !hasAgg) {
      return "无 GROUP BY 与聚合函数";
    }
    StringBuilder sb = new StringBuilder();
    if (hasGroup) {
      sb.append("GROUP BY ").append(String.join(", ", body.getGroupByColumns()));
    }
    if (hasAgg) {
      if (sb.length() > 0) {
        sb.append("；");
      }
      List<String> aggs = new ArrayList<>();
      for (AggregateFunctionItem item : body.getAggregateFunctions()) {
        if (item == null) {
          continue;
        }
        String fn = item.getFunctionName() == null ? "AGG" : item.getFunctionName();
        String args = item.getArgumentsText() == null ? "" : item.getArgumentsText();
        String line = fn + "(" + args + ")";
        if (StringUtils.hasText(item.getAlias())) {
          line = line + " AS " + item.getAlias();
        }
        aggs.add(line);
      }
      sb.append("聚合：").append(String.join(", ", aggs));
    }
    return sb.toString();
  }

  private static String describeOrdering(SqlQueryBody body) {
    if (CollectionUtils.isEmpty(body.getOrderByColumns())) {
      return "无 ORDER BY";
    }
    List<String> cols = body.getOrderByColumns();
    List<Boolean> asc = body.getOrderByAscFlags();
    List<String> parts = new ArrayList<>(cols.size());
    for (int i = 0; i < cols.size(); i++) {
      String dir = "ASC";
      if (asc != null && i < asc.size() && Boolean.FALSE.equals(asc.get(i))) {
        dir = "DESC";
      }
      parts.add(cols.get(i) + " " + dir);
    }
    return "ORDER BY " + String.join(", ", parts);
  }

  private String describePagination(SqlQueryBody body) {
    Long limit = body.getLimit();
    Long offset = body.getOffset();
    if (limit == null && offset == null) {
      return "无分页（未设置 LIMIT/OFFSET）";
    }
    if (limit != null && offset != null) {
      return String.format(Locale.ROOT, "分页：LIMIT %d OFFSET %d", limit, offset);
    }
    if (limit != null) {
      return String.format(Locale.ROOT, "限制返回行数 LIMIT %d", limit);
    }
    return String.format(Locale.ROOT, "仅 OFFSET %d（未设 LIMIT，可能返回大量行）", offset);
  }

  private static String describeSubqueryAndCte(SqlStructure structure, SqlQueryBody body) {
    List<String> parts = new ArrayList<>();
    if (!CollectionUtils.isEmpty(structure.getCtes())) {
      parts.add(describeCtes(structure.getCtes()));
    }
    if (!CollectionUtils.isEmpty(body.getSubqueries())) {
      parts.add(describeSubqueries(body.getSubqueries()));
    }
    if (parts.isEmpty()) {
      return "无 CTE 与子查询";
    }
    return String.join("\n", parts);
  }

  private static String describeCtes(List<CteClause> ctes) {
    List<String> lines = new ArrayList<>(ctes.size());
    for (CteClause cte : ctes) {
      if (cte == null) {
        continue;
      }
      String name = StringUtils.hasText(cte.getName()) ? cte.getName() : "cte";
      String inner = "（内部结构已解析）";
      if (cte.getBody() != null && !CollectionUtils.isEmpty(cte.getBody().getAllTables())) {
        List<String> tables = cte.getBody().getAllTables().stream()
            .map(RuleBasedSemanticBuilder::tableLabel)
            .filter(StringUtils::hasText)
            .collect(Collectors.toList());
        if (!tables.isEmpty()) {
          inner = "引用表：" + String.join(", ", tables);
        }
      }
      lines.add("WITH " + name + "：" + inner);
    }
    return "CTE：" + String.join("；", lines);
  }

  private static String describeSubqueries(List<SubqueryClause> subqueries) {
    List<String> samples = new ArrayList<>();
    int count = 0;
    for (SubqueryClause sub : subqueries) {
      if (sub == null) {
        continue;
      }
      count++;
      if (samples.size() >= MAX_SUBQUERY_SAMPLES) {
        continue;
      }
      String loc = StringUtils.hasText(sub.getLocation()) ? sub.getLocation() : "未知位置";
      String alias = StringUtils.hasText(sub.getAlias()) ? " 别名 " + sub.getAlias() : "";
      samples.add(loc + alias);
    }
    if (count > MAX_SUBQUERY_SAMPLES) {
      return String.format(Locale.ROOT, "子查询共 %d 处，示例位置：%s …", count, String.join(", ", samples));
    }
    return String.format(Locale.ROOT, "子查询共 %d 处：%s", count, String.join(", ", samples));
  }

  private List<String> collectRisks(SqlStructure structure, SqlQueryBody body) {
    List<String> risks = new ArrayList<>();
    long deepOffset = sqlexplanProperties.getExplain().getDeepPaginationOffsetThreshold();
    Long offset = body.getOffset();
    if (offset != null && offset >= deepOffset) {
      risks.add(String.format(Locale.ROOT,
          "深分页：OFFSET=%d 超过阈值 %d，丢弃前序行代价高", offset, deepOffset));
    }
    if (offset != null && offset > 0 && body.getLimit() == null) {
      risks.add("仅 OFFSET 无 LIMIT，可能拉取大量结果");
    }
    if (CollectionUtils.isEmpty(body.getWherePredicates())
        && CollectionUtils.isEmpty(body.getJoins())
        && body.getFromTables() != null
        && body.getFromTables().size() == 1) {
      risks.add("单表且无 WHERE，易出现全表扫描");
    }
    if (!CollectionUtils.isEmpty(body.getWherePredicates())) {
      for (WherePredicate p : body.getWherePredicates()) {
        if (p == null) {
          continue;
        }
        if (Boolean.TRUE.equals(p.getColumnWrappedByFunction())) {
          risks.add("WHERE 中列被函数包裹，可能无法走索引：" + predicateText(p));
        }
        String text = predicateText(p).toUpperCase(Locale.ROOT);
        if (text.contains("LIKE") && text.contains("%")) {
          if (text.indexOf("LIKE '%") >= 0 || text.contains("LIKE \"%")) {
            risks.add("前缀通配 LIKE 通常无法利用 B-Tree 索引");
            break;
          }
        }
      }
    }
    if (!CollectionUtils.isEmpty(body.getSubqueries())) {
      risks.add("含子查询，优化器可能物化临时表或重复扫描");
    }
    if (!CollectionUtils.isEmpty(structure.getCtes())) {
      risks.add("含 CTE，MySQL 可能物化中间结果，请关注执行计划中的 MATERIALIZED");
    }
    if (!CollectionUtils.isEmpty(body.getJoins()) && body.getJoins().size() >= 3) {
      risks.add("多表 JOIN（≥3），关联顺序与索引对性能影响大");
    }
    return risks;
  }

  private List<String> collectUnionRisks(SqlStructure structure) {
    List<String> risks = new ArrayList<>();
    risks.add("UNION 各分支需分别优化，排序/去重可能产生额外开销");
    if (structure.getSetOperation() == SqlSetOperationEnum.UNION) {
      risks.add("UNION（非 ALL）需去重，可能使用临时表");
    }
    if (structure.getBody() != null) {
      risks.addAll(collectRisks(structure, structure.getBody()));
    }
    return risks;
  }

  private static String tableLabel(TableReference ref) {
    if (ref == null) {
      return "";
    }
    if (StringUtils.hasText(ref.getAlias())) {
      return ref.getTableName() + " AS " + ref.getAlias();
    }
    return ref.getTableName() == null ? "" : ref.getTableName();
  }
}
