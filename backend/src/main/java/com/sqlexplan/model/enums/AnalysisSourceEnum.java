package com.sqlexplan.model.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * SQL 语义与计划解释的来源。
 */
@Schema(description = "分析结果来源")
public enum AnalysisSourceEnum {

  @Schema(description = "本地规则模板生成，未调用外网 LLM")
  LOCAL_RULES,

  @Schema(description = "AI（LLM）生成或增强")
  AI
}
