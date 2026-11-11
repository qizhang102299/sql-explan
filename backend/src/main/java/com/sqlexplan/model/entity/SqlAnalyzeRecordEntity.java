package com.sqlexplan.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * SQL 分析历史实体。
 */
@Data
@TableName("sql_analyze_record")
public class SqlAnalyzeRecordEntity {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long datasourceId;

  private String sqlText;

  private String sqlFingerprint;

  private String resultJson;

  private Long costMs;

  private Long createdTime;
}
