package com.sqlexplan.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sqlexplan.config.SqlexplanProperties;
import com.sqlexplan.model.domain.LlmStructuredAnalysis;
import com.sqlexplan.model.enums.LlmApiProtocolEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link LlmClient} 单元测试（Mock HTTP，不访问网络）。
 */
@ExtendWith(MockitoExtension.class)
class LlmClientTest {

  @Mock
  private LlmHttpTransport httpTransport;

  @Mock
  private AnthropicApiKeyResolver apiKeyResolver;

  private LlmClient llmClient;

  private SqlexplanProperties properties;

  @BeforeEach
  void setUp() {
    properties = new SqlexplanProperties();
    properties.getLlm().setEnabled(true);
    LlmStructuredOutputParser parser = new LlmStructuredOutputParser();
    llmClient = new LlmClient(properties, httpTransport, parser, apiKeyResolver);
  }

  @Test
  void analyzeSuccessAnthropic() {
    when(apiKeyResolver.resolve(LlmApiProtocolEnum.ANTHROPIC)).thenReturn("test-key");
    String body = """
        {"content":[{"type":"text","text":"{\\"semantic\\":{\\"intentSummary\\":\\"x\\"},\
        \\"planNarrative\\":\\"\\",\\"indexAdvices\\":[],\\"optimizedSql\\":\\"\\",\
        \\"rewriteExplanation\\":\\"\\"}"}],"type":"message"}
        """;
    when(httpTransport.postJson(anyString(), anyString(), eq("test-key"), eq(LlmApiProtocolEnum.ANTHROPIC)))
        .thenReturn(body);
    LlmStructuredAnalysis analysis = llmClient.analyze("system", "user");
    assertThat(analysis.getSemantic().getIntentSummary()).isEqualTo("x");
    ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
    verify(httpTransport, times(1))
        .postJson(urlCaptor.capture(), anyString(), eq("test-key"), eq(LlmApiProtocolEnum.ANTHROPIC));
    assertThat(urlCaptor.getValue()).endsWith("/v1/messages");
  }

  @Test
  void analyzeOpenAiCompatible() {
    properties.getLlm().setProtocol(LlmApiProtocolEnum.OPENAI_COMPATIBLE);
    properties.getLlm().setBaseUrl("http://127.0.0.1:11434");
    properties.getLlm().setModelId("llama3");
    when(apiKeyResolver.resolve(LlmApiProtocolEnum.OPENAI_COMPATIBLE)).thenReturn("");
    String openAiBody = """
        {"choices":[{"message":{"content":"{\\"semantic\\":{\\"intentSummary\\":\\"local\\"},\
        \\"planNarrative\\":\\"\\",\\"indexAdvices\\":[],\\"optimizedSql\\":\\"\\",\
        \\"rewriteExplanation\\":\\"\\"}"}}]}
        """;
    when(httpTransport.postJson(anyString(), anyString(), anyString(), eq(LlmApiProtocolEnum.OPENAI_COMPATIBLE)))
        .thenReturn(openAiBody);
    LlmStructuredAnalysis analysis = llmClient.analyze("system", "user");
    assertThat(analysis.getSemantic().getIntentSummary()).isEqualTo("local");
    ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
    verify(httpTransport).postJson(urlCaptor.capture(), anyString(), anyString(), eq(LlmApiProtocolEnum.OPENAI_COMPATIBLE));
    assertThat(urlCaptor.getValue()).isEqualTo("http://127.0.0.1:11434/v1/chat/completions");
  }

  @Test
  void analyzeRetriesOnParseFailure() {
    when(apiKeyResolver.resolve(LlmApiProtocolEnum.ANTHROPIC)).thenReturn("test-key");
    when(httpTransport.postJson(anyString(), anyString(), eq("test-key"), eq(LlmApiProtocolEnum.ANTHROPIC)))
        .thenReturn("{\"content\":[]}")
        .thenReturn("""
            {"content":[{"type":"text","text":"{\\"semantic\\":{},\\"planNarrative\\":\\"y\\",\
            \\"indexAdvices\\":[],\\"optimizedSql\\":\\"\\",\\"rewriteExplanation\\":\\"\\"}"}]}
            """);
    LlmStructuredAnalysis analysis = llmClient.analyze("system", "user");
    assertThat(analysis.getPlanNarrative()).isEqualTo("y");
    verify(httpTransport, times(2)).postJson(anyString(), anyString(), eq("test-key"), eq(LlmApiProtocolEnum.ANTHROPIC));
  }

  @Test
  void analyzeWithoutApiKeyThrows() {
    when(apiKeyResolver.resolve(LlmApiProtocolEnum.ANTHROPIC)).thenReturn("");
    assertThatThrownBy(() -> llmClient.analyze("system", "user"))
        .hasMessageContaining("ANTHROPIC_API_KEY");
  }

  @Test
  void invocationReadyOpenAiWithoutKey() {
    properties.getLlm().setProtocol(LlmApiProtocolEnum.OPENAI_COMPATIBLE);
    assertThat(llmClient.isInvocationReady()).isTrue();
  }
}
