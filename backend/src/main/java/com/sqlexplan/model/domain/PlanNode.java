package com.sqlexplan.model.domain;

import com.sqlexplan.model.enums.PlanNodeKindEnum;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * MySQL EXPLAIN FORMAT=JSON 结构化计划节点（树形）。
 */
@Data
public class PlanNode {

  private PlanNodeKindEnum nodeKind = PlanNodeKindEnum.TABLE;

  /**
   * 运算块标签（如 nested_loop、ordering_operation）。
   */
  private String operationLabel;

  private String tableName;

  private String accessType;

  private List<String> possibleKeys = new ArrayList<>();

  private String indexKey;

  private Long rowsExaminedPerScan;

  private Long rowsProducedPerJoin;

  private Double filteredPercent;

  private List<String> extras = new ArrayList<>();

  private String attachedCondition;

  private List<PlanNode> children = new ArrayList<>();
}
