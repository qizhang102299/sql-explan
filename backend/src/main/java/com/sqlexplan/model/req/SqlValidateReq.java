package com.sqlexplan.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * SQL 语法与安全校验请求。
 */
@Data
@Schema(description = "SQL 校验请求")
public class SqlValidateReq {

  @NotBlank(message = "SQL 不能为空")
  @Schema(description = "待校验 SQL")
  private String sql;

  @Schema(description = "目标数据源 ID；传入时执行 EXPLAIN 语义层校验")
  private Long dataSourceId;
}
