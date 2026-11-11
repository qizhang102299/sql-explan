package com.sqlexplan.model.res.dbobject;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 视图详情响应。
 */
@Data
@Schema(description = "视图详情")
public class DbViewDetailRes {

  @Schema(description = "视图名")
  private String viewName;

  @Schema(description = "字段列表")
  private List<DbViewColumnRes> columns = new ArrayList<>();

  @Schema(description = "视图定义 SQL")
  private String definitionSql;

  @Schema(description = "CHECK OPTION")
  private String checkOption;

  @Schema(description = "DEFINER")
  private String definer;

  @Schema(description = "SECURITY TYPE")
  private String securityType;
}
