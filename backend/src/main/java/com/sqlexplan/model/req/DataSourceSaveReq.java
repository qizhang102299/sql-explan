package com.sqlexplan.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 数据源保存（新增或更新）请求。
 */
@Data
@Schema(description = "数据源保存请求")
public class DataSourceSaveReq {

  @Schema(description = "数据源 ID，为空表示新增")
  private Long id;

  @NotBlank(message = "名称不能为空")
  @Size(max = 128, message = "名称长度不能超过 128")
  @Schema(description = "数据源名称", requiredMode = Schema.RequiredMode.REQUIRED)
  private String name;

  @NotBlank(message = "主机不能为空")
  @Size(max = 255, message = "主机长度不能超过 255")
  @Schema(description = "主机地址", requiredMode = Schema.RequiredMode.REQUIRED)
  private String host;

  @NotNull(message = "端口不能为空")
  @Min(value = 1, message = "端口取值范围为 1-65535")
  @Max(value = 65535, message = "端口取值范围为 1-65535")
  @Schema(description = "端口", requiredMode = Schema.RequiredMode.REQUIRED)
  private Integer port;

  @NotBlank(message = "数据库名不能为空")
  @Size(max = 128, message = "数据库名长度不能超过 128")
  @Schema(description = "数据库名", requiredMode = Schema.RequiredMode.REQUIRED)
  private String dbName;

  @NotBlank(message = "用户名不能为空")
  @Size(max = 128, message = "用户名长度不能超过 128")
  @Schema(description = "用户名", requiredMode = Schema.RequiredMode.REQUIRED)
  private String username;

  @Schema(description = "密码，更新时为空则保留原密码")
  private String password;
}
