package com.sqlexplan.constant;

/**
 * EXPLAIN 与计划解读相关常量。
 */
public final class ExplainConstants {

  public static final String EXPLAIN_PREFIX = "EXPLAIN FORMAT=JSON ";

  public static final String JSON_QUERY_BLOCK = "query_block";

  public static final String JSON_TABLE = "table";

  public static final String JSON_NESTED_LOOP = "nested_loop";

  public static final String JSON_ORDERING_OPERATION = "ordering_operation";

  public static final String JSON_GROUPING_OPERATION = "grouping_operation";

  public static final String JSON_DUPLICATES_REMOVAL = "duplicates_removal";

  public static final String JSON_MATERIALIZED_FROM_SUBQUERY = "materialized_from_subquery";

  public static final String JSON_USING_FILESORT = "using_filesort";

  public static final String JSON_USING_TEMPORARY_TABLE = "using_temporary_table";

  public static final String JSON_ACCESS_TYPE_ALL = "ALL";

  public static final String EXTRA_USING_FILESORT = "Using filesort";

  public static final String EXTRA_USING_TEMPORARY = "Using temporary table";

  public static final int DEFAULT_LARGE_SCAN_ROWS_THRESHOLD = 100_000;

  public static final int DEFAULT_DEEP_PAGINATION_OFFSET_THRESHOLD = 10_000;

  public static final String PARSE_FAILED = "EXPLAIN JSON 解析失败";

  private ExplainConstants() {
  }
}
