package com.sqlexplan.model.res.dbobject;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 表摘要信息。
 */
@Data
@Schema(description = "表摘要")
public class DbTableSummaryRes {

  @Schema(description = "表名")
  private String name;

  @Schema(description = "注释")
  private String comment;

  @Schema(description = "存储引擎")
  private String engine;

  @Schema(description = "估计行数")
  private Long estimatedRows;

  @Schema(description = "数据大小（字节，含索引）")
  private Long dataSizeBytes;

  @Schema(description = "创建时间（毫秒时间戳）")
  private Long createdTime;

  @Schema(description = "更新时间（毫秒时间戳）")
  private Long updatedTime;
}
