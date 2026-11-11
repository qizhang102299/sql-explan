package com.sqlexplan.constant;

import java.util.Set;

/**
 * SQL 安全校验相关常量。
 */
public final class SqlSafetyConstants {

  private SqlSafetyConstants() {
  }

  public static final Set<String> DANGEROUS_FUNCTION_NAMES = Set.of(
      "SLEEP",
      "BENCHMARK",
      "LOAD_FILE");

  public static final String REJECT_EMPTY = "SQL 不能为空";

  public static final String REJECT_MULTI_STATEMENT = "仅允许单条 SQL 语句";

  public static final String REJECT_NOT_SELECT = "仅允许 SELECT 查询语句";

  public static final String REJECT_PARSE_FAILED = "SQL 解析失败";

  public static final String REJECT_FOR_UPDATE = "禁止使用 SELECT ... FOR UPDATE";

  public static final String REJECT_LOCK_IN_SHARE_MODE = "禁止使用 LOCK IN SHARE MODE";

  public static final String REJECT_INTO_OUTFILE = "禁止使用 INTO OUTFILE";

  public static final String REJECT_INTO_DUMPFILE = "禁止使用 INTO DUMPFILE";

  public static final String REJECT_INTO_VARIABLE = "禁止使用 SELECT ... INTO 变量赋值";

  public static final String REJECT_DANGEROUS_FUNCTION = "禁止使用危险函数: ";

  public static final String REJECT_KEYWORD_PATTERN = "SQL 包含禁止的关键字或写法: ";
}
