package com.sqlexplan.service;

import com.sqlexplan.model.req.DataSourceDeleteReq;
import com.sqlexplan.model.req.DataSourceDetailReq;
import com.sqlexplan.model.req.DataSourcePageReq;
import com.sqlexplan.model.req.DataSourceSaveReq;
import com.sqlexplan.model.req.DataSourceTestReq;
import com.sqlexplan.model.res.DataSourcePageRes;
import com.sqlexplan.model.res.DataSourceRes;
import com.sqlexplan.model.res.DataSourceTestRes;

/**
 * 目标数据源配置服务。
 */
public interface DataSourceService {

  /**
   * 新增或更新数据源。
   *
   * @param req 保存请求
   * @return 数据源信息（不含密码）
   */
  DataSourceRes save(DataSourceSaveReq req);

  /**
   * 删除数据源。
   *
   * @param req 删除请求
   */
  void delete(DataSourceDeleteReq req);

  /**
   * 查询数据源详情。
   *
   * @param req 详情请求
   * @return 数据源信息
   */
  DataSourceRes detail(DataSourceDetailReq req);

  /**
   * 分页查询数据源。
   *
   * @param req 分页请求
   * @return 分页结果
   */
  DataSourcePageRes page(DataSourcePageReq req);

  /**
   * 测试数据源连通性。
   *
   * @param req 测试请求
   * @return 测试结果
   */
  DataSourceTestRes testConnection(DataSourceTestReq req);
}
