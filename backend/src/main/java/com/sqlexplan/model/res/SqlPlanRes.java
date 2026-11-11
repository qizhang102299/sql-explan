package com.sqlexplan.model.res;

import com.sqlexplan.model.domain.ExplainPlan;
import com.sqlexplan.model.domain.PlanInterpretation;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 执行计划与规则解读响应。
 */
@Data
@Schema(description = "执行计划与解读")
public class SqlPlanRes {

  @Schema(description = "结构化执行计划")
  private ExplainPlan explainPlan;

  @Schema(description = "规则识别的问题点")
  private PlanInterpretation interpretation;

  @Schema(description = "白话解释")
  private String narrative;
}
