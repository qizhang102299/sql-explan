package com.sqlexplan.model.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 优化前后基准测试报告。
 */
@Data
@Schema(description = "优化前后耗时对比")
public class BenchmarkReport {

  @Schema(description = "是否已执行基准测试")
  private boolean executed;

  @Schema(description = "重复次数（不含预热）")
  private Integer iterations;

  @Schema(description = "原 SQL 耗时统计")
  private BenchmarkLatencyStats originalStats;

  @Schema(description = "优化 SQL 耗时统计")
  private BenchmarkLatencyStats optimizedStats;

  /**
   * 相对原 SQL 中位数的提升比例，正值表示变快（(原-优)/原）。
   */
  @Schema(description = "耗时提升比例（相对原 SQL 中位数）")
  private Double improvementRatio;

  @Schema(description = "结果等价性报告")
  private ResultEquivalenceReport equivalence;

  @Schema(description = "结果不一致时为 true 并附带告警说明")
  private boolean equivalenceWarning;

  @Schema(description = "告警或补充说明")
  private String warningMessage;

  @Schema(description = "是否在目标库创建临时索引验证收益")
  private boolean indexBenefitVerified;

  @Schema(description = "索引验证：建索引前原 SQL 中位数（毫秒）")
  private Long originalMedianBeforeIndexMs;

  @Schema(description = "索引验证：建索引后原 SQL 中位数（毫秒）")
  private Long originalMedianAfterIndexMs;

  @Schema(description = "索引验证说明")
  private String indexVerificationNote;
}
