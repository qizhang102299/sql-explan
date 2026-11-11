package com.sqlexplan.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sqlexplan.model.domain.LlmStructuredAnalysis;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link LlmStructuredOutputParser} 单元测试。
 */
class LlmStructuredOutputParserTest {

  private LlmStructuredOutputParser parser;

  @BeforeEach
  void setUp() {
    parser = new LlmStructuredOutputParser();
  }

  @Test
  void parseAnthropicResponseExtractsJson() {
    String response = """
        {"content":[{"type":"text","text":"{\\"semantic\\":{\\"intentSummary\\":\\"test\\"},\
        \\"planNarrative\\":\\"ok\\",\\"indexAdvices\\":[],\\"optimizedSql\\":\\"\\",\
        \\"rewriteExplanation\\":\\"\\"}"}],"id":"msg","type":"message"}
        """;
    LlmStructuredAnalysis analysis = parser.parseAnthropicResponse(response);
    assertThat(analysis.getSemantic().getIntentSummary()).isEqualTo("test");
    assertThat(analysis.getPlanNarrative()).isEqualTo("ok");
  }

  @Test
  void parseAnalysisJsonWithFence() {
    String json = """
        ```json
        {"semantic":{"intentSummary":"a"},"planNarrative":"b","indexAdvices":[],
        "optimizedSql":"SELECT 1","rewriteExplanation":"c"}
        ```
        """;
    LlmStructuredAnalysis analysis = parser.parseAnalysisJson(json);
    assertThat(analysis.getOptimizedSql()).isEqualTo("SELECT 1");
  }

  @Test
  void parseEmptyThrows() {
    assertThatThrownBy(() -> parser.parseAnalysisJson(""))
        .hasMessageContaining("为空");
  }

  @Test
  void parseOpenAiResponse() {
    String response = """
        {"choices":[{"message":{"content":"{\\"semantic\\":{\\"intentSummary\\":\\"oai\\"},\
        \\"planNarrative\\":\\"\\",\\"indexAdvices\\":[],\\"optimizedSql\\":\\"\\",\
        \\"rewriteExplanation\\":\\"\\"}"}}]}
        """;
    LlmStructuredAnalysis analysis = parser.parseOpenAiResponse(response);
    assertThat(analysis.getSemantic().getIntentSummary()).isEqualTo("oai");
  }
}
