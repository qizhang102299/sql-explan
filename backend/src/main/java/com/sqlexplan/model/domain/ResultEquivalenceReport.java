package com.sqlexplan.model.domain;

import lombok.Data;

/**
 * 两条 SELECT 结果等价性校验报告。
 */
@Data
public class ResultEquivalenceReport {

  private boolean equivalent;

  private long originalRowCount;

  private long optimizedRowCount;

  private String originalChecksum;

  private String optimizedChecksum;

  /**
   * 行数超过配置上限时未计算内容校验和，仅比对行数。
   */
  private boolean checksumSkipped;

  private String message;
}
