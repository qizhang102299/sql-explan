package com.sqlexplan.model.req;

import com.sqlexplan.constant.BenchmarkConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * SQL 综合分析请求。
 */
@Data
@Schema(description = "SQL 分析请求")
public class SqlAnalyzeReq {

  @NotNull(message = "数据源 ID 不能为空")
  @Schema(description = "目标数据源 ID")
  private Long dataSourceId;

  @NotBlank(message = "SQL 不能为空")
  @Schema(description = "待分析的 SELECT")
  private String sql;

  @Schema(description = "是否执行优化前后耗时对比", example = "true")
  private Boolean enableBenchmark = Boolean.TRUE;

  @Min(value = BenchmarkConstants.MIN_ITERATIONS, message = "基准测试次数最少为 1")
  @Max(value = BenchmarkConstants.MAX_ITERATIONS, message = "基准测试次数最多为 20")
  @Schema(description = "基准测试交替执行次数（每侧）", example = "5")
  private Integer benchmarkIterations = BenchmarkConstants.DEFAULT_ITERATIONS;

  @Schema(description = "是否在目标库创建临时索引验证索引建议收益（默认仅 EXPLAIN 预估）", example = "false")
  private Boolean verifyIndexBenefit = Boolean.FALSE;
}
