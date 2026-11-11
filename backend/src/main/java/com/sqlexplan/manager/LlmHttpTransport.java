package com.sqlexplan.manager;

import com.sqlexplan.model.enums.LlmApiProtocolEnum;

/**
 * LLM HTTP 传输抽象（便于单元测试注入 Mock）。
 */
public interface LlmHttpTransport {

  /**
   * 发送 POST 请求。
   *
   * @param url         完整 URL
   * @param jsonBody    JSON 请求体（不得包含 API Key）
   * @param apiKey      API Key（由调用方从环境变量读取）
   * @param protocol    API 协议，决定请求头
   * @return HTTP 响应体文本；非 2xx 时抛出 {@link com.sqlexplan.exception.BizException}
   */
  String postJson(String url, String jsonBody, String apiKey, LlmApiProtocolEnum protocol);
}
