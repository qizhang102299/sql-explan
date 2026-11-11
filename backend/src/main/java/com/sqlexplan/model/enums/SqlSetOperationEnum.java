package com.sqlexplan.model.enums;

import lombok.Getter;

/**
 * 集合运算类型（UNION / INTERSECT 等）。
 */
@Getter
public enum SqlSetOperationEnum {

  UNION("UNION"),

  UNION_ALL("UNION ALL"),

  INTERSECT("INTERSECT"),

  EXCEPT("EXCEPT");

  private final String label;

  SqlSetOperationEnum(String label) {
    this.label = label;
  }
}
