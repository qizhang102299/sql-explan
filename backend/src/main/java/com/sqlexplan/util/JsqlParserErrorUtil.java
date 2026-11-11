package com.sqlexplan.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.sf.jsqlparser.JSQLParserException;
import org.springframework.util.StringUtils;

/**
 * 从 JSqlParser 异常与 SQL 文本提取语法错误位置与友好文案。
 */
public final class JsqlParserErrorUtil {

  private static final Pattern LINE_COLUMN_PATTERN =
      Pattern.compile("at line (\\d+), column (\\d+)", Pattern.CASE_INSENSITIVE);

  private static final Pattern UNEXPECTED_TOKEN_PATTERN =
      Pattern.compile("Encountered unexpected token:\\s*(?:(?:\"([^\"]+)\")|(\\S+))", Pattern.CASE_INSENSITIVE);

  private static final Pattern EXPECTING_ITEM_PATTERN = Pattern.compile("^\\s*\"([^\"]+)\"\\s*$");

  private static final int MAX_SUGGESTION_TOKENS = 5;

  private static final int KEYWORD_SUGGEST_MAX_DISTANCE = 2;

  private static final List<String> SQL_KEYWORDS = List.of(
      "SELECT", "FROM", "WHERE", "GROUP", "BY", "ORDER", "HAVING", "LIMIT", "OFFSET",
      "JOIN", "INNER", "LEFT", "RIGHT", "OUTER", "ON", "UNION", "ALL", "DISTINCT",
      "INSERT", "UPDATE", "DELETE", "CREATE", "ALTER", "DROP", "WITH", "AS", "AND", "OR", "NOT");

  private JsqlParserErrorUtil() {}

  /**
   * 根据解析异常构建友好语法说明。
   *
   * @param sql 原始 SQL
   * @param ex  解析异常
   * @return 行、列、token、消息与 suggestions
   */
  public static SyntaxParseOutcome describeSyntaxFailure(String sql, JSQLParserException ex) {
    String raw = ex.getMessage() != null ? ex.getMessage() : "SQL 解析失败";
    int line = 1;
    int column = 1;
    Matcher lc = LINE_COLUMN_PATTERN.matcher(raw);
    if (lc.find()) {
      line = Integer.parseInt(lc.group(1));
      column = Integer.parseInt(lc.group(2));
    }
    String token = null;
    Matcher ut = UNEXPECTED_TOKEN_PATTERN.matcher(raw);
    if (ut.find()) {
      token = ut.group(1) != null ? ut.group(1) : ut.group(2);
    }
    List<String> expecting = extractExpectingTokens(raw);
    List<String> suggestions = buildParserSuggestions(expecting, token, sql);
    String message = buildSyntaxMessage(line, column, token, expecting);
    return new SyntaxParseOutcome(line, column, line, column, token, message, suggestions);
  }

  /**
   * 启发式检测括号、引号是否平衡，并给出出错位置。
   *
   * @param sql SQL
   * @return 若不平衡则返回带位置的说明，否则 null
   */
  public static LocatedSyntaxError locateUnbalanced(String sql) {
    java.util.ArrayDeque<Integer> openParens = new java.util.ArrayDeque<>();
    int singleStart = -1;
    int doubleStart = -1;
    for (int i = 0; i < sql.length(); i++) {
      char c = sql.charAt(i);
      if (c == '\'' && doubleStart < 0) {
        singleStart = singleStart < 0 ? i : -1;
        continue;
      }
      if (c == '"' && singleStart < 0) {
        doubleStart = doubleStart < 0 ? i : -1;
        continue;
      }
      if (singleStart >= 0 || doubleStart >= 0) {
        continue;
      }
      if (c == '(') {
        openParens.push(i);
      } else if (c == ')') {
        if (openParens.isEmpty()) {
          return located(sql, i, i + 1, "存在语法错误：多余的右括号");
        }
        openParens.pop();
      }
    }
    if (singleStart >= 0) {
      return located(sql, singleStart, sql.length(), "存在语法错误：单引号字符串未闭合");
    }
    if (doubleStart >= 0) {
      return located(sql, doubleStart, sql.length(), "存在语法错误：双引号字符串未闭合");
    }
    if (!openParens.isEmpty()) {
      int pos = openParens.peek();
      return located(sql, pos, pos + 1, "存在语法错误：括号未闭合，此处的 '(' 没有对应的 ')'");
    }
    return null;
  }

