package com.sqlexplan.model.res;

import com.sqlexplan.model.enums.SqlValidateErrorTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * SQL 校验结果。
 */
@Data
@Schema(description = "SQL 校验结果")
public class SqlValidateRes {

  @Schema(description = "是否通过全部已执行校验层")
  private Boolean valid;

  @Schema(description = "错误类型；通过时为 null")
  private SqlValidateErrorTypeEnum errorType;

  @Schema(description = "人类可读说明")
  private String message;

  @Schema(description = "出错行号（1 起）")
  private Integer line;

  @Schema(description = "出错列号（1 起）")
  private Integer column;

  @Schema(description = "出错结束行号")
  private Integer endLine;

  @Schema(description = "出错结束列号")
  private Integer endColumn;

  @Schema(description = "出错 token 或片段")
  private String token;

  @Schema(description = "MySQL 错误码（语义层）")
  private Integer mysqlErrorCode;

  @Schema(description = "修复建议，如关键字拼写、相近表名")
  private List<String> suggestions = new ArrayList<>();

  @Schema(description = "语句类型，如 SELECT")
  private String statementType;

  @Schema(description = "解析得到的物理表名列表")
  private List<String> involvedTables = new ArrayList<>();
}
