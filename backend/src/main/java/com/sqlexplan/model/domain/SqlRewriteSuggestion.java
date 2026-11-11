package com.sqlexplan.model.domain;

import com.sqlexplan.model.enums.PlanIssueSeverityEnum;
import com.sqlexplan.model.enums.SqlRewriteRuleTypeEnum;
import lombok.Data;

/**
 * 单条 SQL 规则改写建议。
 */
@Data
public class SqlRewriteSuggestion {

  private SqlRewriteRuleTypeEnum ruleType;

  private PlanIssueSeverityEnum severity;

  private String description;

  /**
   * 改写后的 SQL；仅提示、无法安全自动生成时可为空。
   */
  private String rewrittenSql;
}
