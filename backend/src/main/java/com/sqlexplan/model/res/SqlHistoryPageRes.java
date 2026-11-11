package com.sqlexplan.model.res;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 分析历史分页响应。
 */
@Data
@Schema(description = "分析历史分页结果")
public class SqlHistoryPageRes {

  @Schema(description = "总条数")
  private Long total;

  @Schema(description = "当前页记录")
  private List<SqlHistoryRecordSummaryRes> records = new ArrayList<>();
}
