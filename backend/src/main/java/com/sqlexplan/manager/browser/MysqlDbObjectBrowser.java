package com.sqlexplan.manager.browser;

import com.sqlexplan.constant.DbObjectBrowserConstants;
import com.sqlexplan.exception.BizException;
import com.sqlexplan.model.enums.DbRoutineTypeEnum;
import com.sqlexplan.model.res.dbobject.DbObjectListRes;
import com.sqlexplan.model.res.dbobject.DbRoutineDetailRes;
import com.sqlexplan.model.res.dbobject.DbRoutineParameterRes;
import com.sqlexplan.model.res.dbobject.DbRoutineSummaryRes;
import com.sqlexplan.model.res.dbobject.DbTableBasicRes;
import com.sqlexplan.model.res.dbobject.DbTableColumnRes;
import com.sqlexplan.model.res.dbobject.DbTableDetailRes;
import com.sqlexplan.model.res.dbobject.DbTableForeignKeyRes;
import com.sqlexplan.model.res.dbobject.DbTableIndexColumnRes;
import com.sqlexplan.model.res.dbobject.DbTableIndexRes;
import com.sqlexplan.model.res.dbobject.DbTableSummaryRes;
import com.sqlexplan.model.res.dbobject.DbViewColumnRes;
import com.sqlexplan.model.res.dbobject.DbViewDetailRes;
import com.sqlexplan.model.res.dbobject.DbViewSummaryRes;
import com.sqlexplan.util.DbObjectIdentifierUtil;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * MySQL 对象浏览器实现（只读元数据查询）。
 */
@Slf4j
@Component
public class MysqlDbObjectBrowser implements DbObjectBrowser {

  @Override
  public DbObjectListRes listObjects(JdbcTemplate jdbcTemplate, String schemaName) {
    requireSchema(schemaName);
    DbObjectListRes res = new DbObjectListRes();
    res.setTables(jdbcTemplate.query(
        DbObjectBrowserConstants.LIST_TABLES,
        (rs, rowNum) -> mapTableSummary(rs),
        schemaName));
    res.setViews(jdbcTemplate.query(
        DbObjectBrowserConstants.LIST_VIEWS,
        (rs, rowNum) -> mapViewSummary(rs),
        schemaName));
    res.setRoutines(jdbcTemplate.query(
        DbObjectBrowserConstants.LIST_ROUTINES,
        (rs, rowNum) -> mapRoutineSummary(rs),
        schemaName));
    return res;
  }

  @Override
  public DbTableDetailRes getTableDetail(JdbcTemplate jdbcTemplate, String schemaName, String tableName) {
    requireSchema(schemaName);
    String plainName = DbObjectIdentifierUtil.requireValidName(tableName, "表名");
    assertTableExists(jdbcTemplate, schemaName, plainName);

    DbTableDetailRes res = new DbTableDetailRes();
    res.setBasic(loadTableBasic(jdbcTemplate, schemaName, plainName));
    res.setColumns(loadTableColumns(jdbcTemplate, schemaName, plainName));
    res.setIndexes(loadTableIndexes(jdbcTemplate, schemaName, plainName));
    res.setForeignKeys(loadForeignKeys(jdbcTemplate, schemaName, plainName));
    res.setCreateTableDdl(showCreateTable(jdbcTemplate, plainName));
    return res;
  }

  @Override
  public DbViewDetailRes getViewDetail(JdbcTemplate jdbcTemplate, String schemaName, String viewName) {
    requireSchema(schemaName);
    String plainName = DbObjectIdentifierUtil.requireValidName(viewName, "视图名");
    assertViewExists(jdbcTemplate, schemaName, plainName);

    DbViewDetailRes res = new DbViewDetailRes();
    res.setViewName(plainName);
    res.setColumns(loadViewColumns(jdbcTemplate, schemaName, plainName));
    loadViewMeta(jdbcTemplate, schemaName, plainName, res);
    res.setDefinitionSql(firstNonBlank(res.getDefinitionSql(), showCreateView(jdbcTemplate, plainName)));
    return res;
  }

