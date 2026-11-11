package com.sqlexplan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sqlexplan.constant.ExplainConstants;
import com.sqlexplan.exception.BizException;
import com.sqlexplan.manager.TargetExplainManager;
import com.sqlexplan.model.domain.ExplainPlan;
import com.sqlexplan.model.domain.PlanNode;
import com.sqlexplan.model.enums.PlanNodeKindEnum;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 执行 EXPLAIN 并将 FORMAT=JSON 解析为 {@link PlanNode} 树。
 */
@Slf4j
@Service
public class ExplainService {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final TargetExplainManager targetExplainManager;

  public ExplainService(TargetExplainManager targetExplainManager) {
    this.targetExplainManager = targetExplainManager;
  }

  /**
   * 在目标库执行 EXPLAIN 并解析为结构化计划。
   *
   * @param jdbcTemplate 目标库只读 JdbcTemplate
   * @param selectSql    单条 SELECT
   * @return 结构化执行计划
   */
  public ExplainPlan explain(JdbcTemplate jdbcTemplate, String selectSql) {
    String json = targetExplainManager.fetchExplainJson(jdbcTemplate, selectSql);
    return parseExplainJson(json);
  }

  /**
   * 将 MySQL EXPLAIN FORMAT=JSON 文本解析为计划树（不访问数据库）。
   *
   * @param explainJson JSON 文本
   * @return 结构化执行计划
   */
  public ExplainPlan parseExplainJson(String explainJson) {
    if (!StringUtils.hasText(explainJson)) {
      throw new BizException(ExplainConstants.PARSE_FAILED + ": 内容为空");
    }
    ExplainPlan plan = new ExplainPlan();
    try {
      JsonNode root = OBJECT_MAPPER.readTree(explainJson.trim());
      JsonNode queryBlock = root.path(ExplainConstants.JSON_QUERY_BLOCK);
      if (queryBlock.isMissingNode() || queryBlock.isNull()) {
        if (root.isObject()) {
          queryBlock = root;
        } else {
          throw new BizException(ExplainConstants.PARSE_FAILED + ": 缺少 query_block");
        }
      }
      plan.setRoot(parseQueryBlock(queryBlock, plan.getParseWarnings()));
      if (plan.getRoot() == null) {
        throw new BizException(ExplainConstants.PARSE_FAILED + ": 无法构建计划根节点");
      }
      return plan;
    } catch (BizException ex) {
      throw ex;
    } catch (Exception ex) {
      log.warn("EXPLAIN JSON 解析异常");
      throw new BizException(ExplainConstants.PARSE_FAILED + ": " + ex.getMessage());
    }
  }

  private PlanNode parseQueryBlock(JsonNode block, List<String> warnings) {
    if (block == null || block.isNull()) {
      return null;
    }
    if (block.has(ExplainConstants.JSON_NESTED_LOOP)) {
      PlanNode loop = operationNode(PlanNodeKindEnum.NESTED_LOOP, ExplainConstants.JSON_NESTED_LOOP);
      JsonNode nestedLoop = block.get(ExplainConstants.JSON_NESTED_LOOP);
      if (nestedLoop.isArray()) {
        for (JsonNode childBlock : nestedLoop) {
          PlanNode child = parseQueryBlock(childBlock, warnings);
          if (child != null) {
            loop.getChildren().add(child);
          }
        }
      }
      return loop;
    }
    PlanNode fromOperation = parseOperationWrapper(block, warnings);
    if (fromOperation != null) {
      return fromOperation;
    }
    if (block.has(ExplainConstants.JSON_TABLE)) {
      return parseTableNode(block.get(ExplainConstants.JSON_TABLE), warnings);
    }
    if (block.isObject()) {
      var fields = block.fieldNames();
      if (fields.hasNext()) {
        warnings.add("未识别的 query_block 结构，含字段: " + fields.next());
      }
    }
    return null;
  }

