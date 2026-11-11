package com.sqlexplan.constant;

/**
 * 元数据查询相关常量。
 */
public final class SchemaMetaConstants {

  public static final String QUERY_COLUMNS = """
      SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_DEFAULT,
             COLUMN_COMMENT, ORDINAL_POSITION
      FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = ? AND TABLE_NAME IN (%s)
      ORDER BY TABLE_NAME, ORDINAL_POSITION
      """;

  public static final String QUERY_TABLE_ROWS = """
      SELECT TABLE_NAME, TABLE_ROWS
      FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE' AND TABLE_NAME IN (%s)
      """;

  public static final String QUERY_STATISTICS = """
      SELECT TABLE_NAME, INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME,
             CARDINALITY, INDEX_TYPE
      FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = ? AND TABLE_NAME IN (%s)
      ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX
      """;

  public static final String TABLE_NAME_PATTERN = "^[A-Za-z0-9_$]+$";

  private SchemaMetaConstants() {
  }
}
