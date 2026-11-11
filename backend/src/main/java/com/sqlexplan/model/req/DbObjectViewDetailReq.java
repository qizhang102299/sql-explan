package com.sqlexplan.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 视图详情请求。
 */
@Data
@Schema(description = "视图详情请求")
public class DbObjectViewDetailReq {

  @NotNull(message = "数据源 ID 不能为空")
  @Schema(description = "数据源 ID", requiredMode = Schema.RequiredMode.REQUIRED)
  private Long dataSourceId;

  @NotBlank(message = "视图名不能为空")
  @Schema(description = "视图名", requiredMode = Schema.RequiredMode.REQUIRED)
  private String viewName;
}
