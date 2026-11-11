package com.sqlexplan.service;

import com.sqlexplan.constant.ExplainConstants;
import com.sqlexplan.model.domain.ExplainPlan;
import com.sqlexplan.model.domain.PlanInterpretation;
import com.sqlexplan.model.domain.PlanIssue;
import com.sqlexplan.model.domain.PlanNode;
import com.sqlexplan.model.enums.PlanIssueSeverityEnum;
import com.sqlexplan.model.enums.PlanIssueTypeEnum;
import com.sqlexplan.model.enums.PlanNodeKindEnum;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * 基于 {@link PlanInterpretation} 与 {@link ExplainPlan} 生成执行计划白话中文解释。
 */
@Component
public class PlanExplanationBuilder {

  private static final int INDENT_STEP = 2;

  private final ExplainService explainService;

  public PlanExplanationBuilder(ExplainService explainService) {
    this.explainService = explainService;
  }

  /**
   * 生成执行计划白话说明（含节点解读与整体结论）。
   *
   * @param explainPlan          结构化计划
   * @param planInterpretation   规则解读（可为 null）
   * @return 多段中文说明
   */
  public String build(ExplainPlan explainPlan, PlanInterpretation planInterpretation) {
    if (explainPlan == null || explainPlan.getRoot() == null) {
      return "无有效执行计划，无法解读。";
    }
    List<String> sections = new ArrayList<>();
    sections.add("【节点逐步解读】");
    appendNodeNarrative(explainPlan.getRoot(), 0, sections);
    sections.add("");
    sections.add("【整体结论】");
    sections.add(buildConclusion(planInterpretation, explainPlan));
    return String.join("\n", sections);
  }

  private void appendNodeNarrative(PlanNode node, int depth, List<String> sections) {
    if (node == null) {
      return;
    }
    String indent = " ".repeat(depth * INDENT_STEP);
    if (node.getNodeKind() == PlanNodeKindEnum.TABLE) {
      sections.add(indent + formatTableNode(node));
    } else {
      sections.add(indent + formatOperationNode(node));
    }
    for (PlanNode child : node.getChildren()) {
      appendNodeNarrative(child, depth + 1, sections);
    }
  }

  private String formatTableNode(PlanNode table) {
    String name = StringUtils.hasText(table.getTableName()) ? table.getTableName() : "未知表";
    String access = StringUtils.hasText(table.getAccessType()) ? table.getAccessType() : "未知";
    String accessDesc = describeAccessType(access);
    String why = whySlowForTable(table, access);
    String fix = howToImproveTable(table, access);
    StringBuilder sb = new StringBuilder();
    sb.append(String.format(Locale.ROOT, "表 %s：访问方式 %s（%s）", name, access, accessDesc));
    if (table.getRowsExaminedPerScan() != null) {
      sb.append(String.format(Locale.ROOT, "；预估单次扫描约 %d 行", table.getRowsExaminedPerScan()));
    }
    if (StringUtils.hasText(table.getIndexKey())) {
      sb.append("；使用索引 ").append(table.getIndexKey());
    } else if (!table.getPossibleKeys().isEmpty()) {
      sb.append("；候选索引 ").append(String.join(",", table.getPossibleKeys())).append(" 但未选用");
    }
    if (!CollectionUtils.isEmpty(table.getExtras())) {
      sb.append("；附加信息：").append(String.join(",", table.getExtras()));
    }
    if (StringUtils.hasText(table.getAttachedCondition())) {
      sb.append("；条件：").append(table.getAttachedCondition());
    }
    sb.append("\n").append("  为何可能慢：").append(why);
    sb.append("\n").append("  改进建议：").append(fix);
    return sb.toString();
  }

