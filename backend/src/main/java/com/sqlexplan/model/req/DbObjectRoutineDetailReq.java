package com.sqlexplan.model.req;

import com.sqlexplan.model.enums.DbRoutineTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 存储过程/函数详情请求。
 */
@Data
@Schema(description = "存储过程/函数详情请求")
public class DbObjectRoutineDetailReq {

  @NotNull(message = "数据源 ID 不能为空")
  @Schema(description = "数据源 ID", requiredMode = Schema.RequiredMode.REQUIRED)
  private Long dataSourceId;

  @NotBlank(message = "例程名不能为空")
  @Schema(description = "例程名", requiredMode = Schema.RequiredMode.REQUIRED)
  private String routineName;

  @NotNull(message = "例程类型不能为空")
  @Schema(description = "例程类型", requiredMode = Schema.RequiredMode.REQUIRED)
  private DbRoutineTypeEnum routineType;
}
