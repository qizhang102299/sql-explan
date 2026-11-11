package com.sqlexplan.model.domain;

import lombok.Data;

/**
 * WHERE 中的谓词条件（叶子或复合表达式摘要）。
 */
@Data
public class WherePredicate {

  /**
   * 左侧列名或表达式。
   */
  private String leftExpression;

  /**
   * 运算符（=、>、LIKE、IN 等，复合条件时为 AND/OR）。
   */
  private String operator;

  /**
   * 右侧表达式原文（可为空）。
   */
  private String rightExpression;

  /**
   * 列是否被函数包裹（如 YEAR(col) = 2024）。
   */
  private Boolean columnWrappedByFunction;

  /**
   * 完整谓词文本。
   */
  private String predicateText;
}
