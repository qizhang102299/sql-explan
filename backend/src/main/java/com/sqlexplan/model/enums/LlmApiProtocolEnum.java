package com.sqlexplan.model.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * LLM HTTP 协议类型。
 */
@Schema(description = "LLM API 协议")
public enum LlmApiProtocolEnum {

  @Schema(description = "Anthropic Messages API")
  ANTHROPIC,

  @Schema(description = "OpenAI 兼容 Chat Completions（如 Ollama）")
  OPENAI_COMPATIBLE
}
