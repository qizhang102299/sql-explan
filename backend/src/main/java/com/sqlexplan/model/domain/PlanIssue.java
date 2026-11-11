package com.sqlexplan.model.domain;

import com.sqlexplan.model.enums.PlanIssueSeverityEnum;
import com.sqlexplan.model.enums.PlanIssueTypeEnum;
import lombok.Data;

/**
 * 规则识别出的执行计划或 SQL 结构问题点。
 */
@Data
public class PlanIssue {

  private PlanIssueTypeEnum issueType;

  private PlanIssueSeverityEnum severity;

  private String title;

  private String description;

  private String relatedTable;

  private String evidence;

  /**
   * 可执行的改写 SQL 或 CREATE INDEX 等 remediation（若有）。
   */
  private String remediationSql;
}
