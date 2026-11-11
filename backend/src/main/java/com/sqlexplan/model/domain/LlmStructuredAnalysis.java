package com.sqlexplan.model.domain;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * LLM 结构化分析输出（与 prompt 约定 JSON 对应）。
 */
@Data
public class LlmStructuredAnalysis {

  private SqlSemanticInsight semantic = new SqlSemanticInsight();

  private String planNarrative;

  private List<LlmIndexAdviceItem> indexAdvices = new ArrayList<>();

  private String optimizedSql;

  private String rewriteExplanation;
}
