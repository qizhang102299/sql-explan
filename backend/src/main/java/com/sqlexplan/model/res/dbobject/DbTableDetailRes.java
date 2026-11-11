package com.sqlexplan.model.res.dbobject;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 表结构详情响应。
 */
@Data
@Schema(description = "表结构详情")
public class DbTableDetailRes {

  @Schema(description = "基本信息")
  private DbTableBasicRes basic;

  @Schema(description = "字段列表")
  private List<DbTableColumnRes> columns = new ArrayList<>();

  @Schema(description = "索引列表")
  private List<DbTableIndexRes> indexes = new ArrayList<>();

  @Schema(description = "外键列表")
  private List<DbTableForeignKeyRes> foreignKeys = new ArrayList<>();

  @Schema(description = "建表 DDL")
  private String createTableDdl;
}
