package com.sqlexplan.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 表结构详情请求。
 */
@Data
@Schema(description = "表结构详情请求")
public class DbObjectTableDetailReq {

  @NotNull(message = "数据源 ID 不能为空")
  @Schema(description = "数据源 ID", requiredMode = Schema.RequiredMode.REQUIRED)
  private Long dataSourceId;

  @NotBlank(message = "表名不能为空")
  @Schema(description = "表名", requiredMode = Schema.RequiredMode.REQUIRED)
  private String tableName;
}
