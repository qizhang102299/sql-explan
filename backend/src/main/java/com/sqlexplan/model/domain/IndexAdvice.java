package com.sqlexplan.model.domain;

import com.sqlexplan.model.enums.IndexAdviceTypeEnum;
import com.sqlexplan.model.enums.PlanIssueSeverityEnum;
import lombok.Data;

/**
 * 单条索引建议（含 DDL、理由与收益说明）。
 */
@Data
public class IndexAdvice {

  private IndexAdviceTypeEnum adviceType;

  private PlanIssueSeverityEnum severity;

  private String tableName;

  /**
   * CREATE INDEX 语句；复用已有索引或冗余提示时可为空。
   */
  private String createIndexDdl;

  private String reason;

  private String expectedBenefit;

  private String writeCostNote;

  /**
   * 关联的已有索引名（复用或冗余场景）。
   */
  private String relatedIndexName;
}
