package com.sqlexplan.model.res;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * SQL 安全校验结果。
 */
@Data
@Schema(description = "SQL 安全校验结果")
public class SqlSafetyCheckRes {

  @Schema(description = "是否通过")
  private Boolean passed;

  @Schema(description = "未通过时的原因说明")
  private String reason;
}
