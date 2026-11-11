package com.sqlexplan.constant;

import java.util.Locale;
import java.util.Set;

/**
 * SQL 解析相关常量。
 */
public final class SqlParseConstants {

  public static final Set<String> AGGREGATE_FUNCTION_NAMES = Set.of(
      "COUNT", "SUM", "AVG", "MIN", "MAX", "GROUP_CONCAT", "STD", "STDDEV", "VARIANCE");

  public static final String SUBQUERY_LOCATION_FROM = "FROM";

  public static final String SUBQUERY_LOCATION_WHERE = "WHERE";

  public static final String SUBQUERY_LOCATION_SELECT = "SELECT";

  public static final String SUBQUERY_LOCATION_HAVING = "HAVING";

  public static final String REJECT_PARSE_FAILED = "SQL 解析失败";

  private SqlParseConstants() {
  }

  /**
   * 判断函数名是否为常见聚合函数。
   *
   * @param functionName 函数名
   * @return 是否为聚合函数
   */
  public static boolean isAggregateFunction(String functionName) {
    if (functionName == null) {
      return false;
    }
    return AGGREGATE_FUNCTION_NAMES.contains(functionName.toUpperCase(Locale.ROOT));
  }
}
