package com.sqlexplan.model.res;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 数据源信息响应（不含密码）。
 */
@Data
@Schema(description = "数据源信息")
public class DataSourceRes {

  @Schema(description = "数据源 ID")
  private Long id;

  @Schema(description = "数据源名称")
  private String name;

  @Schema(description = "主机地址")
  private String host;

  @Schema(description = "端口")
  private Integer port;

  @Schema(description = "数据库名")
  private String dbName;

  @Schema(description = "用户名")
  private String username;

  @Schema(description = "创建时间（毫秒时间戳）")
  private Long createdTime;

  @Schema(description = "更新时间（毫秒时间戳）")
  private Long updatedTime;
}
