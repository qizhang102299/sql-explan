package com.sqlexplan.model.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * SQL 校验错误类型。
 */
@Schema(description = "SQL 校验错误类型")
public enum SqlValidateErrorTypeEnum {

  @Schema(description = "语法错误（JSqlParser 或启发式检测）")
  SYNTAX,

  @Schema(description = "安全策略拒绝（非 SELECT、危险写法等）")
  SAFETY,

  @Schema(description = "语义错误（目标库 EXPLAIN 反馈）")
  SEMANTIC,

  @Schema(description = "MySQL 可执行但本系统解析器暂不支持")
  PARSER_UNSUPPORTED
}
