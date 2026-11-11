package com.sqlexplan.manager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sqlexplan.exception.BizException;
import com.sqlexplan.model.domain.LlmIndexAdviceItem;
import com.sqlexplan.model.domain.LlmStructuredAnalysis;
import com.sqlexplan.model.domain.SqlSemanticInsight;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 解析 Anthropic 响应中的 JSON 结构化分析。
 */
@Component
public class LlmStructuredOutputParser {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final Pattern JSON_FENCE_PATTERN =
      Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

  /**
   * 从 Messages API 响应体解析结构化分析。
   *
   * @param responseBody HTTP 响应 JSON
   * @return 结构化分析
   */
  /**
   * 从 OpenAI Chat Completions 响应体解析结构化分析。
   *
   * @param responseBody HTTP 响应 JSON
   * @return 结构化分析
   */
  public LlmStructuredAnalysis parseOpenAiResponse(String responseBody) {
    if (!StringUtils.hasText(responseBody)) {
      throw new BizException("LLM 响应为空");
    }
    try {
      JsonNode root = OBJECT_MAPPER.readTree(responseBody);
      JsonNode choices = root.path("choices");
      if (!choices.isArray() || choices.isEmpty()) {
        throw new BizException("LLM 响应缺少 choices");
      }
      String content = choices.get(0).path("message").path("content").asText("");
      if (!StringUtils.hasText(content)) {
        throw new BizException("LLM 响应无文本内容");
      }
      return parseAnalysisJson(extractJsonObject(content));
    } catch (BizException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new BizException("LLM 响应解析失败: " + ex.getMessage());
    }
  }

  /**
   * 从 Messages API 响应体解析结构化分析。
   *
   * @param responseBody HTTP 响应 JSON
   * @return 结构化分析
   */
  public LlmStructuredAnalysis parseAnthropicResponse(String responseBody) {
    if (!StringUtils.hasText(responseBody)) {
      throw new BizException("LLM 响应为空");
    }
    try {
      JsonNode root = OBJECT_MAPPER.readTree(responseBody);
      JsonNode content = root.path("content");
      if (!content.isArray()) {
        throw new BizException("LLM 响应缺少 content");
      }
      StringBuilder text = new StringBuilder();
      for (JsonNode block : content) {
        if ("text".equals(block.path("type").asText()) && block.has("text")) {
          text.append(block.get("text").asText());
        }
      }
      if (text.isEmpty()) {
        throw new BizException("LLM 响应无文本内容");
      }
      return parseAnalysisJson(extractJsonObject(text.toString()));
    } catch (BizException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new BizException("LLM 响应解析失败: " + ex.getMessage());
    }
  }

  /**
   * 解析模型输出的 JSON 对象为结构化分析。
   *
   * @param jsonText JSON 文本
   * @return 结构化分析
   */
  public LlmStructuredAnalysis parseAnalysisJson(String jsonText) {
    if (!StringUtils.hasText(jsonText)) {
      throw new BizException("LLM 输出 JSON 为空");
    }
    try {
      JsonNode root = OBJECT_MAPPER.readTree(extractJsonObject(jsonText));
      LlmStructuredAnalysis analysis = new LlmStructuredAnalysis();
      analysis.setSemantic(parseSemantic(root.path("semantic")));
      analysis.setPlanNarrative(textOrEmpty(root, "planNarrative"));
      analysis.setIndexAdvices(parseIndexAdvices(root.path("indexAdvices")));
      analysis.setOptimizedSql(textOrEmpty(root, "optimizedSql"));
      analysis.setRewriteExplanation(textOrEmpty(root, "rewriteExplanation"));
      return analysis;
    } catch (Exception ex) {
      throw new BizException("LLM 输出 JSON 解析失败: " + ex.getMessage());
    }
  }

  private static SqlSemanticInsight parseSemantic(JsonNode node) {
    SqlSemanticInsight semantic = new SqlSemanticInsight();
    if (node == null || node.isMissingNode()) {
      return semantic;
    }
    semantic.setIntentSummary(textOrEmpty(node, "intentSummary"));
    semantic.setFiltersDescription(textOrEmpty(node, "filtersDescription"));
    semantic.setJoinsDescription(textOrEmpty(node, "joinsDescription"));
    semantic.setAggregationDescription(textOrEmpty(node, "aggregationDescription"));
    semantic.setOrderingDescription(textOrEmpty(node, "orderingDescription"));
    semantic.setPaginationDescription(textOrEmpty(node, "paginationDescription"));
    semantic.setSubqueryCteDescription(textOrEmpty(node, "subqueryCteDescription"));
    semantic.setInvolvedTables(readStringList(node.path("involvedTables")));
    semantic.setRiskNotes(readStringList(node.path("riskNotes")));
    return semantic;
  }

  private static List<LlmIndexAdviceItem> parseIndexAdvices(JsonNode node) {
    List<LlmIndexAdviceItem> list = new ArrayList<>();
    if (node == null || !node.isArray()) {
      return list;
    }
    for (JsonNode item : node) {
      LlmIndexAdviceItem advice = new LlmIndexAdviceItem();
      advice.setDdl(textOrEmpty(item, "ddl"));
      advice.setTableName(textOrEmpty(item, "tableName"));
      advice.setReason(textOrEmpty(item, "reason"));
      advice.setExpectedBenefit(textOrEmpty(item, "expectedBenefit"));
      advice.setWriteCostNote(textOrEmpty(item, "writeCostNote"));
      list.add(advice);
    }
    return list;
  }

  private static List<String> readStringList(JsonNode array) {
    List<String> list = new ArrayList<>();
    if (array == null || !array.isArray()) {
      return list;
    }
    Iterator<JsonNode> it = array.elements();
    while (it.hasNext()) {
      JsonNode n = it.next();
      if (n != null && n.isTextual()) {
        list.add(n.asText());
      }
    }
    return list;
  }

  private static String textOrEmpty(JsonNode node, String field) {
    if (node == null || node.isMissingNode()) {
      return "";
    }
    JsonNode child = node.path(field);
    return child.isMissingNode() || child.isNull() ? "" : child.asText("");
  }

  private static String extractJsonObject(String raw) {
    String trimmed = raw.trim();
    Matcher fence = JSON_FENCE_PATTERN.matcher(trimmed);
    if (fence.find()) {
      return fence.group(1).trim();
    }
    int start = trimmed.indexOf('{');
    int end = trimmed.lastIndexOf('}');
    if (start >= 0 && end > start) {
      return trimmed.substring(start, end + 1);
    }
    return trimmed;
  }
}
