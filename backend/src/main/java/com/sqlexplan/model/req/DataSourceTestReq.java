package com.sqlexplan.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 数据源连通性测试请求（按 ID 或按连接参数二选一）。
 */
@Data
@Schema(description = "数据源连通性测试请求")
public class DataSourceTestReq {

  @Schema(description = "已保存的数据源 ID")
  private Long dataSourceId;

  @Schema(description = "主机地址，未传 dataSourceId 时必填")
  private String host;

  @Min(value = 1, message = "端口取值范围为 1-65535")
  @Max(value = 65535, message = "端口取值范围为 1-65535")
  @Schema(description = "端口")
  private Integer port;

  @Schema(description = "数据库名")
  private String dbName;

  @Schema(description = "用户名")
  private String username;

  @Schema(description = "密码")
  private String password;
}
