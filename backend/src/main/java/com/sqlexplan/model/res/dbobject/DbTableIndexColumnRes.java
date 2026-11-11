package com.sqlexplan.model.res.dbobject;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 索引列信息。
 */
@Data
@Schema(description = "索引列")
public class DbTableIndexColumnRes {

  @Schema(description = "列名")
  private String columnName;

  @Schema(description = "列在索引中的顺序")
  private Integer seqInIndex;

  @Schema(description = "基数估计")
  private Long cardinality;
}
