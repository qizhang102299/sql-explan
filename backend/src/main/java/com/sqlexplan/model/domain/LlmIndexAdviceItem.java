package com.sqlexplan.model.domain;

import lombok.Data;

/**
 * LLM 返回的单条索引建议项。
 */
@Data
public class LlmIndexAdviceItem {

  private String ddl;

  private String tableName;

  private String reason;

  private String expectedBenefit;

  private String writeCostNote;
}
