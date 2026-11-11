package com.sqlexplan.model.domain;

import com.sqlexplan.model.enums.JoinTypeEnum;
import lombok.Data;

/**
 * JOIN 子句信息。
 */
@Data
public class JoinClause {

  private JoinTypeEnum joinType;

  /**
   * 右表名（物理名）。
   */
  private String rightTableName;

  /**
   * 右表别名。
   */
  private String rightTableAlias;

  /**
   * ON 条件原文（CROSS JOIN 等可能为空）。
   */
  private String onCondition;
}
