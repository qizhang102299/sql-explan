package com.sqlexplan.model.res;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/**
 * 数据源分页查询响应。
 */
@Data
@Schema(description = "数据源分页结果")
public class DataSourcePageRes {

  @Schema(description = "总记录数")
  private Long total;

  @Schema(description = "当前页数据")
  private List<DataSourceRes> records;
}