  @Override
  public DbRoutineDetailRes getRoutineDetail(
      JdbcTemplate jdbcTemplate,
      String schemaName,
      String routineName,
      DbRoutineTypeEnum routineType) {
    requireSchema(schemaName);
    if (routineType == null) {
      throw new BizException("例程类型不能为空");
    }
    String plainName = DbObjectIdentifierUtil.requireValidName(routineName, "例程名");
    String routineTypeSql = routineType.name();
    assertRoutineExists(jdbcTemplate, schemaName, plainName, routineTypeSql);

    DbRoutineDetailRes res = new DbRoutineDetailRes();
    res.setRoutineName(plainName);
    res.setRoutineType(routineType);
    res.setParameters(loadRoutineParameters(jdbcTemplate, schemaName, plainName, routineType));
    loadRoutineMeta(jdbcTemplate, schemaName, plainName, routineTypeSql, res);
    res.setSourceDefinition(
        firstNonBlank(res.getSourceDefinition(), showCreateRoutine(jdbcTemplate, plainName, routineType)));
    return res;
  }

  private static void requireSchema(String schemaName) {
    if (!StringUtils.hasText(schemaName)) {
      throw new BizException("schema 名称不能为空");
    }
  }

  private void assertTableExists(JdbcTemplate jdbcTemplate, String schemaName, String tableName) {
    assertExists(
        jdbcTemplate,
        DbObjectBrowserConstants.EXISTS_TABLE,
        schemaName,
        tableName,
        "表不存在: " + tableName);
  }

  private void assertViewExists(JdbcTemplate jdbcTemplate, String schemaName, String viewName) {
    assertExists(
        jdbcTemplate,
        DbObjectBrowserConstants.EXISTS_VIEW,
        schemaName,
        viewName,
        "视图不存在: " + viewName);
  }

  private void assertRoutineExists(
      JdbcTemplate jdbcTemplate,
      String schemaName,
      String routineName,
      String routineType) {
    List<Integer> found = jdbcTemplate.query(
        DbObjectBrowserConstants.EXISTS_ROUTINE,
        (rs, rowNum) -> rs.getInt(1),
        schemaName,
        routineName,
        routineType);
    if (found.isEmpty()) {
      throw new BizException("例程不存在: " + routineName);
    }
  }

