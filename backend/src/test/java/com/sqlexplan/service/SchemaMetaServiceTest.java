package com.sqlexplan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sqlexplan.config.SqlexplanProperties;
import com.sqlexplan.model.domain.TableSchemaMeta;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

/**
 * {@link SchemaMetaService} 单元测试（Mock JdbcTemplate，不依赖 Docker）。
 */
@ExtendWith(MockitoExtension.class)
class SchemaMetaServiceTest {

  private SchemaMetaService schemaMetaService;

  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    SqlexplanProperties properties = new SqlexplanProperties();
    properties.getSchemaMeta().setCacheTtlSeconds(300);
    schemaMetaService = new SchemaMetaService(properties);
    jdbcTemplate = mock(JdbcTemplate.class);
    stubInformationSchemaQueries();
  }

  @Test
  void loadTableMetadataFromInformationSchema() {
    List<TableSchemaMeta> metaList = schemaMetaService.loadTableMetadata(
        1L,
        "meta_test",
        jdbcTemplate,
        List.of("users"));
    assertThat(metaList).hasSize(1);
    TableSchemaMeta meta = metaList.get(0);
    assertThat(meta.getTableName()).isEqualTo("users");
    assertThat(meta.getEstimatedRows()).isEqualTo(2L);
    assertThat(meta.getColumns()).extracting("columnName")
        .containsExactly("id", "name", "status");
    assertThat(meta.getIndexes()).hasSize(2);
    assertThat(meta.getIndexes().stream().anyMatch(i -> "idx_status".equals(i.getIndexName()))).isTrue();
    assertThat(meta.getIndexes().stream()
        .flatMap(i -> i.getColumns().stream())
        .anyMatch(c -> "status".equals(c.getColumnName()) && Long.valueOf(2L).equals(c.getCardinality())))
        .isTrue();

    schemaMetaService.loadTableMetadata(1L, "meta_test", jdbcTemplate, List.of("users"));
    ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate, times(3)).query(sqlCaptor.capture(), any(RowCallbackHandler.class), eq("meta_test"));
  }

  @Test
  void evictCacheByDataSourceId() {
    schemaMetaService.loadTableMetadata(2L, "db1", jdbcTemplate, List.of("users"));
    schemaMetaService.evictByDataSourceId(2L);
    schemaMetaService.loadTableMetadata(2L, "db1", jdbcTemplate, List.of("users"));
    verify(jdbcTemplate, times(6)).query(anyString(), any(RowCallbackHandler.class), eq("db1"));
  }

  private void stubInformationSchemaQueries() {
    doAnswer(invocation -> {
          String sql = invocation.getArgument(0);
          RowCallbackHandler handler = invocation.getArgument(1);
          try {
            if (sql.contains("information_schema.COLUMNS")) {
              emitColumnRow(handler, "id", "bigint", 1);
              emitColumnRow(handler, "name", "varchar", 2);
              emitColumnRow(handler, "status", "tinyint", 3);
            } else if (sql.contains("information_schema.TABLES")) {
              emitTableRow(handler);
            } else if (sql.contains("information_schema.STATISTICS")) {
              emitIndexRow(handler, "PRIMARY", 0, "id", 1, 2L);
              emitIndexRow(handler, "idx_status", 1, "status", 1, 2L);
            }
          } catch (SQLException ex) {
            throw new RuntimeException(ex);
          }
          return null;
        }).when(jdbcTemplate).query(anyString(), any(RowCallbackHandler.class), any());
  }

  private static void emitColumnRow(
      RowCallbackHandler handler,
      String columnName,
      String dataType,
      int ordinal)
      throws SQLException {
    ResultSet rs = mock(ResultSet.class);
    when(rs.getString("TABLE_NAME")).thenReturn("users");
    when(rs.getString("COLUMN_NAME")).thenReturn(columnName);
    when(rs.getString("DATA_TYPE")).thenReturn(dataType);
    when(rs.getString("IS_NULLABLE")).thenReturn("NO");
    when(rs.getString("COLUMN_DEFAULT")).thenReturn(null);
    when(rs.getString("COLUMN_COMMENT")).thenReturn(null);
    when(rs.getInt("ORDINAL_POSITION")).thenReturn(ordinal);
    handler.processRow(rs);
  }

  private static void emitTableRow(RowCallbackHandler handler) throws SQLException {
    ResultSet rs = mock(ResultSet.class);
    when(rs.getString("TABLE_NAME")).thenReturn("users");
    when(rs.getLong("TABLE_ROWS")).thenReturn(2L);
    when(rs.wasNull()).thenReturn(false);
    handler.processRow(rs);
  }

  private static void emitIndexRow(
      RowCallbackHandler handler,
      String indexName,
      int nonUnique,
      String columnName,
      int seq,
      long cardinality)
      throws SQLException {
    ResultSet rs = mock(ResultSet.class);
    when(rs.getString("TABLE_NAME")).thenReturn("users");
    when(rs.getString("INDEX_NAME")).thenReturn(indexName);
    when(rs.getInt("NON_UNIQUE")).thenReturn(nonUnique);
    when(rs.getInt("SEQ_IN_INDEX")).thenReturn(seq);
    when(rs.getString("COLUMN_NAME")).thenReturn(columnName);
    when(rs.getLong("CARDINALITY")).thenReturn(cardinality);
    when(rs.wasNull()).thenReturn(false);
    when(rs.getString("INDEX_TYPE")).thenReturn("BTREE");
    handler.processRow(rs);
  }
}
