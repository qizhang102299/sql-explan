package com.sqlexplan.service;

import com.sqlexplan.model.req.SqlValidateReq;
import com.sqlexplan.model.res.SqlValidateRes;

/**
 * SQL 语法校验服务。
 */
public interface SqlValidateService {

  /**
   * 校验 SQL。
   *
   * @param req 请求
   * @return 校验结果
   */
  SqlValidateRes validate(SqlValidateReq req);
}