  /**
   * 启发式检测括号是否平衡。
   *
   * @param sql SQL
   * @return 若不平衡则返回说明，否则 null
   */
  public static String detectUnbalancedParentheses(String sql) {
    LocatedSyntaxError err = locateUnbalanced(sql);
    return err == null ? null : err.message();
  }

  /**
   * 检测 SELECT 列表末尾多余逗号，并给出逗号位置。
   *
   * @param sql SQL
   * @return 带位置的说明或 null
   */
  public static LocatedSyntaxError locateTrailingCommaInSelectList(String sql) {
    String upper = sql.toUpperCase(Locale.ROOT);
    int fromIdx = indexOfKeywordOutsideQuotes(sql, upper, "FROM");
    if (fromIdx < 0) {
      return null;
    }
    int i = fromIdx - 1;
    while (i >= 0 && Character.isWhitespace(sql.charAt(i))) {
      i--;
    }
    if (i >= 0 && sql.charAt(i) == ',') {
      return located(sql, i, i + 1, "存在语法错误：SELECT 列表末尾有多余的逗号");
    }
    return null;
  }

  /**
   * 检测 SELECT 列表末尾多余逗号。
   *
   * @param sql SQL
   * @return 说明或 null
   */
  public static String detectTrailingCommaInSelectList(String sql) {
    LocatedSyntaxError err = locateTrailingCommaInSelectList(sql);
    return err == null ? null : err.message();
  }

  /**
   * 检测 WHERE / GROUP BY / ORDER BY / HAVING 后缺少表达式，并给出子句关键字位置。
   *
   * @param sql SQL
   * @return 带位置的说明或 null
   */
  public static LocatedSyntaxError locateClauseMissingExpression(String sql) {
    String[] clauses = {"WHERE", "GROUP BY", "ORDER BY", "HAVING"};
    String upper = sql.toUpperCase(Locale.ROOT);
    for (String clause : clauses) {
      int idx = indexOfKeywordOutsideQuotes(sql, upper, clause);
      if (idx < 0) {
        continue;
      }
      int after = idx + clause.length();
      String tail = sql.substring(after).trim();
      boolean missing = tail.isEmpty() || tail.startsWith(";");
      if (!missing) {
        missing = startsWithNextClause(tail.toUpperCase(Locale.ROOT));
      }
      if (missing) {
        return located(sql, idx, after, "存在语法错误：" + clause + " 后缺少表达式");
      }
    }
    return null;
  }

  /**
   * 检测 WHERE / GROUP BY / ORDER BY / HAVING 后缺少表达式。
   *
   * @param sql SQL
   * @return 说明或 null
   */
  public static String detectClauseMissingExpression(String sql) {
    LocatedSyntaxError err = locateClauseMissingExpression(sql);
    return err == null ? null : err.message();
  }

  private static LocatedSyntaxError located(String sql, int startOffset, int endOffset, String message) {
    int[] start = offsetToLineColumn(sql, startOffset);
    int[] end = offsetToLineColumn(sql, Math.max(endOffset, startOffset + 1));
    return new LocatedSyntaxError(start[0], start[1], end[0], end[1], message);
  }

  /**
   * 将字节偏移转为行列（1 起）。
   *
   * @param sql    SQL
   * @param offset 偏移
   * @return [line, column]
   */
  public static int[] offsetToLineColumn(String sql, int offset) {
    int line = 1;
    int column = 1;
    for (int i = 0; i < offset && i < sql.length(); i++) {
      if (sql.charAt(i) == '\n') {
        line++;
        column = 1;
      } else {
        column++;
      }
    }
    return new int[] {line, column};
  }

