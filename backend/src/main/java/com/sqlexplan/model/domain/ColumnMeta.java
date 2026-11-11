package com.sqlexplan.model.domain;

import lombok.Data;

/**
 * 表字段元数据。
 */
@Data
public class ColumnMeta {

  private String columnName;

  private String dataType;

  private Boolean nullable;

  private String columnDefault;

  private String comment;

  private Integer ordinalPosition;
}