  private PlanNode parseOperationWrapper(JsonNode block, List<String> warnings) {
    for (Map.Entry<String, PlanNodeKindEnum> entry : operationKindMap().entrySet()) {
      if (!block.has(entry.getKey())) {
        continue;
      }
      JsonNode operation = block.get(entry.getKey());
      PlanNode node = operationNode(entry.getValue(), entry.getKey());
      if (operation.path(ExplainConstants.JSON_USING_FILESORT).asBoolean(false)) {
        node.getExtras().add(ExplainConstants.EXTRA_USING_FILESORT);
      }
      if (operation.path(ExplainConstants.JSON_USING_TEMPORARY_TABLE).asBoolean(false)) {
        node.getExtras().add(ExplainConstants.EXTRA_USING_TEMPORARY);
      }
      if (operation.has(ExplainConstants.JSON_TABLE)) {
        node.getChildren().add(parseTableNode(operation.get(ExplainConstants.JSON_TABLE), warnings));
      }
      if (operation.has(ExplainConstants.JSON_QUERY_BLOCK)) {
        PlanNode child = parseQueryBlock(operation.get(ExplainConstants.JSON_QUERY_BLOCK), warnings);
        if (child != null) {
          node.getChildren().add(child);
        }
      }
      if (operation.has(ExplainConstants.JSON_NESTED_LOOP)) {
        PlanNode loop = operationNode(PlanNodeKindEnum.NESTED_LOOP, ExplainConstants.JSON_NESTED_LOOP);
        JsonNode nestedLoop = operation.get(ExplainConstants.JSON_NESTED_LOOP);
        if (nestedLoop.isArray()) {
          for (JsonNode childBlock : nestedLoop) {
            PlanNode child = parseQueryBlock(childBlock, warnings);
            if (child != null) {
              loop.getChildren().add(child);
            }
          }
        }
        node.getChildren().add(loop);
      }
      return node;
    }
    return null;
  }

  private static Map<String, PlanNodeKindEnum> operationKindMap() {
    return Map.of(
        ExplainConstants.JSON_ORDERING_OPERATION, PlanNodeKindEnum.ORDERING,
        ExplainConstants.JSON_GROUPING_OPERATION, PlanNodeKindEnum.GROUPING,
        ExplainConstants.JSON_DUPLICATES_REMOVAL, PlanNodeKindEnum.DUPLICATES_REMOVAL,
        ExplainConstants.JSON_MATERIALIZED_FROM_SUBQUERY, PlanNodeKindEnum.MATERIALIZED);
  }

  private PlanNode operationNode(PlanNodeKindEnum kind, String label) {
    PlanNode node = new PlanNode();
    node.setNodeKind(kind);
    node.setOperationLabel(label);
    return node;
  }

  private PlanNode parseTableNode(JsonNode table, List<String> warnings) {
    if (table == null || table.isNull()) {
      warnings.add("table 节点为空");
      return null;
    }
    PlanNode node = new PlanNode();
    node.setNodeKind(PlanNodeKindEnum.TABLE);
    node.setTableName(textOrNull(table, "table_name"));
    node.setAccessType(textOrNull(table, "access_type"));
    node.setIndexKey(textOrNull(table, "key"));
    node.setPossibleKeys(readStringArray(table.get("possible_keys")));
    node.setRowsExaminedPerScan(readLong(table, "rows_examined_per_scan"));
    node.setRowsProducedPerJoin(readLong(table, "rows_produced_per_join"));
    node.setFilteredPercent(readDouble(table, "filtered"));
    node.setAttachedCondition(textOrNull(table, "attached_condition"));
    return node;
  }

  private static List<String> readStringArray(JsonNode arrayNode) {
    List<String> list = new ArrayList<>();
    if (arrayNode == null || arrayNode.isNull()) {
      return list;
    }
    if (arrayNode.isArray()) {
      for (JsonNode item : arrayNode) {
        if (item != null && !item.isNull()) {
          list.add(item.asText());
        }
      }
    }
    return list;
  }

  private static String textOrNull(JsonNode node, String field) {
    JsonNode child = node.get(field);
    if (child == null || child.isNull() || !child.isTextual()) {
      if (child != null && child.isNumber()) {
        return child.asText();
      }
      return null;
    }
    String text = child.asText();
    if (!StringUtils.hasText(text) || "null".equalsIgnoreCase(text)) {
      return null;
    }
    return text;
  }

  private static Long readLong(JsonNode node, String field) {
    JsonNode child = node.get(field);
    if (child == null || child.isNull()) {
      return null;
    }
    if (child.isNumber()) {
      return child.longValue();
    }
    try {
      return Long.parseLong(child.asText());
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static Double readDouble(JsonNode node, String field) {
    JsonNode child = node.get(field);
    if (child == null || child.isNull()) {
      return null;
    }
    if (child.isNumber()) {
      return child.doubleValue();
    }
    try {
      return Double.parseDouble(child.asText());
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  /**
   * 深度优先收集所有表访问节点。
   *
   * @param root 计划根
   * @return 表节点列表
   */
  public List<PlanNode> collectTableNodes(PlanNode root) {
    List<PlanNode> tables = new ArrayList<>();
    if (root == null) {
      return tables;
    }
    collectTableNodesRecursive(root, tables);
    return tables;
  }

  private void collectTableNodesRecursive(PlanNode node, List<PlanNode> tables) {
    if (node.getNodeKind() == PlanNodeKindEnum.TABLE) {
      tables.add(node);
    }
    for (PlanNode child : node.getChildren()) {
      collectTableNodesRecursive(child, tables);
    }
  }
}
