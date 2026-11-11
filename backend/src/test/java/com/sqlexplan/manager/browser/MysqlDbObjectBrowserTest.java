package com.sqlexplan.manager.browser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sqlexplan.constant.DbObjectBrowserConstants;
import com.sqlexplan.exception.BizException;
import com.sqlexplan.model.enums.DbRoutineTypeEnum;
import com.sqlexplan.model.res.dbobject.DbObjectListRes;
import com.sqlexplan.model.res.dbobject.DbTableDetailRes;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;

/**
 * {@link MysqlDbObjectBrowser} 单元测试（Mock JdbcTemplate）。
 */
@ExtendWith(MockitoExtension.class)
class MysqlDbObjectBrowserTest {

  private static final String SCHEMA = "demo";

  private MysqlDbObjectBrowser browser;

  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    browser = new MysqlDbObjectBrowser();
    jdbcTemplate = mock(JdbcTemplate.class);
  }

  @Test
  void listObjectsAggregatesTablesViewsRoutines() {
    when(jdbcTemplate.query(eq(DbObjectBrowserConstants.LIST_TABLES), any(RowMapper.class), eq(SCHEMA)))
        .thenReturn(List.of());
    when(jdbcTemplate.query(eq(DbObjectBrowserConstants.LIST_VIEWS), any(RowMapper.class), eq(SCHEMA)))
        .thenReturn(List.of());
    when(jdbcTemplate.query(eq(DbObjectBrowserConstants.LIST_ROUTINES), any(RowMapper.class), eq(SCHEMA)))
        .thenReturn(List.of());

    DbObjectListRes res = browser.listObjects(jdbcTemplate, SCHEMA);
    assertThat(res.getTables()).isEmpty();
    assertThat(res.getViews()).isEmpty();
    assertThat(res.getRoutines()).isEmpty();
  }

  @Test
  void getTableDetailRejectsMissingTable() {
    when(jdbcTemplate.query(eq(DbObjectBrowserConstants.EXISTS_TABLE), any(RowMapper.class), eq(SCHEMA), eq("missing")))
        .thenReturn(Collections.emptyList());

    assertThatThrownBy(() -> browser.getTableDetail(jdbcTemplate, SCHEMA, "missing"))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("表不存在");
  }

  @Test
  void getTableDetailLoadsDdlAfterExistenceCheck() throws SQLException {
    when(jdbcTemplate.query(eq(DbObjectBrowserConstants.EXISTS_TABLE), any(RowMapper.class), eq(SCHEMA), eq("users")))
        .thenReturn(List.of(1));
    when(jdbcTemplate.queryForObject(eq(DbObjectBrowserConstants.TABLE_BASIC), any(RowMapper.class), eq(SCHEMA), eq("users")))
        .thenAnswer(invocation -> {
          RowMapper<?> mapper = invocation.getArgument(1);
          ResultSet rs = mockBasicTableRow();
          return mapper.mapRow(rs, 0);
        });
    when(jdbcTemplate.queryForObject(eq(DbObjectBrowserConstants.CHARSET_BY_COLLATION), eq(String.class), anyString()))
        .thenReturn("utf8mb4");
    when(jdbcTemplate.query(eq(DbObjectBrowserConstants.TABLE_COLUMNS), any(RowMapper.class), eq(SCHEMA), eq("users")))
        .thenReturn(List.of());
    doAnswer(invocation -> null)
        .when(jdbcTemplate)
        .query(eq(DbObjectBrowserConstants.TABLE_INDEXES), any(RowCallbackHandler.class), eq(SCHEMA), eq("users"));
    doAnswer(invocation -> null)
        .when(jdbcTemplate)
        .query(eq(DbObjectBrowserConstants.TABLE_FOREIGN_KEYS), any(RowCallbackHandler.class), eq(SCHEMA), eq("users"));
    when(jdbcTemplate.query(eq("SHOW CREATE TABLE `users`"), any(ResultSetExtractor.class)))
        .thenAnswer(invocation -> {
          ResultSetExtractor<?> extractor = invocation.getArgument(1);
          ResultSet rs = mock(ResultSet.class);
          when(rs.next()).thenReturn(true);
          when(rs.getString(2)).thenReturn("CREATE TABLE `users` (id bigint)");
          return extractor.extractData(rs);
        });

    DbTableDetailRes detail = browser.getTableDetail(jdbcTemplate, SCHEMA, "users");
    assertThat(detail.getBasic().getTableName()).isEqualTo("users");
    assertThat(detail.getCreateTableDdl()).contains("CREATE TABLE");
  }

  @Test
  void getRoutineDetailRejectsInvalidName() {
    assertThatThrownBy(() -> browser.getRoutineDetail(jdbcTemplate, SCHEMA, "bad-name;", DbRoutineTypeEnum.PROCEDURE))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("非法");
  }

  private static ResultSet mockBasicTableRow() throws SQLException {
    ResultSet rs = mock(ResultSet.class);
    when(rs.getString("ENGINE")).thenReturn("InnoDB");
    when(rs.getLong("TABLE_ROWS")).thenReturn(100L);
    when(rs.wasNull()).thenReturn(false);
    when(rs.getLong("DATA_LENGTH")).thenReturn(16384L);
    when(rs.getLong("INDEX_LENGTH")).thenReturn(0L);
    when(rs.getString("TABLE_COMMENT")).thenReturn("");
    when(rs.getTimestamp("CREATE_TIME")).thenReturn(new Timestamp(1_700_000_000_000L));
    when(rs.getString("TABLE_COLLATION")).thenReturn("utf8mb4_0900_ai_ci");
    return rs;
  }
}
