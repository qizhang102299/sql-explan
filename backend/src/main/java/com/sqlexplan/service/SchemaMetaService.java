package com.sqlexplan.service;

import com.sqlexplan.config.SqlexplanProperties;
import com.sqlexplan.constant.SchemaMetaConstants;
import com.sqlexplan.exception.BizException;
import com.sqlexplan.model.domain.ColumnMeta;
import com.sqlexplan.model.domain.IndexColumnMeta;
import com.sqlexplan.model.domain.IndexMeta;
import com.sqlexplan.model.domain.TableSchemaMeta;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * 从目标库 information_schema 拉取表结构、索引与统计信息（带短期内存缓存）。
 */
@Slf4j
@Service
public class SchemaMetaService {

  private static final Pattern TABLE_NAME_VALIDATOR =
      Pattern.compile(SchemaMetaConstants.TABLE_NAME_PATTERN);

  private final SqlexplanProperties sqlexplanProperties;

  private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

  public SchemaMetaService(SqlexplanProperties sqlexplanProperties) {
    this.sqlexplanProperties = sqlexplanProperties;
  }

  /**
   * 批量加载多张表的元数据（同一数据源、同一 schema 下结果会被短期缓存）。
   *
   * @param dataSourceId 数据源 ID（用于缓存键，可为 null 表示不走跨请求缓存）
   * @param schemaName   数据库名（TABLE_SCHEMA）
   * @param jdbcTemplate 目标库只读 JdbcTemplate
   * @param tableNames   物理表名列表
   * @return 表元数据列表
   */
  public List<TableSchemaMeta> loadTableMetadata(
      Long dataSourceId,
      String schemaName,
      JdbcTemplate jdbcTemplate,
      List<String> tableNames) {
    if (!StringUtils.hasText(schemaName)) {
      throw new BizException("schema 名称不能为空");
    }
    if (jdbcTemplate == null) {
      throw new BizException("JdbcTemplate 不能为空");
    }
    if (CollectionUtils.isEmpty(tableNames)) {
      return new ArrayList<>();
    }
    List<String> normalized = normalizeTableNames(tableNames);
    String cacheKey = buildCacheKey(dataSourceId, schemaName, normalized);
    long ttlMs = sqlexplanProperties.getSchemaMeta().getCacheTtlSeconds() * 1000L;
    long now = System.currentTimeMillis();
    CacheEntry cached = cache.get(cacheKey);
    if (cached != null && cached.expiresAtMs > now) {
      log.debug("命中表元数据缓存, key={}", cacheKey);
      return deepCopy(cached.data);
    }
    List<TableSchemaMeta> loaded = queryFromDatabase(schemaName, jdbcTemplate, normalized);
    cache.put(cacheKey, new CacheEntry(loaded, now + ttlMs));
    return deepCopy(loaded);
  }

  /**
   * 数据源配置变更或手动刷新时清除该数据源下所有元数据缓存。
   *
   * @param dataSourceId 数据源 ID
   */
  public void evictByDataSourceId(Long dataSourceId) {
    if (dataSourceId == null) {
      return;
    }
    String prefix = dataSourceId + ":";
    cache.keySet().removeIf(key -> key.startsWith(prefix));
    log.info("已清除数据源元数据缓存, dataSourceId={}", dataSourceId);
  }

