package com.sqlexplan.model.res.dbobject;

import com.sqlexplan.model.enums.DbRoutineTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 存储过程/函数摘要信息。
 */
@Data
@Schema(description = "例程摘要")
public class DbRoutineSummaryRes {

  @Schema(description = "名称")
  private String name;

  @Schema(description = "注释")
  private String comment;

  @Schema(description = "类型")
  private DbRoutineTypeEnum routineType;

  @Schema(description = "创建时间（毫秒时间戳）")
  private Long createdTime;

  @Schema(description = "修改时间（毫秒时间戳）")
  private Long modifiedTime;
}
