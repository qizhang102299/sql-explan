package com.sqlexplan.model.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 存储过程/函数类型。
 */
@Schema(description = "例程类型")
public enum DbRoutineTypeEnum {

  @Schema(description = "存储过程")
  PROCEDURE,

  @Schema(description = "函数")
  FUNCTION
}
