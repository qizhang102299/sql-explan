package com.sqlexplan.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * SQL 分析历史分页请求。
 */
@Data
@Schema(description = "分析历史分页请求")
public class SqlHistoryPageReq {

  @Min(value = 1, message = "页码最小为 1")
  @Schema(description = "页码", example = "1")
  private Integer pageNum = 1;

  @Min(value = 1, message = "每页条数最小为 1")
  @Max(value = 100, message = "每页条数最大为 100")
  @Schema(description = "每页条数", example = "10")
  private Integer pageSize = 10;

  @Schema(description = "按数据源过滤，可为空")
  private Long dataSourceId;
}