  private String formatOperationNode(PlanNode node) {
    String op = StringUtils.hasText(node.getOperationLabel())
        ? node.getOperationLabel()
        : node.getNodeKind().name();
    String kindDesc = switch (node.getNodeKind()) {
      case NESTED_LOOP -> "嵌套循环连接：外表驱动内表，内表访问次数取决于外表行数";
      case ORDERING -> "排序运算";
      case GROUPING -> "分组运算";
      case DUPLICATES_REMOVAL -> "去重运算";
      case MATERIALIZED -> "子查询物化";
      default -> "运算步骤";
    };
    String why = whySlowForOperation(node);
    String fix = howToImproveOperation(node);
    StringBuilder sb = new StringBuilder();
    sb.append(String.format(Locale.ROOT, "运算 %s（%s）", op, kindDesc));
    if (!CollectionUtils.isEmpty(node.getExtras())) {
      sb.append("：").append(String.join(",", node.getExtras()));
    }
    sb.append("\n").append("  为何可能慢：").append(why);
    sb.append("\n").append("  改进建议：").append(fix);
    return sb.toString();
  }

  private static String describeAccessType(String access) {
    return switch (access.toUpperCase(Locale.ROOT)) {
      case "ALL" -> "全表扫描，逐行读取";
      case "INDEX" -> "全索引扫描（遍历索引叶子）";
      case "RANGE" -> "索引范围扫描";
      case "REF" -> "非唯一索引等值/前缀查找";
      case "EQ_REF" -> "唯一索引等值查找，每次最多一行";
      case "CONST" -> "主键/唯一索引常量定位，至多一行";
      case "SYSTEM" -> "系统表，仅一行";
      default -> "MySQL 优化器选择的访问路径";
    };
  }

  private static String whySlowForTable(PlanNode table, String access) {
    if (ExplainConstants.JSON_ACCESS_TYPE_ALL.equalsIgnoreCase(access)) {
      return "全表扫描随数据量线性变慢，过滤性差时尤为明显";
    }
    Long rows = table.getRowsExaminedPerScan();
    if (rows != null && rows >= ExplainConstants.DEFAULT_LARGE_SCAN_ROWS_THRESHOLD) {
      return "单次扫描行数很大，I/O 与 CPU 过滤成本高";
    }
    if (!table.getPossibleKeys().isEmpty() && !StringUtils.hasText(table.getIndexKey())) {
      return "存在可用索引但未命中，条件可能未满足最左前缀或存在隐式转换";
    }
    if (hasExtra(table, "filesort")) {
      return "需额外排序，无法直接按索引顺序输出";
    }
    if (hasExtra(table, "temporary")) {
      return "需创建临时表，增加内存/磁盘开销";
    }
    return "当前访问路径在数据量增长时可能成为瓶颈，请结合扫描行数评估";
  }

  private static String howToImproveTable(PlanNode table, String access) {
    if (ExplainConstants.JSON_ACCESS_TYPE_ALL.equalsIgnoreCase(access)) {
      String cond = table.getAttachedCondition();
      if (StringUtils.hasText(cond)) {
        return "为 WHERE/JOIN 中的等值与范围列建立合适索引，或改写条件避免对列施加函数";
      }
      return "补充有针对性的 WHERE 限制，或建立覆盖查询条件的联合索引";
    }
    if (!table.getPossibleKeys().isEmpty() && !StringUtils.hasText(table.getIndexKey())) {
      return "调整谓词写法以命中 possible_keys，或重建/补齐联合索引最左列";
    }
    if (hasExtra(table, "filesort")) {
      return "让 ORDER BY 列与索引顺序一致，或减少参与排序的行数";
    }
    if (hasExtra(table, "temporary")) {
      return "减少 DISTINCT/GROUP BY 宽度，确保分组列有索引或改写为流式聚合";
    }
    if (rowsLarge(table)) {
      return "缩小过滤范围、增加选择性更高的条件，或拆分查询";
    }
    return "保持统计信息新鲜，确认索引与查询谓词一致";
  }

  private static boolean rowsLarge(PlanNode table) {
    Long rows = table.getRowsExaminedPerScan();
    return rows != null && rows >= ExplainConstants.DEFAULT_LARGE_SCAN_ROWS_THRESHOLD;
  }