  private List<TableSchemaMeta> queryFromDatabase(
      String schemaName,
      JdbcTemplate jdbcTemplate,
      List<String> tableNames) {
    String inClause = tableNames.stream()
        .map(name -> "'" + name.replace("'", "''") + "'")
        .collect(Collectors.joining(","));
    Map<String, TableSchemaMeta> metaMap = new LinkedHashMap<>();
    for (String name : tableNames) {
      TableSchemaMeta meta = new TableSchemaMeta();
      meta.setTableName(name);
      metaMap.put(name, meta);
    }
    String columnsSql = String.format(SchemaMetaConstants.QUERY_COLUMNS, inClause);
    jdbcTemplate.query(
        columnsSql,
        rs -> {
          String table = rs.getString("TABLE_NAME");
          TableSchemaMeta meta = metaMap.get(table);
          if (meta == null) {
            return;
          }
          ColumnMeta column = new ColumnMeta();
          column.setColumnName(rs.getString("COLUMN_NAME"));
          column.setDataType(rs.getString("DATA_TYPE"));
          column.setNullable("YES".equalsIgnoreCase(rs.getString("IS_NULLABLE")));
          column.setColumnDefault(rs.getString("COLUMN_DEFAULT"));
          column.setComment(rs.getString("COLUMN_COMMENT"));
          column.setOrdinalPosition(rs.getInt("ORDINAL_POSITION"));
          meta.getColumns().add(column);
        },
        schemaName);
    String rowsSql = String.format(SchemaMetaConstants.QUERY_TABLE_ROWS, inClause);
    jdbcTemplate.query(
        rowsSql,
        rs -> {
          String table = rs.getString("TABLE_NAME");
          TableSchemaMeta meta = metaMap.get(table);
          if (meta == null) {
            return;
          }
          long rows = rs.getLong("TABLE_ROWS");
          if (!rs.wasNull()) {
            meta.setEstimatedRows(rows);
          }
        },
        schemaName);
    String statsSql = String.format(SchemaMetaConstants.QUERY_STATISTICS, inClause);
    Map<String, IndexMeta> indexByKey = new LinkedHashMap<>();
    jdbcTemplate.query(
        statsSql,
        rs -> {
          String table = rs.getString("TABLE_NAME");
          TableSchemaMeta meta = metaMap.get(table);
          if (meta == null) {
            return;
          }
          String indexName = rs.getString("INDEX_NAME");
          String indexKey = table + "#" + indexName;
          int nonUnique = rs.getInt("NON_UNIQUE");
          String indexType = rs.getString("INDEX_TYPE");
          IndexMeta indexMeta = indexByKey.computeIfAbsent(indexKey, k -> {
            IndexMeta idx = new IndexMeta();
            idx.setIndexName(indexName);
            idx.setUnique(nonUnique == 0);
            idx.setIndexType(indexType);
            meta.getIndexes().add(idx);
            return idx;
          });
          IndexColumnMeta col = new IndexColumnMeta();
          col.setColumnName(rs.getString("COLUMN_NAME"));
          col.setSeqInIndex(rs.getInt("SEQ_IN_INDEX"));
          long card = rs.getLong("CARDINALITY");
          if (!rs.wasNull()) {
            col.setCardinality(card);
          }
          indexMeta.getColumns().add(col);
        },
        schemaName);
    for (TableSchemaMeta meta : metaMap.values()) {
      meta.getIndexes().sort(Comparator.comparing(IndexMeta::getIndexName));
      meta.getColumns().sort(Comparator.comparing(ColumnMeta::getOrdinalPosition));
    }
    return new ArrayList<>(metaMap.values());
  }

  private List<String> normalizeTableNames(List<String> tableNames) {
    List<String> result = new ArrayList<>(tableNames.size());
    for (String name : tableNames) {
      if (!StringUtils.hasText(name)) {
        continue;
      }
      String plain = name;
      int dot = name.lastIndexOf('.');
      if (dot >= 0) {
        plain = name.substring(dot + 1);
      }
      if (!TABLE_NAME_VALIDATOR.matcher(plain).matches()) {
        throw new BizException("非法表名: " + plain);
      }
      if (!result.contains(plain)) {
        result.add(plain);
      }
    }
    result.sort(String::compareTo);
    return result;
  }

  private String buildCacheKey(Long dataSourceId, String schemaName, List<String> tableNames) {
    String dsPart = dataSourceId != null ? String.valueOf(dataSourceId) : "ephemeral";
    return dsPart + ":" + schemaName + ":" + String.join(",", tableNames);
  }

  private List<TableSchemaMeta> deepCopy(List<TableSchemaMeta> source) {
    List<TableSchemaMeta> copy = new ArrayList<>(source.size());
    for (TableSchemaMeta item : source) {
      TableSchemaMeta meta = new TableSchemaMeta();
      meta.setTableName(item.getTableName());
      meta.setEstimatedRows(item.getEstimatedRows());
      meta.getColumns().addAll(item.getColumns());
      for (IndexMeta idx : item.getIndexes()) {
        IndexMeta indexCopy = new IndexMeta();
        indexCopy.setIndexName(idx.getIndexName());
        indexCopy.setUnique(idx.getUnique());
        indexCopy.setIndexType(idx.getIndexType());
        indexCopy.getColumns().addAll(idx.getColumns());
        meta.getIndexes().add(indexCopy);
      }
      copy.add(meta);
    }
    return copy;
  }

  private static final class CacheEntry {

    private final List<TableSchemaMeta> data;

    private final long expiresAtMs;

    private CacheEntry(List<TableSchemaMeta> data, long expiresAtMs) {
      this.data = data;
      this.expiresAtMs = expiresAtMs;
    }
  }
}
