package com.sqlexplan.model.res.dbobject;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 视图字段信息。
 */
@Data
@Schema(description = "视图字段")
public class DbViewColumnRes {

  @Schema(description = "序号")
  private Integer ordinalPosition;

  @Schema(description = "字段名")
  private String name;

  @Schema(description = "类型")
  private String columnType;

  @Schema(description = "是否可空")
  private Boolean nullable;

  @Schema(description = "默认值")
  private String defaultValue;

  @Schema(description = "注释")
  private String comment;
}
