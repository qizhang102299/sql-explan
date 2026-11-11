package com.sqlexplan.util;

import com.sqlexplan.constant.SchemaMetaConstants;
import com.sqlexplan.exception.BizException;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

/**
 * 数据库对象名合法性校验与反引号转义（防注入）。
 */
public final class DbObjectIdentifierUtil {

  private static final Pattern OBJECT_NAME_VALIDATOR =
      Pattern.compile(SchemaMetaConstants.TABLE_NAME_PATTERN);

  private DbObjectIdentifierUtil() {
  }

  /**
   * 校验并规范化对象名（去掉 schema 前缀）。
   *
   * @param name  原始名称
   * @param label 对象类型描述（用于错误提示）
   * @return 纯对象名
   */
  public static String requireValidName(String name, String label) {
    if (!StringUtils.hasText(name)) {
      throw new BizException(label + "不能为空");
    }
    String plain = name.trim();
    int dot = plain.lastIndexOf('.');
    if (dot >= 0) {
      plain = plain.substring(dot + 1);
    }
    if (!OBJECT_NAME_VALIDATOR.matcher(plain).matches()) {
      throw new BizException("非法" + label + ": " + plain);
    }
    return plain;
  }

  /**
   * MySQL 反引号转义标识符。
   *
   * @param identifier 已校验的纯对象名
   * @return 可安全拼接的标识符片段（含反引号）
   */
  public static String escapeIdentifier(String identifier) {
    return "`" + identifier.replace("`", "``") + "`";
  }
}
