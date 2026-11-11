package com.sqlexplan.model.res;

import com.sqlexplan.model.domain.BenchmarkReport;
import com.sqlexplan.model.domain.IndexAdviceReport;
import com.sqlexplan.model.domain.SqlSemanticInsight;
import com.sqlexplan.model.enums.AnalysisSourceEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * SQL 综合分析响应。
 */
@Data
@Schema(description = "SQL 综合分析结果")
public class SqlAnalyzeRes {

  @Schema(description = "记录 ID（分析完成后落库）")
  private Long recordId;

  @Schema(description = "语义与计划解释来源：本地规则或 AI")
  private AnalysisSourceEnum analysisSource;

  @Schema(description = "AI 状态说明（如未使用 AI、降级原因）")
  private String aiStatusNote;

  @Schema(description = "语义分析")
  private SqlSemanticInsight semantic;

  @Schema(description = "执行计划与解读")
  private SqlPlanRes plan;

  @Schema(description = "索引建议")
  private IndexAdviceReport indexAdvices;

  @Schema(description = "优化 SQL")
  private OptimizedSqlRes optimizedSql;

  @Schema(description = "耗时对比")
  private BenchmarkReport benchmark;

  @Schema(description = "分析完成时间（毫秒时间戳）")
  private Long createdTime;
}
