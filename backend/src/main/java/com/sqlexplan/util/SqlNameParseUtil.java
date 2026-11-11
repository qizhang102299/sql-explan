package com.sqlexplan.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

/**
 * SQL 标识符与列引用解析工具。
 */
public final class SqlNameParseUtil {

  private static final Pattern QUALIFIED_COLUMN =
      Pattern.compile("^[`]?([\\w$]+)[`]?\\.[`]?([\\w$]+)[`]?$");

  private static final Pattern SIMPLE_IDENTIFIER = Pattern.compile("^[`]?([\\w$]+)[`]?$");

  private static final Pattern JOIN_EQUALITY =
      Pattern.compile(
          "[`]?([\\w$]+)[`]?\\.[`]?([\\w$]+)[`]?\\s*=\\s*[`]?([\\w$]+)[`]?\\.[`]?([\\w$]+)[`]?",
          Pattern.CASE_INSENSITIVE);

  private SqlNameParseUtil() {
  }

  /**
   * 解析带表限定或未限定的列引用。
   *
   * @param expression 列或表达式文本
   * @return 长度为 2 的数组 [tableOrAlias, column]，无法解析时元素可能为 null
   */
  public static String[] parseQualifiedColumn(String expression) {
    if (!StringUtils.hasText(expression)) {
      return new String[] {null, null};
    }
    String trimmed = expression.trim();
    Matcher qualified = QUALIFIED_COLUMN.matcher(trimmed);
    if (qualified.matches()) {
      return new String[] {qualified.group(1), qualified.group(2)};
    }
    Matcher simple = SIMPLE_IDENTIFIER.matcher(trimmed);
    if (simple.matches()) {
      return new String[] {null, simple.group(1)};
    }
    return new String[] {null, null};
  }

  /**
   * 规范化标识符（去反引号、小写比较用原文保留）。
   *
   * @param name 标识符
   * @return 去除反引号后的名称
   */
  public static String stripBackticks(String name) {
    if (name == null) {
      return null;
    }
    return name.replace("`", "");
  }

  /**
   * 从 JOIN ON 条件中提取等值关联列（按左表.列记录）。
   *
   * @param onCondition ON 条件原文
   * @return 列名对列表，每项为 [leftTable, leftColumn, rightTable, rightColumn]
   */
  public static java.util.List<String[]> parseJoinEqualities(String onCondition) {
    java.util.List<String[]> pairs = new java.util.ArrayList<>();
    if (!StringUtils.hasText(onCondition)) {
      return pairs;
    }
    Matcher matcher = JOIN_EQUALITY.matcher(onCondition);
    while (matcher.find()) {
      pairs.add(
          new String[] {
            matcher.group(1),
            matcher.group(2),
            matcher.group(3),
            matcher.group(4)
          });
    }
    return pairs;
  }

  /**
   * 判断 LIKE 模式是否以通配符开头（无法利用 BTree 索引）。
   *
   * @param rightExpression 右侧 LIKE 模式字面量
   * @return 是否前导模糊
   */
  public static boolean isLeadingWildcardLike(String rightExpression) {
    if (!StringUtils.hasText(rightExpression)) {
      return false;
    }
    String text = rightExpression.trim();
    if (text.length() >= 2
        && ((text.startsWith("'") && text.endsWith("'")) || (text.startsWith("\"") && text.endsWith("\"")))) {
      text = text.substring(1, text.length() - 1);
    }
    return text.startsWith("%");
  }

  /**
   * 比较两标识符是否相同（忽略大小写与反引号）。
   *
   * @param a 标识符 a
   * @param b 标识符 b
   * @return 是否相同
   */
  public static boolean sameIdentifier(String a, String b) {
    if (a == null || b == null) {
      return false;
    }
    return stripBackticks(a).equalsIgnoreCase(stripBackticks(b));
  }

  /**
   * 生成索引 DDL 中的安全列名引用。
   *
   * @param columnName 列名
   * @return 列名（含特殊字符时加反引号）
   */
  public static String quoteColumnIfNeeded(String columnName) {
    if (columnName == null) {
      return "";
    }
    if (columnName.chars().allMatch(ch -> Character.isLetterOrDigit(ch) || ch == '_')) {
      return columnName;
    }
    return "`" + stripBackticks(columnName) + "`";
  }

  /**
   * 将表名规范化为 DDL 片段。
   *
   * @param tableName 表名
   * @return 表名 DDL 片段
   */
  public static String quoteTableName(String tableName) {
    return quoteColumnIfNeeded(stripBackticks(tableName));
  }
}
