package com.sqlexplan.model.res.dbobject;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 数据源对象列表响应。
 */
@Data
@Schema(description = "数据源对象列表")
public class DbObjectListRes {

  @Schema(description = "表列表")
  private List<DbTableSummaryRes> tables = new ArrayList<>();

  @Schema(description = "视图列表")
  private List<DbViewSummaryRes> views = new ArrayList<>();

  @Schema(description = "存储过程与函数列表")
  private List<DbRoutineSummaryRes> routines = new ArrayList<>();
}
