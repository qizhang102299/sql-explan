package com.sqlexplan.util;

/**
 * SQL 文本规范化工具。
 */
public final class SqlTextUtil {

  private SqlTextUtil() {
  }

  /**
   * 去除 SQL 首尾空白以及末尾的分号。
   *
   * <p>仅处理末尾的分号，语句中间的分号保持不变，
   * 因此多语句（如 a; b）仍会被安全校验拒绝。
   *
   * @param sql 原始 SQL，可为 null
   * @return 规范化后的 SQL，入参为 null 时返回空字符串
   */
  public static String stripTrailingSemicolons(String sql) {
    if (sql == null) {
      return "";
    }
    int end = sql.length();
    while (end > 0) {
      char c = sql.charAt(end - 1);
      if (Character.isWhitespace(c) || c == ';') {
        end--;
      } else {
        break;
      }
    }
    return sql.substring(0, end).trim();
  }
}
