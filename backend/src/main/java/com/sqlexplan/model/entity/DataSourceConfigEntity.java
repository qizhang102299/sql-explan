package com.sqlexplan.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 目标数据源配置实体，对应表 datasource_config。
 */
@Data
@TableName("datasource_config")
public class DataSourceConfigEntity {

  @TableId(type = IdType.AUTO)
  private Long id;

  private String name;

  private String host;

  private Integer port;

  private String dbName;

  private String username;

  private String passwordEncrypted;

  private Long createdTime;

  private Long updatedTime;
}
