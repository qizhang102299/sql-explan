package com.sqlexplan.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * SQL 分析历史详情请求。
 */
@Data
@Schema(description = "分析历史详情请求")
public class SqlHistoryDetailReq {

  @NotNull(message = "记录 ID 不能为空")
  @Schema(description = "分析记录主键")
  private Long id;
}
