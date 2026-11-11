package com.sqlexplan.util;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

/**
 * MySQL 执行期语法/语义错误码与文案映射。
 */
public final class MySqlSemanticErrorUtil {

  private static final Pattern TABLE_NOT_EXIST_PATTERN =
      Pattern.compile("Table '([^']+)' doesn't exist", Pattern.CASE_INSENSITIVE);

  private static final Pattern UNKNOWN_COLUMN_PATTERN =
      Pattern.compile("Unknown column '([^']+)'", Pattern.CASE_INSENSITIVE);

  private static final Pattern FUNCTION_NOT_EXIST_PATTERN =
      Pattern.compile("FUNCTION ([^ ]+) does not exist", Pattern.CASE_INSENSITIVE);

  private MySqlSemanticErrorUtil() {}

  /**
   * 将 SQLException 映射为友好说明与建议。
   *
   * @param ex           SQL 异常
   * @param tableNames   当前库表名（用于相近名建议）
   * @param columnNames  当前表列名（可为空）
   * @return 映射结果
   */
  public static SemanticErrorOutcome map(
      SQLException ex,
      List<String> tableNames,
      List<String> columnNames) {
    int code = ex.getErrorCode();
    String msg = ex.getMessage() != null ? ex.getMessage() : "数据库执行错误";
    List<String> suggestions = new ArrayList<>();
    String friendly;
    switch (code) {
      case 1146 -> {
        friendly = "表不存在";
        Matcher m = TABLE_NOT_EXIST_PATTERN.matcher(msg);
        if (m.find()) {
          String full = m.group(1);
          String table = full.contains(".") ? full.substring(full.indexOf('.') + 1) : full;
          friendly = "表不存在：" + table;
          suggestSimilar(table, tableNames, suggestions, "你是否想查询");
        }
      }
      case 1054 -> {
        friendly = "列不存在";
        Matcher m = UNKNOWN_COLUMN_PATTERN.matcher(msg);
        if (m.find()) {
          String col = m.group(1);
          friendly = "列不存在：" + col;
          suggestSimilar(col, columnNames, suggestions, "你是否想使用列");
        }
      }
      case 1064 -> friendly = "MySQL 语法错误：" + shorten(msg);
      case 1052 -> friendly = "列名歧义：" + shorten(msg);
      case 1305 -> {
        friendly = "函数不存在";
        Matcher m = FUNCTION_NOT_EXIST_PATTERN.matcher(msg);
        if (m.find()) {
          friendly = "函数不存在：" + m.group(1);
        }
      }
      default -> friendly = shorten(msg);
    }
    return new SemanticErrorOutcome(code, friendly, suggestions);
  }

  /**
   * 从 DataAccessException 链中提取 SQLException。
   *
   * @param throwable 根因链
   * @return SQLException 或 null
   */
  public static SQLException findSqlException(Throwable throwable) {
    Throwable cur = throwable;
    while (cur != null) {
      if (cur instanceof SQLException sqlEx) {
        return sqlEx;
      }
      cur = cur.getCause();
    }
    return null;
  }

  private static void suggestSimilar(
      String name,
      List<String> candidates,
      List<String> suggestions,
      String prefix) {
    if (candidates == null || candidates.isEmpty() || !StringUtils.hasText(name)) {
      return;
    }
    String best = null;
    int bestDist = Integer.MAX_VALUE;
    for (String c : candidates) {
      int d = levenshtein(name.toLowerCase(Locale.ROOT), c.toLowerCase(Locale.ROOT));
      if (d < bestDist) {
        bestDist = d;
        best = c;
      }
    }
    if (best != null && bestDist <= Math.max(3, name.length() / 2)) {
      suggestions.add(prefix + " " + best);
    }
  }

  private static String shorten(String msg) {
    if (msg == null) {
      return "";
    }
    int nl = msg.indexOf('\n');
    return nl > 0 ? msg.substring(0, nl).trim() : msg.trim();
  }

  private static int levenshtein(String a, String b) {
    int[][] dp = new int[a.length() + 1][b.length() + 1];
    for (int i = 0; i <= a.length(); i++) {
      dp[i][0] = i;
    }
    for (int j = 0; j <= b.length(); j++) {
      dp[0][j] = j;
    }
    for (int i = 1; i <= a.length(); i++) {
      for (int j = 1; j <= b.length(); j++) {
        int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
        dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
      }
    }
    return dp[a.length()][b.length()];
  }

  /**
   * 语义错误映射结果。
   */
  public record SemanticErrorOutcome(int mysqlErrorCode, String message, List<String> suggestions) {}
}
