package com.sqlexplan.util;

import com.sqlexplan.constant.SqlFingerprintConstants;
import com.sqlexplan.exception.BizException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

/**
 * SQL 参数化与指纹（哈希）工具。
 */
public final class SqlFingerprintUtil {

  private static final Pattern STRING_LITERAL_PATTERN =
      Pattern.compile("'(?:''|[^'])*'");

  private static final Pattern NUMBER_LITERAL_PATTERN =
      Pattern.compile("(?<![A-Za-z0-9_.])\\d+(?:\\.\\d+)?(?![A-Za-z0-9_.])");

  private SqlFingerprintUtil() {
  }

  /**
   * 将 SQL 中的字面量替换为占位符并压缩空白。
   *
   * @param sql 原始 SQL
   * @return 参数化后的 SQL
   */
  public static String parameterize(String sql) {
    if (!StringUtils.hasText(sql)) {
      return "";
    }
    String trimmed = sql.trim().replaceAll("\\s+", " ");
    Statements statements;
    try {
      statements = CCJSqlParserUtil.parseStatements(trimmed);
    } catch (JSQLParserException ex) {
      throw new BizException("SQL 参数化失败: " + ex.getMessage());
    }
    if (statements.getStatements().size() != 1) {
      throw new BizException("SQL 参数化仅支持单条语句");
    }
    if (statements.getStatements().get(0) == null) {
      throw new BizException("SQL 参数化失败: 空语句");
    }
    String normalized = statementDeParseNormalize(trimmed);
    String maskedStrings = STRING_LITERAL_PATTERN.matcher(normalized).replaceAll(
        SqlFingerprintConstants.PARAM_PLACEHOLDER);
    return NUMBER_LITERAL_PATTERN.matcher(maskedStrings).replaceAll(
        SqlFingerprintConstants.PARAM_PLACEHOLDER).trim();
  }

  private static String statementDeParseNormalize(String trimmed) {
    try {
      Statement statement = CCJSqlParserUtil.parse(trimmed);
      return statement.toString().replaceAll("\\s+", " ").trim();
    } catch (JSQLParserException ex) {
      return trimmed;
    }
  }

  /**
   * 计算参数化 SQL 的 SHA-256 指纹。
   *
   * @param sql 原始 SQL
   * @return 十六进制指纹
   */
  public static String fingerprint(String sql) {
    String parameterized = parameterize(sql);
    try {
      MessageDigest digest = MessageDigest.getInstance(SqlFingerprintConstants.HASH_ALGORITHM);
      byte[] hash = digest.digest(parameterized.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException ex) {
      throw new BizException("不支持的哈希算法");
    }
  }

}
