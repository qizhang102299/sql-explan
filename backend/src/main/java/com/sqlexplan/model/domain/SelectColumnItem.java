package com.sqlexplan.model.domain;

import lombok.Data;

/**
 * SELECT 列表中的列或表达式项。
 */
@Data
public class SelectColumnItem {

  /**
   * 表达式原文。
   */
  private String expression;

  /**
   * 输出别名（可为空）。
   */
  private String alias;

  /**
   * 关联表名或别名（可为空，表达式无明确表前缀时）。
   */
  private String tableQualifier;
}
