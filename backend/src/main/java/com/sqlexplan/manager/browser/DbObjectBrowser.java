package com.sqlexplan.manager.browser;

import com.sqlexplan.model.enums.DbRoutineTypeEnum;
import com.sqlexplan.model.res.dbobject.DbObjectListRes;
import com.sqlexplan.model.res.dbobject.DbRoutineDetailRes;
import com.sqlexplan.model.res.dbobject.DbTableDetailRes;
import com.sqlexplan.model.res.dbobject.DbViewDetailRes;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 目标库对象浏览器抽象（按数据库方言实现）。
 */
public interface DbObjectBrowser {

  /**
   * 列举当前 schema 下的表、视图与例程。
   *
   * @param jdbcTemplate 只读连接
   * @param schemaName   数据库名
   * @return 对象列表
   */
  DbObjectListRes listObjects(JdbcTemplate jdbcTemplate, String schemaName);

  /**
   * 查询表结构详情。
   *
   * @param jdbcTemplate 只读连接
   * @param schemaName   数据库名
   * @param tableName    表名
   * @return 表详情
   */
  DbTableDetailRes getTableDetail(JdbcTemplate jdbcTemplate, String schemaName, String tableName);

  /**
   * 查询视图详情。
   *
   * @param jdbcTemplate 只读连接
   * @param schemaName   数据库名
   * @param viewName     视图名
   * @return 视图详情
   */
  DbViewDetailRes getViewDetail(JdbcTemplate jdbcTemplate, String schemaName, String viewName);

  /**
   * 查询存储过程或函数详情。
   *
   * @param jdbcTemplate 只读连接
   * @param schemaName   数据库名
   * @param routineName  例程名
   * @param routineType  例程类型
   * @return 例程详情
   */
  DbRoutineDetailRes getRoutineDetail(
      JdbcTemplate jdbcTemplate,
      String schemaName,
      String routineName,
      DbRoutineTypeEnum routineType);
}
