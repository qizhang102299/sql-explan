package com.sqlexplan.service;

import com.sqlexplan.model.req.SqlAnalyzeReq;
import com.sqlexplan.model.req.SqlHistoryDetailReq;
import com.sqlexplan.model.req.SqlHistoryPageReq;
import com.sqlexplan.model.res.SqlAnalyzeRes;
import com.sqlexplan.model.res.SqlHistoryPageRes;

/**
 * SQL 分析与历史记录服务。
 */
public interface SqlAnalyzeService {

  /**
   * 执行 SQL 综合分析并落库。
   *
   * @param req 分析请求
   * @return 分析结果
   */
  SqlAnalyzeRes analyze(SqlAnalyzeReq req);

  /**
   * 分页查询分析历史。
   *
   * @param req 分页请求
   * @return 分页结果
   */
  SqlHistoryPageRes historyPage(SqlHistoryPageReq req);

  /**
   * 查询历史详情。
   *
   * @param req 详情请求
   * @return 完整分析结果
   */
  SqlAnalyzeRes historyDetail(SqlHistoryDetailReq req);
}
