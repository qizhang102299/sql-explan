package com.sqlexplan.model.domain;

import com.sqlexplan.model.enums.SqlRewriteRuleTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 通过安全、EXPLAIN 与等价校验的改写候选。
 */
@Data
@Schema(description = "已验证通过的 SQL 改写候选")
public class ValidatedRewriteCandidate {

  @Schema(description = "规则类型")
  private SqlRewriteRuleTypeEnum ruleType;

  @Schema(description = "规则说明")
  private String description;

  @Schema(description = "改写后 SQL")
  private String rewrittenSql;
}
