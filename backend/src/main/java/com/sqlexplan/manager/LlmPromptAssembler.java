package com.sqlexplan.manager;

import com.sqlexplan.constant.LlmConstants;
import com.sqlexplan.exception.BizException;
import com.sqlexplan.model.domain.ExplainPlan;
import com.sqlexplan.model.domain.IndexAdviceReport;
import com.sqlexplan.model.domain.PlanInterpretation;
import com.sqlexplan.model.domain.SqlRewriteResult;
import com.sqlexplan.model.domain.SqlStructure;
import com.sqlexplan.model.domain.TableSchemaMeta;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

/**
 * 组装发给 LLM 的 system / user 提示词（不含密码与数据行）。
 */
@Component
public class LlmPromptAssembler {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final String systemPrompt;

  private final String userTemplate;

  public LlmPromptAssembler() {
    this.systemPrompt = loadClasspath(LlmConstants.PROMPT_SYSTEM_PATH);
    this.userTemplate = loadClasspath(LlmConstants.PROMPT_USER_TEMPLATE_PATH);
  }

  /**
   * 获取 system 提示词全文。
   *
   * @return system 提示词
   */
  public String getSystemPrompt() {
    return systemPrompt;
  }

  /**
   * 构建 user 消息正文。
   *
   * @param sql                 原始 SQL
   * @param sqlStructure        解析结构
   * @param tableMetadata       表结构元数据
   * @param explainPlan         执行计划
   * @param planInterpretation  规则解读
   * @param ruleIndexReport     规则索引建议
   * @param ruleRewriteResult   规则改写
   * @return user 消息文本
   */
  public String buildUserMessage(
      String sql,
      SqlStructure sqlStructure,
      List<TableSchemaMeta> tableMetadata,
      ExplainPlan explainPlan,
      PlanInterpretation planInterpretation,
      IndexAdviceReport ruleIndexReport,
      SqlRewriteResult ruleRewriteResult) {
    String message = userTemplate;
    message = message.replace("{{SQL}}", nullToEmpty(sql));
    message = message.replace("{{SQL_STRUCTURE_JSON}}", toJson(sqlStructure));
    message = message.replace("{{SCHEMA_META_JSON}}", toJson(tableMetadata));
    message = message.replace("{{EXPLAIN_PLAN_JSON}}", toJson(explainPlan));
    message = message.replace(
        "{{PLAN_INTERPRETATION_SUMMARY}}",
        planInterpretation == null ? "" : nullToEmpty(planInterpretation.getSummary()));
    message = message.replace("{{RULE_INDEX_SUMMARY}}", summarizeIndex(ruleIndexReport));
    message = message.replace("{{RULE_REWRITE_SUMMARY}}", summarizeRewrite(ruleRewriteResult));
    return message;
  }

  private static String summarizeIndex(IndexAdviceReport report) {
    if (report == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    if (StringUtils.hasText(report.getSummary())) {
      sb.append(report.getSummary()).append('\n');
    }
    if (report.getAdvices() != null) {
      report.getAdvices().forEach(advice -> {
        if (StringUtils.hasText(advice.getCreateIndexDdl())) {
          sb.append(advice.getCreateIndexDdl()).append('\n');
        } else if (StringUtils.hasText(advice.getReason())) {
          sb.append(advice.getReason()).append('\n');
        }
      });
    }
    return sb.toString().trim();
  }

  private static String summarizeRewrite(SqlRewriteResult result) {
    if (result == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    if (StringUtils.hasText(result.getRecommendedSql())) {
      sb.append("推荐 SQL: ").append(result.getRecommendedSql()).append('\n');
    }
    if (result.getSuggestions() != null) {
      result.getSuggestions().forEach(s -> {
        if (StringUtils.hasText(s.getDescription())) {
          sb.append("- ").append(s.getDescription()).append('\n');
        }
      });
    }
    return sb.toString().trim();
  }

  private static String toJson(Object value) {
    if (value == null) {
      return "null";
    }
    try {
      return OBJECT_MAPPER.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
      throw new BizException("序列化提示词上下文失败");
    }
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private static String loadClasspath(String path) {
    try {
      ClassPathResource resource = new ClassPathResource(path);
      try (InputStream in = resource.getInputStream()) {
        return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
      }
    } catch (IOException ex) {
      throw new BizException("加载提示词模板失败: " + path);
    }
  }
}
