package com.sqlexplan.model.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 执行计划问题严重级别。
 */
@Schema(description = "执行计划问题严重级别")
public enum PlanIssueSeverityEnum {

  @Schema(description = "严重")
  CRITICAL,

  @Schema(description = "警告")
  WARNING,

  @Schema(description = "提示")
  INFO
}
