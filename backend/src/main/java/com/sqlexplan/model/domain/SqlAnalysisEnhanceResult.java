package com.sqlexplan.model.domain;

import com.sqlexplan.model.enums.AnalysisSourceEnum;
import lombok.Data;

/**
 * 阶段 5：规则引擎 + LLM 增强后的分析片段（供阶段 6 编排复用）。
 */
@Data
public class SqlAnalysisEnhanceResult {

  /**
   * 是否实际使用了 AI 成功输出。
   */
  private boolean aiUsed;

  /**
   * 语义与计划白话解释的来源。
   */
  private AnalysisSourceEnum analysisSource;

  /**
   * 未使用 AI 或降级时的说明（如「未使用 AI」）。
   */
  private String aiStatusNote;

  private boolean fromCache;

  private SqlSemanticInsight semantic = new SqlSemanticInsight();

  private String planNarrative;

  private IndexAdviceReport indexAdviceReport = new IndexAdviceReport();

  private String optimizedSql;

  private String rewriteExplanation;

  private boolean optimizedSqlAccepted;

  private AiOptimizedSqlValidation optimizedValidation;
}
