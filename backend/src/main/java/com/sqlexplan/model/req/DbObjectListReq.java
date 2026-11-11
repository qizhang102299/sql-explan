package com.sqlexplan.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 数据源对象列表请求。
 */
@Data
@Schema(description = "数据源对象列表请求")
public class DbObjectListReq {

  @NotNull(message = "数据源 ID 不能为空")
  @Schema(description = "数据源 ID", requiredMode = Schema.RequiredMode.REQUIRED)
  private Long dataSourceId;
}
