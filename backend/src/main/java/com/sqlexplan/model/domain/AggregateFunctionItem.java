package com.sqlexplan.model.domain;

import lombok.Data;

/**
 * 聚合函数项。
 */
@Data
public class AggregateFunctionItem {

  private String functionName;

  private String argumentsText;

  private String alias;
}
