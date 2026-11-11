package com.sqlexplan.model.domain;

import lombok.Data;

/**
 * 查询涉及的物理表引用（含别名）。
 */
@Data
public class TableReference {

  /**
   * 表名（不含 schema 时仅为表名；含 schema 时为 schema.table）。
   */
  private String tableName;

  /**
   * schema 名（可为空）。
   */
  private String schemaName;

  /**
   * 查询中的别名（可为空）。
   */
  private String alias;
}
