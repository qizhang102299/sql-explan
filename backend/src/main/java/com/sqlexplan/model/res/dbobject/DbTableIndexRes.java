package com.sqlexplan.model.res.dbobject;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 表索引信息。
 */
@Data
@Schema(description = "表索引")
public class DbTableIndexRes {

  @Schema(description = "索引名")
  private String indexName;

  @Schema(description = "是否唯一")
  private Boolean unique;

  @Schema(description = "索引类型")
  private String indexType;

  @Schema(description = "索引基数估计")
  private Long cardinality;

  @Schema(description = "索引列")
  private List<DbTableIndexColumnRes> columns = new ArrayList<>();
}
