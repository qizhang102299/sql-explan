package com.sqlexplan.model.res.dbobject;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 表基本信息。
 */
@Data
@Schema(description = "表基本信息")
public class DbTableBasicRes {

  @Schema(description = "表名")
  private String tableName;

  @Schema(description = "存储引擎")
  private String engine;

  @Schema(description = "字符集")
  private String charset;

  @Schema(description = "排序规则")
  private String collation;

  @Schema(description = "估计行数")
  private Long estimatedRows;

  @Schema(description = "数据大小（字节，含索引）")
  private Long dataSizeBytes;

  @Schema(description = "注释")
  private String comment;

  @Schema(description = "创建时间（毫秒时间戳）")
  private Long createdTime;
}
