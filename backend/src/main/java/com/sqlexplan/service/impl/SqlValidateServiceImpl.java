package com.sqlexplan.service.impl;

import com.sqlexplan.manager.TargetJdbcResolver;
import com.sqlexplan.model.req.SqlValidateReq;
import com.sqlexplan.model.res.SqlValidateRes;
import com.sqlexplan.service.SqlSyntaxValidator;
import com.sqlexplan.service.SqlValidateService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * SQL 校验服务实现。
 */
@Service
@RequiredArgsConstructor
public class SqlValidateServiceImpl implements SqlValidateService {

  private final SqlSyntaxValidator sqlSyntaxValidator;

  private final TargetJdbcResolver targetJdbcResolver;

  @Override
  public SqlValidateRes validate(SqlValidateReq req) {
    if (req.getDataSourceId() == null) {
      return sqlSyntaxValidator.validate(req.getSql());
    }
    TargetJdbcResolver.TargetConnectionContext ctx = targetJdbcResolver.resolve(req.getDataSourceId());
    JdbcTemplate jdbc = ctx.getJdbcTemplate();
    return sqlSyntaxValidator.validate(req.getSql(), jdbc, ctx.getDbName());
  }
}
