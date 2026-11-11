package com.sqlexplan.constant;

/**
 * 目标库 JDBC 相关常量。
 */
public final class TargetDataSourceConstants {

  private TargetDataSourceConstants() {
  }

  public static final String DRIVER_CLASS_NAME = "com.mysql.cj.jdbc.Driver";

  public static final String JDBC_URL_TEMPLATE =
      "jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
          + "&useSSL=false&allowPublicKeyRetrieval=true";

  public static final String VALIDATION_QUERY = "SELECT 1";
}
