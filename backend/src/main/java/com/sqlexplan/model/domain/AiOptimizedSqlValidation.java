package com.sqlexplan.model.domain;

import lombok.Data;

/**
 * AI 改写 SQL 的校验流水线结果。
 */
@Data
public class AiOptimizedSqlValidation {

  private boolean passed;

  private boolean safetyPassed;

  private boolean explainPassed;

  private boolean equivalencePassed;

  private String rejectedSql;

  private String failureReason;

  private ExplainPlan explainPlan;

  private ResultEquivalenceReport equivalenceReport;
}
