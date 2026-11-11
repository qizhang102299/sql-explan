package com.sqlexplan.model.domain;

import lombok.Data;

/**
 * 索引列及基数信息。
 */
@Data
public class IndexColumnMeta {

  private String columnName;

  private Integer seqInIndex;

  /**
   * STATISTICS.CARDINALITY（MySQL 估计值，可为 null）。
   */
  private Long cardinality;
}
