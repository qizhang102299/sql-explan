package com.sqlexplan.constant;

/**
 * SQL 规则改写相关常量。
 */
public final class SqlRewriterConstants {

  public static final String SELECT_DISTINCT_PATTERN = "(?i)\\bSELECT\\s+DISTINCT\\b";

  public static final int DEFERRED_JOIN_INNER_LIMIT = 20;

  private SqlRewriterConstants() {
  }
}
