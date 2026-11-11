package com.sqlexplan.constant;

/**
 * LLM（Anthropic Messages API）相关常量。
 */
public final class LlmConstants {

  public static final String ENV_API_KEY = "ANTHROPIC_API_KEY";

  public static final String DEFAULT_BASE_URL = "https://api.anthropic.com";

  public static final String ANTHROPIC_MESSAGES_PATH = "/v1/messages";

  public static final String OPENAI_CHAT_COMPLETIONS_PATH = "/v1/chat/completions";

  public static final String ANTHROPIC_VERSION = "2023-06-01";

  public static final String DEFAULT_MODEL_ID = "claude-sonnet-5-5";

  public static final String ENV_OPENAI_API_KEY = "OPENAI_API_KEY";

  public static final String HEADER_AUTHORIZATION = "Authorization";

  public static final String HEADER_API_KEY = "x-api-key";

  public static final String HEADER_ANTHROPIC_VERSION = "anthropic-version";

  public static final String HEADER_CONTENT_TYPE = "Content-Type";

  public static final String MEDIA_TYPE_JSON = "application/json";

  public static final int DEFAULT_MAX_OUTPUT_TOKENS = 8192;

  public static final int JSON_PARSE_MAX_RETRIES = 1;

  public static final int HTTP_MAX_RETRIES = 2;

  public static final String PROMPT_SYSTEM_PATH = "prompts/sql-analysis-system.txt";

  public static final String PROMPT_USER_TEMPLATE_PATH = "prompts/sql-analysis-user-template.txt";

  public static final String DEGRADE_NO_API_KEY = "未配置 ANTHROPIC_API_KEY，已降级为规则引擎结果";

  public static final String DEGRADE_DISABLED = "LLM 功能已关闭，未使用 AI";

  public static final String DEGRADE_CALL_FAILED = "LLM 调用失败，已降级为规则引擎结果";

  public static final String DEGRADE_PARSE_FAILED = "LLM 输出解析失败，已降级为规则引擎结果";

  private LlmConstants() {
  }
}
