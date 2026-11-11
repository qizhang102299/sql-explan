package com.sqlexplan.model.res.dbobject;

import com.sqlexplan.model.enums.DbRoutineTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 存储过程/函数详情响应。
 */
@Data
@Schema(description = "例程详情")
public class DbRoutineDetailRes {

  @Schema(description = "名称")
  private String routineName;

  @Schema(description = "类型")
  private DbRoutineTypeEnum routineType;

  @Schema(description = "参数列表")
  private List<DbRoutineParameterRes> parameters = new ArrayList<>();

  @Schema(description = "返回类型（函数）")
  private String returnType;

  @Schema(description = "源码定义")
  private String sourceDefinition;

  @Schema(description = "DEFINER")
  private String definer;

  @Schema(description = "是否确定性")
  private Boolean deterministic;

  @Schema(description = "SQL 数据访问特性")
  private String sqlDataAccess;

  @Schema(description = "创建时间（毫秒时间戳）")
  private Long createdTime;

  @Schema(description = "修改时间（毫秒时间戳）")
  private Long modifiedTime;
}
