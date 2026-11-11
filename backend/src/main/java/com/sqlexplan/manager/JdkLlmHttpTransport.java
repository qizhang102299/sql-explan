package com.sqlexplan.manager;

import com.sqlexplan.config.SqlexplanProperties;
import com.sqlexplan.constant.LlmConstants;
import com.sqlexplan.exception.BizException;
import com.sqlexplan.model.enums.LlmApiProtocolEnum;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 基于 JDK {@link HttpClient} 的 LLM HTTP 实现。
 */
@Component
public class JdkLlmHttpTransport implements LlmHttpTransport {

  private final HttpClient httpClient;

  private final int readTimeoutMs;

  public JdkLlmHttpTransport(SqlexplanProperties sqlexplanProperties) {
    SqlexplanProperties.Llm llm = sqlexplanProperties.getLlm();
    this.readTimeoutMs = llm.getReadTimeoutMs();
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(llm.getConnectTimeoutMs()))
        .build();
  }

  @Override
  public String postJson(String url, String jsonBody, String apiKey, LlmApiProtocolEnum protocol) {
    LlmApiProtocolEnum effective = protocol == null ? LlmApiProtocolEnum.ANTHROPIC : protocol;
    if (effective == LlmApiProtocolEnum.ANTHROPIC && !StringUtils.hasText(apiKey)) {
      throw new BizException("缺少 API Key");
    }
    HttpRequest.Builder builder = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(Duration.ofMillis(readTimeoutMs))
        .header(LlmConstants.HEADER_CONTENT_TYPE, LlmConstants.MEDIA_TYPE_JSON)
        .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
    applyAuthHeaders(builder, apiKey, effective);
    try {
      HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
      int status = response.statusCode();
      if (status < 200 || status >= 300) {
        throw new BizException("LLM HTTP 状态异常: " + status);
      }
      return response.body();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new BizException("LLM 请求被中断");
    } catch (IOException ex) {
      throw new BizException("LLM 请求失败: " + ex.getMessage());
    }
  }

  private static void applyAuthHeaders(
      HttpRequest.Builder builder,
      String apiKey,
      LlmApiProtocolEnum protocol) {
    if (protocol == LlmApiProtocolEnum.OPENAI_COMPATIBLE) {
      if (StringUtils.hasText(apiKey)) {
        builder.header(LlmConstants.HEADER_AUTHORIZATION, "Bearer " + apiKey.trim());
      }
      return;
    }
    builder.header(LlmConstants.HEADER_API_KEY, apiKey.trim());
    builder.header(LlmConstants.HEADER_ANTHROPIC_VERSION, LlmConstants.ANTHROPIC_VERSION);
  }
}
