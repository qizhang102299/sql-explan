package com.sqlexplan.constant;

import java.util.Locale;
import java.util.Set;

/**
 * 索引建议规则常量。
 */
public final class IndexAdvisorConstants {

  public static final Set<String> EQUALITY_OPERATORS = Set.of("=", "<=>", "IN", "NOT IN");

  public static final Set<String> RANGE_OPERATORS = Set.of(">", "<", ">=", "<=", "!=", "<>");

  public static final String OPERATOR_LIKE = "LIKE";

  public static final String OPERATOR_BETWEEN = "BETWEEN";

  public static final String INDEX_NAME_PREFIX = "idx_";

  public static final int MAX_INDEX_NAME_LENGTH = 64;

  private IndexAdvisorConstants() {
  }

  /**
   * 判断谓词运算符是否属于等值类。
   *
   * @param operator 运算符
   * @return 是否为等值类
   */
  public static boolean isEqualityOperator(String operator) {
    if (operator == null) {
      return false;
    }
    return EQUALITY_OPERATORS.contains(operator.toUpperCase(Locale.ROOT));
  }

  /**
   * 判断谓词运算符是否属于范围类。
   *
   * @param operator 运算符
   * @return 是否为范围类
   */
  public static boolean isRangeOperator(String operator) {
    if (operator == null) {
      return false;
    }
    String upper = operator.toUpperCase(Locale.ROOT);
    if (RANGE_OPERATORS.contains(upper)) {
      return true;
    }
    return OPERATOR_LIKE.equalsIgnoreCase(operator) || OPERATOR_BETWEEN.equalsIgnoreCase(operator);
  }
}
