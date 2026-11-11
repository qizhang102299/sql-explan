package com.sqlexplan.model.res;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 数据源连通性测试结果。
 */
@Data
@Schema(description = "数据源连通性测试结果")
public class DataSourceTestRes {

  @Schema(description = "是否连通")
  private Boolean success;

  @Schema(description = "说明信息")
  private String message;

  @Schema(description = "连接耗时（毫秒）")
  private Long elapsedMs;
}
