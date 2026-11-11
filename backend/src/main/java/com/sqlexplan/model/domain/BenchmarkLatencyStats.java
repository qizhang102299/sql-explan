package com.sqlexplan.model.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 单侧 SQL 多次执行的耗时统计。
 */
@Data
@Schema(description = "耗时统计（毫秒）")
public class BenchmarkLatencyStats {

  @Schema(description = "执行次数")
  private int executionCount;

  @Schema(description = "中位数（毫秒）")
  private Long medianMs;

  @Schema(description = "P95（毫秒）")
  private Long p95Ms;

  @Schema(description = "最小值（毫秒）")
  private Long minMs;

  @Schema(description = "最大值（毫秒）")
  private Long maxMs;
}
