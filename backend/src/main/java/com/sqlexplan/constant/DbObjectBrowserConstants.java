package com.sqlexplan.constant;

/**
 * 对象浏览器元数据查询 SQL 常量。
 */
public final class DbObjectBrowserConstants {

  public static final String EXISTS_TABLE = """
      SELECT 1
      FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND TABLE_TYPE = 'BASE TABLE'
      LIMIT 1
      """;

  public static final String EXISTS_VIEW = """
      SELECT 1
      FROM information_schema.VIEWS
      WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?
      LIMIT 1
      """;

  public static final String EXISTS_ROUTINE = """
      SELECT 1
      FROM information_schema.ROUTINES
      WHERE ROUTINE_SCHEMA = ? AND ROUTINE_NAME = ? AND ROUTINE_TYPE = ?
      LIMIT 1
      """;

  public static final String LIST_TABLES = """
      SELECT TABLE_NAME, TABLE_COMMENT, ENGINE, TABLE_ROWS,
             DATA_LENGTH, INDEX_LENGTH, CREATE_TIME, UPDATE_TIME
      FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE'
      ORDER BY TABLE_NAME
      """;

  public static final String LIST_VIEWS = """
      SELECT v.TABLE_NAME, t.TABLE_COMMENT, v.IS_UPDATABLE
      FROM information_schema.VIEWS v
      LEFT JOIN information_schema.TABLES t
        ON t.TABLE_SCHEMA = v.TABLE_SCHEMA AND t.TABLE_NAME = v.TABLE_NAME
      WHERE v.TABLE_SCHEMA = ?
      ORDER BY v.TABLE_NAME
      """;

  public static final String LIST_ROUTINES = """
      SELECT ROUTINE_NAME, ROUTINE_COMMENT, ROUTINE_TYPE, CREATED, LAST_ALTERED
      FROM information_schema.ROUTINES
      WHERE ROUTINE_SCHEMA = ?
      ORDER BY ROUTINE_TYPE, ROUTINE_NAME
      """;

  public static final String TABLE_BASIC = """
      SELECT ENGINE, TABLE_ROWS, DATA_LENGTH, INDEX_LENGTH, TABLE_COMMENT,
             CREATE_TIME, UPDATE_TIME, TABLE_COLLATION
      FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND TABLE_TYPE = 'BASE TABLE'
      """;

  public static final String CHARSET_BY_COLLATION = """
      SELECT CHARACTER_SET_NAME
      FROM information_schema.COLLATION_CHARACTER_SET_APPLICABILITY
      WHERE COLLATION_NAME = ?
      LIMIT 1
      """;

  public static final String TABLE_COLUMNS = """
      SELECT ORDINAL_POSITION, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT,
             COLUMN_KEY, EXTRA, COLUMN_COMMENT
      FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?
      ORDER BY ORDINAL_POSITION
      """;

  public static final String TABLE_INDEXES = """
      SELECT INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME, CARDINALITY, INDEX_TYPE
      FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?
      ORDER BY INDEX_NAME, SEQ_IN_INDEX
      """;

  public static final String TABLE_FOREIGN_KEYS = """
      SELECT CONSTRAINT_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME,
             ORDINAL_POSITION
      FROM information_schema.KEY_COLUMN_USAGE
      WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND REFERENCED_TABLE_NAME IS NOT NULL
      ORDER BY CONSTRAINT_NAME, ORDINAL_POSITION
      """;

  public static final String VIEW_COLUMNS = """
      SELECT ORDINAL_POSITION, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT,
             COLUMN_COMMENT
      FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?
      ORDER BY ORDINAL_POSITION
      """;

  public static final String VIEW_META = """
      SELECT VIEW_DEFINITION, CHECK_OPTION, DEFINER, SECURITY_TYPE
      FROM information_schema.VIEWS
      WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?
      """;

  public static final String ROUTINE_PARAMETERS = """
      SELECT ORDINAL_POSITION, PARAMETER_MODE, PARAMETER_NAME, DTD_IDENTIFIER
      FROM information_schema.PARAMETERS
      WHERE SPECIFIC_SCHEMA = ? AND SPECIFIC_NAME = ?
      ORDER BY ORDINAL_POSITION
      """;

  public static final String ROUTINE_META = """
      SELECT ROUTINE_TYPE, DTD_IDENTIFIER, ROUTINE_DEFINITION, DEFINER,
             IS_DETERMINISTIC, SQL_DATA_ACCESS, CREATED, LAST_ALTERED
      FROM information_schema.ROUTINES
      WHERE ROUTINE_SCHEMA = ? AND ROUTINE_NAME = ? AND ROUTINE_TYPE = ?
      """;

  private DbObjectBrowserConstants() {
  }
}
