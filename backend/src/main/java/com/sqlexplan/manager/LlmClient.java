package com.sqlexplan.manager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sqlexplan.config.SqlexplanProperties;
import com.sqlexplan.constant.LlmConstants;
import com.sqlexplan.exception.BizException;
import com.sqlexplan.model.domain.LlmStructuredAnalysis;
import com.sqlexplan.model.enums.LlmApiProtocolEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * LLM 封装：可配置 base-url、模型与协议；超时、重试、JSON 解析失败重试。
 */
@Slf4j
@Component
public class LlmClient {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final SqlexplanProperties sqlexplanProperties;

  private final LlmHttpTransport httpTransport;

  private final LlmStructuredOutputParser outputParser;

  private final AnthropicApiKeyResolver apiKeyResolver;

  public LlmClient(
      SqlexplanProperties sqlexplanProperties,
      LlmHttpTransport httpTransport,
      LlmStructuredOutputParser outputParser,
      AnthropicApiKeyResolver apiKeyResolver) {
    this.sqlexplanProperties = sqlexplanProperties;
    this.httpTransport = httpTransport;
    this.outputParser = outputParser;
    this.apiKeyResolver = apiKeyResolver;
  }

  /**
   * 调用 LLM 生成 SQL 结构化分析。
   *
   * @param systemPrompt system 提示词
   * @param userMessage  user 消息（不含密码与数据行）
   * @return 结构化分析；调用或解析失败时抛出 {@link BizException}
   */
  public LlmStructuredAnalysis analyze(String systemPrompt, String userMessage) {
    String apiKey = resolveApiKey();
    if (!isApiKeySatisfied(apiKey)) {
      throw new BizException(LlmConstants.DEGRADE_NO_API_KEY);
    }
    SqlexplanProperties.Llm llm = sqlexplanProperties.getLlm();
    String requestBody = buildRequestBody(llm, systemPrompt, userMessage);
    String url = resolveApiUrl(llm);
    BizException lastError = null;
    int maxAttempts = 1 + Math.max(LlmConstants.HTTP_MAX_RETRIES, LlmConstants.JSON_PARSE_MAX_RETRIES);
    for (int attempt = 0; attempt < maxAttempts; attempt++) {
      try {
        String responseBody = httpTransport.postJson(url, requestBody, apiKey, llm.getProtocol());
        return parseResponse(llm.getProtocol(), responseBody);
      } catch (BizException ex) {
        lastError = ex;
        log.warn("LLM 调用或解析失败, attempt={}", attempt + 1);
      }
    }
    throw lastError != null ? lastError : new BizException(LlmConstants.DEGRADE_CALL_FAILED);
  }

  /**
   * 配置与环境是否允许发起 LLM 请求。
   *
   * @return true 表示已启用且满足协议下的鉴权要求
   */
  public boolean isInvocationReady() {
    if (!sqlexplanProperties.getLlm().isEnabled()) {
      return false;
    }
    if (sqlexplanProperties.getLlm().getProtocol() == LlmApiProtocolEnum.OPENAI_COMPATIBLE) {
      return true;
    }
    return StringUtils.hasText(resolveApiKey());
  }

  /**
   * 解析完整请求 URL。
   *
   * @param llm 配置
   * @return 不含查询串的 POST URL
   */
  public static String resolveApiUrl(SqlexplanProperties.Llm llm) {
    String base = llm.getBaseUrl() == null ? "" : llm.getBaseUrl().trim();
    while (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    if (!StringUtils.hasText(base)) {
      base = LlmConstants.DEFAULT_BASE_URL;
    }
    String path = llm.getProtocol() == LlmApiProtocolEnum.OPENAI_COMPATIBLE
        ? LlmConstants.OPENAI_CHAT_COMPLETIONS_PATH
        : LlmConstants.ANTHROPIC_MESSAGES_PATH;
    return base + path;
  }

  private LlmStructuredAnalysis parseResponse(LlmApiProtocolEnum protocol, String responseBody) {
    if (protocol == LlmApiProtocolEnum.OPENAI_COMPATIBLE) {
      return outputParser.parseOpenAiResponse(responseBody);
    }
    return outputParser.parseAnthropicResponse(responseBody);
  }

  private boolean isApiKeySatisfied(String apiKey) {
    if (sqlexplanProperties.getLlm().getProtocol() == LlmApiProtocolEnum.OPENAI_COMPATIBLE) {
      return true;
    }
    return StringUtils.hasText(apiKey);
  }

  private String buildRequestBody(SqlexplanProperties.Llm llm, String systemPrompt, String userMessage) {
    if (llm.getProtocol() == LlmApiProtocolEnum.OPENAI_COMPATIBLE) {
      return buildOpenAiRequestBody(llm, systemPrompt, userMessage);
    }
    return buildAnthropicRequestBody(llm, systemPrompt, userMessage);
  }

  private String buildAnthropicRequestBody(SqlexplanProperties.Llm llm, String systemPrompt, String userMessage) {
    ObjectNode root = OBJECT_MAPPER.createObjectNode();
    root.put("model", llm.getModelId());
    root.put("max_tokens", llm.getMaxOutputTokens());
    ArrayNode system = OBJECT_MAPPER.createArrayNode();
    ObjectNode systemBlock = OBJECT_MAPPER.createObjectNode();
    systemBlock.put("type", "text");
    systemBlock.put("text", systemPrompt);
    system.add(systemBlock);
    root.set("system", system);
    ArrayNode messages = OBJECT_MAPPER.createArrayNode();
    ObjectNode userBlock = OBJECT_MAPPER.createObjectNode();
    userBlock.put("role", "user");
    ArrayNode content = OBJECT_MAPPER.createArrayNode();
    ObjectNode text = OBJECT_MAPPER.createObjectNode();
    text.put("type", "text");
    text.put("text", userMessage);
    content.add(text);
    userBlock.set("content", content);
    messages.add(userBlock);
    root.set("messages", messages);
    return writeJson(root);
  }

  private String buildOpenAiRequestBody(SqlexplanProperties.Llm llm, String systemPrompt, String userMessage) {
    ObjectNode root = OBJECT_MAPPER.createObjectNode();
    root.put("model", llm.getModelId());
    root.put("max_tokens", llm.getMaxOutputTokens());
    ArrayNode messages = OBJECT_MAPPER.createArrayNode();
    ObjectNode systemBlock = OBJECT_MAPPER.createObjectNode();
    systemBlock.put("role", "system");
    systemBlock.put("content", systemPrompt);
    messages.add(systemBlock);
    ObjectNode userBlock = OBJECT_MAPPER.createObjectNode();
    userBlock.put("role", "user");
    userBlock.put("content", userMessage);
    messages.add(userBlock);
    root.set("messages", messages);
    return writeJson(root);
  }

  private static String writeJson(ObjectNode root) {
    try {
      return OBJECT_MAPPER.writeValueAsString(root);
    } catch (Exception ex) {
      throw new BizException("构建 LLM 请求体失败");
    }
  }

  private String resolveApiKey() {
    return apiKeyResolver.resolve(sqlexplanProperties.getLlm().getProtocol());
  }
}
