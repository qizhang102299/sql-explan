package com.sqlexplan.model.res.dbobject;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 例程参数信息。
 */
@Data
@Schema(description = "例程参数")
public class DbRoutineParameterRes {

  @Schema(description = "参数方向：IN/OUT/INOUT")
  private String direction;

  @Schema(description = "参数名")
  private String name;

  @Schema(description = "参数类型")
  private String parameterType;
}
