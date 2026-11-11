package com.sqlexplan.constant;

/**
 * 基准测试相关常量。
 */
public final class BenchmarkConstants {

  private BenchmarkConstants() {
  }

  public static final int DEFAULT_ITERATIONS = 5;

  public static final int MAX_ITERATIONS = 20;

  public static final int MIN_ITERATIONS = 1;

  /**
   * 临时索引名前缀，测完必定删除。
   */
  public static final String TEMP_INDEX_NAME_PREFIX = "sqlexplan_tmp_";

  public static final String INDEX_BENEFIT_EXPLAIN_ONLY_NOTE = "未开启实库索引验证，收益为基于 EXPLAIN 的预估";

  public static final String EQUIVALENCE_MISMATCH_WARNING = "优化 SQL 与原 SQL 结果不等价，请谨慎采纳";

  private static final String TEMP_INDEX_DDL_SUBQUERY_ALIAS = "_sqlexplan_bench";

  public static String countSubqueryAlias() {
    return TEMP_INDEX_DDL_SUBQUERY_ALIAS;
  }
}
