package com.sqlexplan.model.res;

import com.sqlexplan.model.domain.ResultEquivalenceReport;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import com.sqlexplan.model.domain.ValidatedRewriteCandidate;
import lombok.Data;

/**
 * 优化 SQL 与校验结果。
 */
@Data
@Schema(description = "优化 SQL")
public class OptimizedSqlRes {

  @Schema(description = "最终采纳的优化 SQL")
  private String sql;

  @Schema(description = "改写说明")
  private String explanation;

  @Schema(description = "是否通过等价校验（与原 SQL 相同时视为通过）")
  private boolean equivalencePassed;

  @Schema(description = "等价校验详情")
  private ResultEquivalenceReport equivalenceReport;

  @Schema(description = "通过校验的改写候选（仅展示通过的）")
  private List<ValidatedRewriteCandidate> passedCandidates = new ArrayList<>();
}
