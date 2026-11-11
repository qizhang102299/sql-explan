package com.sqlexplan.model.domain;

import lombok.Data;

/**
 * WITH 公用表表达式（CTE）。
 */
@Data
public class CteClause {

  private String name;

  private SqlStructure body;
}
