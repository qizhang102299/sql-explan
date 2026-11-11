package com.sqlexplan.manager;

import com.sqlexplan.constant.LlmConstants;
import com.sqlexplan.model.enums.LlmApiProtocolEnum;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 从环境变量解析 LLM API Key（不读配置、不写日志）。
 */
@Component
public class AnthropicApiKeyResolver {

  /**
   * 按协议读取 API Key。
   *
   * @param protocol API 协议
   * @return 非空则可用；未配置时可能返回空字符串（OpenAI 兼容本地场景允许为空）
   */
  public String resolve(LlmApiProtocolEnum protocol) {
    if (protocol == LlmApiProtocolEnum.OPENAI_COMPATIBLE) {
      String openAi = System.getenv(LlmConstants.ENV_OPENAI_API_KEY);
      if (StringUtils.hasText(openAi)) {
        return openAi.trim();
      }
      String anthropic = System.getenv(LlmConstants.ENV_API_KEY);
      return StringUtils.hasText(anthropic) ? anthropic.trim() : "";
    }
    String key = System.getenv(LlmConstants.ENV_API_KEY);
    if (!StringUtils.hasText(key)) {
      return "";
    }
    return key.trim();
  }
}
