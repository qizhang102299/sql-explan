package com.sqlexplan.model.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * SQL 规则改写类型。
 */
@Schema(description = "SQL 规则改写类型")
public enum SqlRewriteRuleTypeEnum {

  @Schema(description = "将 SELECT * 精简为明确列")
  SELECT_STAR_NARROW,

  @Schema(description = "OR 条件改写为 UNION ALL")
  OR_TO_UNION_ALL,

  @Schema(description = "IN 子查询改写为 EXISTS")
  IN_SUBQUERY_TO_EXISTS,

  @Schema(description = "深分页改为延迟关联")
  DEEP_PAGINATION_DEFERRED_JOIN,

  @Schema(description = "去除不必要的 DISTINCT")
  REMOVE_REDUNDANT_DISTINCT,

  @Schema(description = "去除不必要的 ORDER BY")
  REMOVE_REDUNDANT_ORDER_BY,

  @Schema(description = "LIKE '%x' 改写或全文索引方案")
  LIKE_LEADING_WILDCARD,

  @Schema(description = "NOT IN 改写为 NOT EXISTS")
  NOT_IN_TO_NOT_EXISTS,

  @Schema(description = "IN 子查询改写为 INNER JOIN")
  IN_SUBQUERY_TO_JOIN,

  @Schema(description = "关联子查询改写为 JOIN")
  CORRELATED_SUBQUERY_TO_JOIN,

  @Schema(description = "列上函数改写为范围条件")
  FUNCTION_ON_COLUMN_TO_RANGE,

  @Schema(description = "隐式类型转换：统一为与列类型一致的字面量")
  IMPLICIT_CAST_NORMALIZE,

  @Schema(description = "LIMIT 无 ORDER BY 时补排序键")
  LIMIT_ADD_ORDER_BY,

  @Schema(description = "大表 COUNT(*) 替代方案提示")
  COUNT_STAR_LARGE_TABLE_HINT
}
