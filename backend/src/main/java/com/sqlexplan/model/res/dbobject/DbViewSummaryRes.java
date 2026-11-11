package com.sqlexplan.model.res.dbobject;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 视图摘要信息。
 */
@Data
@Schema(description = "视图摘要")
public class DbViewSummaryRes {

  @Schema(description = "视图名")
  private String name;

  @Schema(description = "注释")
  private String comment;

  @Schema(description = "是否可更新")
  private Boolean updatable;
}
