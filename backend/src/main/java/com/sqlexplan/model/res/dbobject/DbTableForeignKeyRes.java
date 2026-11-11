package com.sqlexplan.model.res.dbobject;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 外键约束信息。
 */
@Data
@Schema(description = "外键")
public class DbTableForeignKeyRes {

  @Schema(description = "约束名")
  private String constraintName;

  @Schema(description = "本表列")
  private List<String> columnNames = new ArrayList<>();

  @Schema(description = "引用表")
  private String referencedTableName;

  @Schema(description = "引用列")
  private List<String> referencedColumnNames = new ArrayList<>();
}