  private void assertExists(
      JdbcTemplate jdbcTemplate,
      String sql,
      String schemaName,
      String objectName,
      String notFoundMessage) {
    List<Integer> found = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getInt(1), schemaName, objectName);
    if (found.isEmpty()) {
      throw new BizException(notFoundMessage);
    }
  }

  private DbTableSummaryRes mapTableSummary(ResultSet rs) throws SQLException {
    DbTableSummaryRes item = new DbTableSummaryRes();
    item.setName(rs.getString("TABLE_NAME"));
    item.setComment(rs.getString("TABLE_COMMENT"));
    item.setEngine(rs.getString("ENGINE"));
    long rows = rs.getLong("TABLE_ROWS");
    if (!rs.wasNull()) {
      item.setEstimatedRows(rows);
    }
    item.setDataSizeBytes(sumBytes(rs.getLong("DATA_LENGTH"), rs.getLong("INDEX_LENGTH")));
    item.setCreatedTime(toEpochMs(rs.getTimestamp("CREATE_TIME")));
    item.setUpdatedTime(toEpochMs(rs.getTimestamp("UPDATE_TIME")));
    return item;
  }

  private DbViewSummaryRes mapViewSummary(ResultSet rs) throws SQLException {
    DbViewSummaryRes item = new DbViewSummaryRes();
    item.setName(rs.getString("TABLE_NAME"));
    item.setComment(rs.getString("TABLE_COMMENT"));
    item.setUpdatable("YES".equalsIgnoreCase(rs.getString("IS_UPDATABLE")));
    return item;
  }

  private DbRoutineSummaryRes mapRoutineSummary(ResultSet rs) throws SQLException {
    DbRoutineSummaryRes item = new DbRoutineSummaryRes();
    item.setName(rs.getString("ROUTINE_NAME"));
    item.setComment(rs.getString("ROUTINE_COMMENT"));
    item.setRoutineType(parseRoutineType(rs.getString("ROUTINE_TYPE")));
    item.setCreatedTime(toEpochMs(rs.getTimestamp("CREATED")));
    item.setModifiedTime(toEpochMs(rs.getTimestamp("LAST_ALTERED")));
    return item;
  }

  private DbTableBasicRes loadTableBasic(JdbcTemplate jdbcTemplate, String schemaName, String tableName) {
    try {
      return jdbcTemplate.queryForObject(
          DbObjectBrowserConstants.TABLE_BASIC,
          (rs, rowNum) -> {
            DbTableBasicRes basic = new DbTableBasicRes();
            basic.setTableName(tableName);
            basic.setEngine(rs.getString("ENGINE"));
            long rows = rs.getLong("TABLE_ROWS");
            if (!rs.wasNull()) {
              basic.setEstimatedRows(rows);
            }
            basic.setDataSizeBytes(sumBytes(rs.getLong("DATA_LENGTH"), rs.getLong("INDEX_LENGTH")));
            basic.setComment(rs.getString("TABLE_COMMENT"));
            basic.setCreatedTime(toEpochMs(rs.getTimestamp("CREATE_TIME")));
            String collation = rs.getString("TABLE_COLLATION");
            basic.setCollation(collation);
            basic.setCharset(resolveCharset(jdbcTemplate, collation));
            return basic;
          },
          schemaName,
          tableName);
    } catch (EmptyResultDataAccessException ex) {
      throw new BizException("表不存在: " + tableName);
    }
  }

  private String resolveCharset(JdbcTemplate jdbcTemplate, String collation) {
    if (!StringUtils.hasText(collation)) {
      return null;
    }
    try {
      return jdbcTemplate.queryForObject(
          DbObjectBrowserConstants.CHARSET_BY_COLLATION,
          String.class,
          collation);
    } catch (EmptyResultDataAccessException ex) {
      return null;
    }
  }

  private List<DbTableColumnRes> loadTableColumns(JdbcTemplate jdbcTemplate, String schemaName, String tableName) {
    return jdbcTemplate.query(
        DbObjectBrowserConstants.TABLE_COLUMNS,
        (rs, rowNum) -> {
          DbTableColumnRes col = new DbTableColumnRes();
          col.setOrdinalPosition(rs.getInt("ORDINAL_POSITION"));
          col.setName(rs.getString("COLUMN_NAME"));
          col.setColumnType(rs.getString("COLUMN_TYPE"));
          col.setNullable("YES".equalsIgnoreCase(rs.getString("IS_NULLABLE")));
          col.setDefaultValue(rs.getString("COLUMN_DEFAULT"));
          String key = rs.getString("COLUMN_KEY");
          if (StringUtils.hasText(key)) {
            col.setColumnKey(key);
          }
          col.setExtra(rs.getString("EXTRA"));
          col.setComment(rs.getString("COLUMN_COMMENT"));
          return col;
        },
        schemaName,
        tableName);
  }

  private List<DbTableIndexRes> loadTableIndexes(JdbcTemplate jdbcTemplate, String schemaName, String tableName) {
    Map<String, DbTableIndexRes> indexMap = new LinkedHashMap<>();
    jdbcTemplate.query(
        DbObjectBrowserConstants.TABLE_INDEXES,
        rs -> {
          try {
            String indexName = rs.getString("INDEX_NAME");
            int nonUnique = rs.getInt("NON_UNIQUE");
            String indexType = rs.getString("INDEX_TYPE");
            DbTableIndexRes index = indexMap.computeIfAbsent(indexName, k -> {
              DbTableIndexRes idx = new DbTableIndexRes();
              idx.setIndexName(indexName);
              idx.setUnique(nonUnique == 0);
              idx.setIndexType(indexType);
              return idx;
            });
            DbTableIndexColumnRes col = new DbTableIndexColumnRes();
            col.setColumnName(rs.getString("COLUMN_NAME"));
            col.setSeqInIndex(rs.getInt("SEQ_IN_INDEX"));
            long card = rs.getLong("CARDINALITY");
            if (!rs.wasNull()) {
              col.setCardinality(card);
              if (index.getCardinality() == null || card > index.getCardinality()) {
                index.setCardinality(card);
              }
            }
            index.getColumns().add(col);
          } catch (SQLException ex) {
            throw new RuntimeException(ex);
          }
        },
        schemaName,
        tableName);
    return new ArrayList<>(indexMap.values());
  }

  private List<DbTableForeignKeyRes> loadForeignKeys(JdbcTemplate jdbcTemplate, String schemaName, String tableName) {
    Map<String, DbTableForeignKeyRes> fkMap = new LinkedHashMap<>();
    jdbcTemplate.query(
        DbObjectBrowserConstants.TABLE_FOREIGN_KEYS,
        rs -> {
          try {
            String constraintName = rs.getString("CONSTRAINT_NAME");
            String referencedTable = rs.getString("REFERENCED_TABLE_NAME");
            DbTableForeignKeyRes fk = fkMap.computeIfAbsent(constraintName, k -> {
              DbTableForeignKeyRes item = new DbTableForeignKeyRes();
              item.setConstraintName(constraintName);
              item.setReferencedTableName(referencedTable);
              return item;
            });
            fk.getColumnNames().add(rs.getString("COLUMN_NAME"));
            fk.getReferencedColumnNames().add(rs.getString("REFERENCED_COLUMN_NAME"));
          } catch (SQLException ex) {
            throw new RuntimeException(ex);
          }
        },
        schemaName,
        tableName);
    return new ArrayList<>(fkMap.values());
  }

  private List<DbViewColumnRes> loadViewColumns(JdbcTemplate jdbcTemplate, String schemaName, String viewName) {
    return jdbcTemplate.query(
        DbObjectBrowserConstants.VIEW_COLUMNS,
        (rs, rowNum) -> {
          DbViewColumnRes col = new DbViewColumnRes();
          col.setOrdinalPosition(rs.getInt("ORDINAL_POSITION"));
          col.setName(rs.getString("COLUMN_NAME"));
          col.setColumnType(rs.getString("COLUMN_TYPE"));
          col.setNullable("YES".equalsIgnoreCase(rs.getString("IS_NULLABLE")));
          col.setDefaultValue(rs.getString("COLUMN_DEFAULT"));
          col.setComment(rs.getString("COLUMN_COMMENT"));
          return col;
        },
        schemaName,
        viewName);
  }

  private void loadViewMeta(
      JdbcTemplate jdbcTemplate,
      String schemaName,
      String viewName,
      DbViewDetailRes res) {
    try {
      jdbcTemplate.queryForObject(
          DbObjectBrowserConstants.VIEW_META,
          (rs, rowNum) -> {
            res.setDefinitionSql(rs.getString("VIEW_DEFINITION"));
            res.setCheckOption(rs.getString("CHECK_OPTION"));
            res.setDefiner(rs.getString("DEFINER"));
            res.setSecurityType(rs.getString("SECURITY_TYPE"));
            return null;
          },
          schemaName,
          viewName);
    } catch (EmptyResultDataAccessException ex) {
      throw new BizException("视图不存在: " + viewName);
    }
  }

  private List<DbRoutineParameterRes> loadRoutineParameters(
      JdbcTemplate jdbcTemplate,
      String schemaName,
      String routineName,
      DbRoutineTypeEnum routineType) {
    return jdbcTemplate.query(
        DbObjectBrowserConstants.ROUTINE_PARAMETERS,
        (rs, rowNum) -> {
          int ordinal = rs.getInt("ORDINAL_POSITION");
          if (ordinal == 0) {
            return null;
          }
          DbRoutineParameterRes param = new DbRoutineParameterRes();
          param.setDirection(rs.getString("PARAMETER_MODE"));
          param.setName(rs.getString("PARAMETER_NAME"));
          param.setParameterType(rs.getString("DTD_IDENTIFIER"));
          return param;
        },
        schemaName,
        buildSpecificName(routineName, routineType))
        .stream()
        .filter(item -> item != null)
        .toList();
  }

  private void loadRoutineMeta(
      JdbcTemplate jdbcTemplate,
      String schemaName,
      String routineName,
      String routineTypeSql,
      DbRoutineDetailRes res) {
    try {
      jdbcTemplate.queryForObject(
          DbObjectBrowserConstants.ROUTINE_META,
          (rs, rowNum) -> {
            if ("FUNCTION".equalsIgnoreCase(rs.getString("ROUTINE_TYPE"))) {
              res.setReturnType(rs.getString("DTD_IDENTIFIER"));
            }
            res.setSourceDefinition(rs.getString("ROUTINE_DEFINITION"));
            res.setDefiner(rs.getString("DEFINER"));
            res.setDeterministic("YES".equalsIgnoreCase(rs.getString("IS_DETERMINISTIC")));
            res.setSqlDataAccess(rs.getString("SQL_DATA_ACCESS"));
            res.setCreatedTime(toEpochMs(rs.getTimestamp("CREATED")));
            res.setModifiedTime(toEpochMs(rs.getTimestamp("LAST_ALTERED")));
            return null;
          },
          schemaName,
          routineName,
          routineTypeSql);
    } catch (EmptyResultDataAccessException ex) {
      throw new BizException("例程不存在: " + routineName);
    }
  }

  private String showCreateTable(JdbcTemplate jdbcTemplate, String tableName) {
    String sql = "SHOW CREATE TABLE " + DbObjectIdentifierUtil.escapeIdentifier(tableName);
    return jdbcTemplate.query(
        sql,
        rs -> {
          if (!rs.next()) {
            throw new BizException("无法获取建表 DDL: " + tableName);
          }
          return rs.getString(2);
        });
  }

  private String showCreateView(JdbcTemplate jdbcTemplate, String viewName) {
    String sql = "SHOW CREATE VIEW " + DbObjectIdentifierUtil.escapeIdentifier(viewName);
    return jdbcTemplate.query(
        sql,
        rs -> {
          if (!rs.next()) {
            throw new BizException("无法获取视图定义: " + viewName);
          }
          return rs.getString(2);
        });
  }

  private String showCreateRoutine(JdbcTemplate jdbcTemplate, String routineName, DbRoutineTypeEnum routineType) {
    String keyword = routineType == DbRoutineTypeEnum.FUNCTION ? "FUNCTION" : "PROCEDURE";
    String sql =
        "SHOW CREATE " + keyword + " " + DbObjectIdentifierUtil.escapeIdentifier(routineName);
    return jdbcTemplate.query(
        sql,
        rs -> {
          if (!rs.next()) {
            throw new BizException("无法获取例程定义: " + routineName);
          }
          return rs.getString(3);
        });
  }

  private static String buildSpecificName(String routineName, DbRoutineTypeEnum routineType) {
    return routineName;
  }

  private static DbRoutineTypeEnum parseRoutineType(String routineType) {
    if (!StringUtils.hasText(routineType)) {
      return DbRoutineTypeEnum.PROCEDURE;
    }
    String normalized = routineType.trim().toUpperCase(Locale.ROOT);
    if ("FUNCTION".equals(normalized)) {
      return DbRoutineTypeEnum.FUNCTION;
    }
    return DbRoutineTypeEnum.PROCEDURE;
  }

  private static Long toEpochMs(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.getTime();
  }

  private static Long sumBytes(long dataLength, long indexLength) {
    long data = dataLength;
    long index = indexLength;
    if (data < 0) {
      data = 0;
    }
    if (index < 0) {
      index = 0;
    }
    long total = data + index;
    return total > 0 ? total : null;
  }

  private static String firstNonBlank(String primary, String fallback) {
    if (StringUtils.hasText(primary)) {
      return primary;
    }
    return fallback;
  }
}
