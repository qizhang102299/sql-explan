package com.sqlexplan.util;

import com.sqlexplan.exception.BizException;
import com.sqlexplan.model.domain.ColumnMeta;
import com.sqlexplan.model.domain.IndexColumnMeta;
import com.sqlexplan.model.domain.IndexMeta;
import com.sqlexplan.model.domain.TableSchemaMeta;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * 根据表结构元数据计算版本指纹（供 LLM 结果缓存键使用）。
 */
public final class SchemaMetaVersionUtil {

  private static final String HASH_ALGORITHM = "SHA-256";

  private SchemaMetaVersionUtil() {
  }

  /**
   * 计算多表 schema 的版本哈希（列与索引定义变化会导致版本变化）。
   *
   * @param tables 表元数据列表
   * @return 十六进制版本字符串
   */
  public static String computeVersion(List<TableSchemaMeta> tables) {
    if (tables == null || tables.isEmpty()) {
      return "empty";
    }
    List<TableSchemaMeta> sorted = tables.stream()
        .sorted(Comparator.comparing(
            t -> t.getTableName() == null ? "" : t.getTableName().toLowerCase(Locale.ROOT)))
        .toList();
    StringBuilder payload = new StringBuilder(4096);
    for (TableSchemaMeta table : sorted) {
      payload.append("t:").append(nullToEmpty(table.getTableName())).append('|');
      payload.append("rows:").append(table.getEstimatedRows() == null ? 0 : table.getEstimatedRows());
      payload.append('|');
      appendColumns(payload, table.getColumns());
      appendIndexes(payload, table.getIndexes());
      payload.append(';');
    }
    return sha256Hex(payload.toString());
  }

  private static void appendColumns(StringBuilder payload, List<ColumnMeta> columns) {
    if (columns == null || columns.isEmpty()) {
      return;
    }
    List<ColumnMeta> sorted = columns.stream()
        .sorted(Comparator.comparing(c -> nullToEmpty(c.getColumnName())))
        .toList();
    payload.append("cols:");
    for (ColumnMeta col : sorted) {
      payload.append(nullToEmpty(col.getColumnName())).append(':')
          .append(nullToEmpty(col.getDataType())).append(',');
    }
    payload.append('|');
  }

  private static void appendIndexes(StringBuilder payload, List<IndexMeta> indexes) {
    if (indexes == null || indexes.isEmpty()) {
      return;
    }
    List<IndexMeta> sorted = indexes.stream()
        .sorted(Comparator.comparing(i -> nullToEmpty(i.getIndexName())))
        .toList();
    payload.append("idx:");
    for (IndexMeta index : sorted) {
      payload.append(nullToEmpty(index.getIndexName())).append(':');
      if (index.getColumns() != null) {
        List<IndexColumnMeta> cols = index.getColumns().stream()
            .sorted(Comparator.comparing(c -> c.getSeqInIndex() == null ? 0 : c.getSeqInIndex()))
            .toList();
        for (IndexColumnMeta col : cols) {
          payload.append(nullToEmpty(col.getColumnName())).append(',');
        }
      }
      payload.append(';');
    }
    payload.append('|');
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private static String sha256Hex(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException ex) {
      throw new BizException("不支持的哈希算法");
    }
  }
}