  private static boolean startsWithNextClause(String tailUpper) {
    return tailUpper.startsWith("GROUP ")
        || tailUpper.startsWith("ORDER ")
        || tailUpper.startsWith("HAVING ")
        || tailUpper.startsWith("LIMIT ")
        || tailUpper.startsWith("UNION ")
        || tailUpper.startsWith(";");
  }

  private static int indexOfKeywordOutsideQuotes(String sql, String upper, String keyword) {
    int from = 0;
    while (from < sql.length()) {
      int idx = upper.indexOf(keyword, from);
      if (idx < 0) {
        return -1;
      }
      if (isWordBoundary(upper, idx, keyword.length())) {
        return idx;
      }
      from = idx + keyword.length();
    }
    return -1;
  }

  private static boolean isWordBoundary(String upper, int idx, int len) {
    boolean before = idx == 0 || !Character.isLetterOrDigit(upper.charAt(idx - 1));
    int afterIdx = idx + len;
    boolean after = afterIdx >= upper.length() || !Character.isLetterOrDigit(upper.charAt(afterIdx));
    return before && after;
  }

  private static List<String> extractExpectingTokens(String raw) {
    List<String> items = new ArrayList<>();
    int start = raw.indexOf("Was expecting");
    if (start < 0) {
      return items;
    }
    String[] lines = raw.substring(start).split("\\R");
    for (String line : lines) {
      Matcher m = EXPECTING_ITEM_PATTERN.matcher(line.trim());
      if (m.matches()) {
        String token = m.group(1);
        if (!"<EOF>".equals(token) && !"<ST_SEMICOLON>".equals(token)) {
          items.add(humanizeExpectation(token));
        }
        if (items.size() >= MAX_SUGGESTION_TOKENS) {
          break;
        }
      }
    }
    return items;
  }

  private static String humanizeExpectation(String token) {
    if (token.startsWith("<K_") && token.endsWith(">")) {
      return token.substring(3, token.length() - 1);
    }
    if (token.startsWith("<") && token.endsWith(">")) {
      return token.substring(1, token.length() - 1).toLowerCase(Locale.ROOT);
    }
    return token;
  }

  private static List<String> buildParserSuggestions(List<String> expecting, String token, String sql) {
    List<String> out = new ArrayList<>();
    String kw = token == null ? null : suggestKeywordIfTypo(token);
    if (kw != null) {
      out.add(kw);
    } else if (!expecting.isEmpty()) {
      out.add("可能缺少：" + String.join("、", new LinkedHashSet<>(expecting)));
    }
    return out;
  }

  private static String suggestKeywordIfTypo(String token) {
    if (!StringUtils.hasText(token)) {
      return null;
    }
    String upper = token.toUpperCase(Locale.ROOT);
    if (SQL_KEYWORDS.contains(upper)) {
      return null;
    }
    String best = null;
    int bestDist = Integer.MAX_VALUE;
    for (String kw : SQL_KEYWORDS) {
      int d = levenshtein(upper, kw);
      if (d < bestDist && d <= KEYWORD_SUGGEST_MAX_DISTANCE) {
        bestDist = d;
        best = kw;
      }
    }
    if (best != null) {
      return "你是否想写 " + best;
    }
    return null;
  }

  private static String buildSyntaxMessage(int line, int column, String token, List<String> expecting) {
    StringBuilder sb = new StringBuilder();
    sb.append("第 ").append(line).append(" 行第 ").append(column).append(" 列附近存在语法错误");
    if (StringUtils.hasText(token)) {
      sb.append("：意外的 ").append(token);
    } else if (!expecting.isEmpty()) {
      sb.append("：可能缺少 ").append(expecting.get(0));
    }
    return sb.toString();
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
   * 语法解析失败时的结构化片段。
   */
  public record SyntaxParseOutcome(
      int line,
      int column,
      int endLine,
      int endColumn,
      String token,
      String message,
      List<String> suggestions) {}

  /**
   * 带位置的启发式语法错误。
   */
  public record LocatedSyntaxError(int line, int column, int endLine, int endColumn, String message) {}
}
