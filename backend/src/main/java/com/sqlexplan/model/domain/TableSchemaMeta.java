package com.sqlexplan.model.domain;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 单张表的 schema 元数据汇总。
 */
@Data
public class TableSchemaMeta {

  private String tableName;

  /**
   * information_schema.TABLES.TABLE_ROWS 估计行数。
   */
  private Long estimatedRows;

  private List<ColumnMeta> columns = new ArrayList<>();

  private List<IndexMeta> indexes = new ArrayList<>();
}
