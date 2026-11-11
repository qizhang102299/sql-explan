package com.sqlexplan.model.domain;

import lombok.Data;

/**
 * 子查询引用（FROM 或 WHERE 等位置）。
 */
@Data
public class SubqueryClause {

  /**
   * 出现位置：FROM、WHERE、SELECT 等。
   */
  private String location;

  /**
   * 子查询别名（可为空）。
   */
  private String alias;

  /**
   * 子查询内部结构。
   */
  private SqlStructure innerStructure;
}
