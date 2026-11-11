package com.sqlexplan.model.enums;

import lombok.Getter;

/**
 * SQL JOIN 类型。
 */
@Getter
public enum JoinTypeEnum {

  INNER("INNER JOIN"),

  LEFT("LEFT JOIN"),

  RIGHT("RIGHT JOIN"),

  FULL("FULL JOIN"),

  CROSS("CROSS JOIN"),

  NATURAL("NATURAL JOIN"),

  SIMPLE("JOIN"),

  UNKNOWN("UNKNOWN");

  private final String label;

  JoinTypeEnum(String label) {
    this.label = label;
  }
}
