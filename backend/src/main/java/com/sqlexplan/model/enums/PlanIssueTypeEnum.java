package com.sqlexplan.model.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 执行计划与 SQL 规则问题类型。
 */
@Schema(description = "执行计划问题类型")
public enum PlanIssueTypeEnum {

  @Schema(description = "全表扫描")
  FULL_TABLE_SCAN,

  @Schema(description = "存在可用索引但未使用")
  INDEX_NOT_USED,

  @Schema(description = "文件排序 filesort")
  FILESORT,

  @Schema(description = "使用临时表")
  TEMPORARY_TABLE,

  @Schema(description = "单次扫描行数过大")
  LARGE_ROWS_EXAMINED,

  @Schema(description = "疑似隐式类型转换")
  IMPLICIT_TYPE_CONVERSION,

  @Schema(description = "索引列被函数包裹")
  INDEX_COLUMN_FUNCTION,

  @Schema(description = "深分页")
  DEEP_PAGINATION,

  @Schema(description = "LIKE 前导通配无法走 B-Tree")
  LIKE_LEADING_WILDCARD,

  @Schema(description = "NOT IN / != / <> 等不利于索引的范围扫描")
  NON_SARGABLE_OPERATOR,

  @Schema(description = "LIMIT 无 ORDER BY，结果顺序不确定")
  LIMIT_WITHOUT_ORDER_BY,

  @Schema(description = "大表 COUNT(*) 全表计数")
  COUNT_STAR_LARGE_TABLE,

  @Schema(description = "JOIN 两侧列类型或字符集/排序规则不一致")
  JOIN_COLLATION_MISMATCH,

  @Schema(description = "关联子查询易导致逐行执行")
  CORRELATED_SUBQUERY,

  @Schema(description = "索引列参与表达式，无法直接走索引")
  INDEX_COLUMN_EXPRESSION
}