  private static String whySlowForOperation(PlanNode node) {
    if (node.getNodeKind() == PlanNodeKindEnum.NESTED_LOOP) {
      return "内表被反复探测，内表若未走索引将放大为嵌套全表扫描";
    }
    if (hasExtra(node, "filesort") || node.getNodeKind() == PlanNodeKindEnum.ORDERING) {
      return "排序数据量大时需要 filesort，占用内存并可能落盘";
    }
    if (hasExtra(node, "temporary")
        || node.getNodeKind() == PlanNodeKindEnum.GROUPING
        || node.getNodeKind() == PlanNodeKindEnum.MATERIALIZED) {
      return "需要临时空间存放中间结果，数据量大时 I/O 显著";
    }
    if (node.getNodeKind() == PlanNodeKindEnum.DUPLICATES_REMOVAL) {
      return "去重常伴随排序或哈希临时结构";
    }
    return "该步骤增加额外 CPU/内存开销";
  }

  private static String howToImproveOperation(PlanNode node) {
    if (node.getNodeKind() == PlanNodeKindEnum.NESTED_LOOP) {
      return "保证 JOIN 列两侧均有索引，让小表驱动大表，必要时改用 STRAIGHT_JOIN 做验证";
    }
    if (hasExtra(node, "filesort") || node.getNodeKind() == PlanNodeKindEnum.ORDERING) {
      return "建立与 ORDER BY 一致的索引，或延迟排序（先过滤再排序）";
    }
    if (node.getNodeKind() == PlanNodeKindEnum.MATERIALIZED) {
      return "将相关子查询改写为 JOIN，或为子查询结果加索引友好的过滤";
    }
    if (hasExtra(node, "temporary") || node.getNodeKind() == PlanNodeKindEnum.GROUPING) {
      return "为 GROUP BY 列建索引，减少 SELECT 列宽，避免无谓 DISTINCT";
    }
    return "结合上层表访问方式一并优化，避免运算步骤处理过大的中间集";
  }

  private String buildConclusion(PlanInterpretation interpretation, ExplainPlan explainPlan) {
    List<PlanIssue> issues = interpretation != null ? interpretation.getIssues() : List.of();
    if (issues.isEmpty()) {
      List<PlanNode> tables = explainService.collectTableNodes(explainPlan.getRoot());
      boolean anyAll = tables.stream()
          .anyMatch(t -> ExplainConstants.JSON_ACCESS_TYPE_ALL.equalsIgnoreCase(t.getAccessType()));
      if (anyAll) {
        return "规则未命中告警项，但存在全表扫描访问类型，仍建议关注数据量与索引";
      }
      return "未发现明显性能风险点；随数据增长请继续关注扫描行数与索引命中";
    }
    long critical = issues.stream().filter(i -> i.getSeverity() == PlanIssueSeverityEnum.CRITICAL).count();
    long warning = issues.stream().filter(i -> i.getSeverity() == PlanIssueSeverityEnum.WARNING).count();
    String titles = issues.stream()
        .map(PlanIssue::getTitle)
        .filter(StringUtils::hasText)
        .distinct()
        .limit(5)
        .collect(Collectors.joining("、"));
    String summary = interpretation != null && StringUtils.hasText(interpretation.getSummary())
        ? interpretation.getSummary()
        : String.format(Locale.ROOT, "共 %d 项问题（严重 %d，警告 %d）", issues.size(), critical, warning);
    if (StringUtils.hasText(titles)) {
      return summary + "。重点关注：" + titles + "。" + topIssueAdvice(issues);
    }
    return summary + "。" + topIssueAdvice(issues);
  }

  private static String topIssueAdvice(List<PlanIssue> issues) {
    for (PlanIssue issue : issues) {
      if (issue.getIssueType() == PlanIssueTypeEnum.FULL_TABLE_SCAN) {
        return " 优先为过滤列补索引或收紧 WHERE。";
      }
      if (issue.getIssueType() == PlanIssueTypeEnum.DEEP_PAGINATION) {
        return " 深分页建议改为延迟关联或键集翻页。";
      }
      if (issue.getIssueType() == PlanIssueTypeEnum.FILESORT) {
        return " 对齐 ORDER BY 与索引，或先缩小结果集再排序。";
      }
    }
    return " 请结合上文节点建议逐项调整。";
  }

  private static boolean hasExtra(PlanNode node, String keyword) {
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
}
