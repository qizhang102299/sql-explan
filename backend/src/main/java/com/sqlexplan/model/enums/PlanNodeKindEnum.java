package com.sqlexplan.model.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 执行计划节点类型（表访问或运算包装）。
 */
@Schema(description = "执行计划节点类型")
public enum PlanNodeKindEnum {

  @Schema(description = "表访问")
  TABLE,

  @Schema(description = "嵌套循环连接")
  NESTED_LOOP,

  @Schema(description = "排序运算")
  ORDERING,

  @Schema(description = "分组运算")
  GROUPING,

  @Schema(description = "去重运算")
  DUPLICATES_REMOVAL,

  @Schema(description = "子查询物化")
  MATERIALIZED,

  @Schema(description = "其他运算块")
  OPERATION
}
