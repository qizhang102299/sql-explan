package com.sqlexplan.service;

import com.sqlexplan.manager.TargetJdbcResolver;
import com.sqlexplan.manager.TargetJdbcResolver.TargetConnectionContext;
import com.sqlexplan.manager.browser.DbObjectBrowserRegistry;
import com.sqlexplan.model.req.DbObjectListReq;
import com.sqlexplan.model.req.DbObjectRoutineDetailReq;
import com.sqlexplan.model.req.DbObjectTableDetailReq;
import com.sqlexplan.model.req.DbObjectViewDetailReq;
import com.sqlexplan.model.res.dbobject.DbObjectListRes;
import com.sqlexplan.model.res.dbobject.DbRoutineDetailRes;
import com.sqlexplan.model.res.dbobject.DbTableDetailRes;
import com.sqlexplan.model.res.dbobject.DbViewDetailRes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 数据源对象浏览器服务。
 */
@Service
@RequiredArgsConstructor
public class DbObjectBrowserService {

  private final TargetJdbcResolver targetJdbcResolver;

  private final DbObjectBrowserRegistry dbObjectBrowserRegistry;

  /**
   * 列举数据源下的表、视图与例程。
   *
   * @param req 列表请求
   * @return 对象列表
   */
  public DbObjectListRes listObjects(DbObjectListReq req) {
    TargetConnectionContext context = targetJdbcResolver.resolve(req.getDataSourceId());
    return dbObjectBrowserRegistry.getBrowser()
        .listObjects(context.getJdbcTemplate(), context.getDbName());
  }

  /**
   * 查询表结构详情。
   *
   * @param req 表详情请求
   * @return 表详情
   */
  public DbTableDetailRes getTableDetail(DbObjectTableDetailReq req) {
    TargetConnectionContext context = targetJdbcResolver.resolve(req.getDataSourceId());
    return dbObjectBrowserRegistry.getBrowser()
        .getTableDetail(context.getJdbcTemplate(), context.getDbName(), req.getTableName());
  }

  /**
   * 查询视图详情。
   *
   * @param req 视图详情请求
   * @return 视图详情
   */
  public DbViewDetailRes getViewDetail(DbObjectViewDetailReq req) {
    TargetConnectionContext context = targetJdbcResolver.resolve(req.getDataSourceId());
    return dbObjectBrowserRegistry.getBrowser()
        .getViewDetail(context.getJdbcTemplate(), context.getDbName(), req.getViewName());
  }

  /**
   * 查询存储过程或函数详情。
   *
   * @param req 例程详情请求
   * @return 例程详情
   */
  public DbRoutineDetailRes getRoutineDetail(DbObjectRoutineDetailReq req) {
    TargetConnectionContext context = targetJdbcResolver.resolve(req.getDataSourceId());
    return dbObjectBrowserRegistry.getBrowser()
        .getRoutineDetail(
            context.getJdbcTemplate(),
            context.getDbName(),
            req.getRoutineName(),
            req.getRoutineType());
  }
}
