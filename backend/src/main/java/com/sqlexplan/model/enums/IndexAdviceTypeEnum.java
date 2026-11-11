package com.sqlexplan.model.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 索引建议类型。
 */
@Schema(description = "索引建议类型")
public enum IndexAdviceTypeEnum {

  @Schema(description = "建议新建联合索引")
  CREATE_INDEX,

  @Schema(description = "可复用已有索引（最左前缀）")
  REUSE_EXISTING_INDEX,

  @Schema(description = "覆盖索引机会")
  COVERING_INDEX,

  @Schema(description = "冗余索引")
  REDUNDANT_INDEX,

  @Schema(description = "重复索引（列序相同）")
  DUPLICATE_INDEX,

  @Schema(description = "谓词不利于索引使用的说明")
  NON_SARGABLE_OPERATOR_HINT
}
