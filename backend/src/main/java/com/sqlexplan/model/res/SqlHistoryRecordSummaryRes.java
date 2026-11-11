package com.sqlexplan.model.res;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 分析历史列表摘要。
 */
@Data
@Schema(description = "分析历史摘要")
public class SqlHistoryRecordSummaryRes {

  @Schema(description = "记录 ID")
  private Long id;

  @Schema(description = "数据源 ID")
  private Long dataSourceId;

  @Schema(description = "原始 SQL（列表可能截断展示）")
  private String sqlText;

  @Schema(description = "SQL 指纹")
  private String sqlFingerprint;

  @Schema(description = "分析总耗时（毫秒）")
  private Long costMs;

  @Schema(description = "创建时间（毫秒时间戳）")
  private Long createdTime;
}
