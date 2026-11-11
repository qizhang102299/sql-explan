package com.sqlexplan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sqlexplan.manager.TargetJdbcResolver;
import com.sqlexplan.manager.TargetJdbcResolver.TargetConnectionContext;
import com.sqlexplan.manager.browser.DbObjectBrowser;
import com.sqlexplan.manager.browser.DbObjectBrowserRegistry;
import com.sqlexplan.model.enums.DbRoutineTypeEnum;
import com.sqlexplan.model.req.DbObjectListReq;
import com.sqlexplan.model.req.DbObjectRoutineDetailReq;
import com.sqlexplan.model.req.DbObjectTableDetailReq;
import com.sqlexplan.model.req.DbObjectViewDetailReq;
import com.sqlexplan.model.res.dbobject.DbObjectListRes;
import com.sqlexplan.model.res.dbobject.DbRoutineDetailRes;
import com.sqlexplan.model.res.dbobject.DbTableDetailRes;
import com.sqlexplan.model.res.dbobject.DbViewDetailRes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@link DbObjectBrowserService} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class DbObjectBrowserServiceTest {

  private static final Long DATA_SOURCE_ID = 9L;

  private static final String DB_NAME = "demo";

  @Mock
  private TargetJdbcResolver targetJdbcResolver;

  @Mock
  private DbObjectBrowserRegistry dbObjectBrowserRegistry;

  @Mock
  private DbObjectBrowser dbObjectBrowser;

  @Mock
  private JdbcTemplate jdbcTemplate;

  private DbObjectBrowserService service;

  @BeforeEach
  void setUp() {
    service = new DbObjectBrowserService(targetJdbcResolver, dbObjectBrowserRegistry);
    when(dbObjectBrowserRegistry.getBrowser()).thenReturn(dbObjectBrowser);

    TargetConnectionContext context = new TargetConnectionContext();
    context.setDataSourceId(DATA_SOURCE_ID);
    context.setJdbcTemplate(jdbcTemplate);
    context.setDbName(DB_NAME);
    when(targetJdbcResolver.resolve(DATA_SOURCE_ID)).thenReturn(context);
  }

  @Test
  void listObjectsDelegatesToBrowser() {
    DbObjectListReq req = new DbObjectListReq();
    req.setDataSourceId(DATA_SOURCE_ID);
    DbObjectListRes expected = new DbObjectListRes();
    when(dbObjectBrowser.listObjects(jdbcTemplate, DB_NAME)).thenReturn(expected);

    assertThat(service.listObjects(req)).isSameAs(expected);
    verify(dbObjectBrowser).listObjects(jdbcTemplate, DB_NAME);
  }

  @Test
  void getTableDetailDelegatesToBrowser() {
    DbObjectTableDetailReq req = new DbObjectTableDetailReq();
    req.setDataSourceId(DATA_SOURCE_ID);
    req.setTableName("orders");
    DbTableDetailRes expected = new DbTableDetailRes();
    when(dbObjectBrowser.getTableDetail(jdbcTemplate, DB_NAME, "orders")).thenReturn(expected);

    assertThat(service.getTableDetail(req)).isSameAs(expected);
  }

  @Test
  void getViewDetailDelegatesToBrowser() {
    DbObjectViewDetailReq req = new DbObjectViewDetailReq();
    req.setDataSourceId(DATA_SOURCE_ID);
    req.setViewName("v_users");
    DbViewDetailRes expected = new DbViewDetailRes();
    when(dbObjectBrowser.getViewDetail(jdbcTemplate, DB_NAME, "v_users")).thenReturn(expected);

    assertThat(service.getViewDetail(req)).isSameAs(expected);
  }

  @Test
  void getRoutineDetailDelegatesToBrowser() {
    DbObjectRoutineDetailReq req = new DbObjectRoutineDetailReq();
    req.setDataSourceId(DATA_SOURCE_ID);
    req.setRoutineName("sp_demo");
    req.setRoutineType(DbRoutineTypeEnum.PROCEDURE);
    DbRoutineDetailRes expected = new DbRoutineDetailRes();
    when(dbObjectBrowser.getRoutineDetail(jdbcTemplate, DB_NAME, "sp_demo", DbRoutineTypeEnum.PROCEDURE))
        .thenReturn(expected);

    assertThat(service.getRoutineDetail(req)).isSameAs(expected);
  }
}
