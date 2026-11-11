package com.sqlexplan.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sqlexplan.exception.BizException;
import com.sqlexplan.manager.TargetJdbcResolver;
import com.sqlexplan.mapper.SqlAnalyzeRecordMapper;
import com.sqlexplan.model.entity.SqlAnalyzeRecordEntity;
import com.sqlexplan.model.req.SqlAnalyzeReq;
import com.sqlexplan.model.req.SqlHistoryDetailReq;
import com.sqlexplan.model.req.SqlHistoryPageReq;
import com.sqlexplan.model.res.SqlAnalyzeRes;
import com.sqlexplan.model.res.SqlHistoryPageRes;
import com.sqlexplan.model.res.SqlHistoryRecordSummaryRes;
import com.sqlexplan.service.AnalyzeOrchestrator;
import com.sqlexplan.service.SqlAnalyzeService;
import com.sqlexplan.util.SqlFingerprintUtil;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * SQL 分析与历史服务实现。
 */
@Service
@RequiredArgsConstructor
public class SqlAnalyzeServiceImpl implements SqlAnalyzeService {

  private static final int SQL_LIST_PREVIEW_LEN = 200;

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final TargetJdbcResolver targetJdbcResolver;

  private final AnalyzeOrchestrator analyzeOrchestrator;

  private final SqlAnalyzeRecordMapper sqlAnalyzeRecordMapper;

  @Override
  public SqlAnalyzeRes analyze(SqlAnalyzeReq req) {
    long start = System.currentTimeMillis();
    TargetJdbcResolver.TargetConnectionContext context = targetJdbcResolver.resolve(req.getDataSourceId());
    SqlAnalyzeRes res = analyzeOrchestrator.analyze(req, context);
    long costMs = System.currentTimeMillis() - start;
    res.setCreatedTime(System.currentTimeMillis());
    SqlAnalyzeRecordEntity entity = new SqlAnalyzeRecordEntity();
    entity.setDatasourceId(req.getDataSourceId());
    entity.setSqlText(req.getSql().trim());
    entity.setSqlFingerprint(SqlFingerprintUtil.fingerprint(req.getSql()));
    entity.setResultJson(serializeResult(res));
    entity.setCostMs(costMs);
    entity.setCreatedTime(res.getCreatedTime());
    sqlAnalyzeRecordMapper.insert(entity);
    res.setRecordId(entity.getId());
    return res;
  }

  @Override
  public SqlHistoryPageRes historyPage(SqlHistoryPageReq req) {
    LambdaQueryWrapper<SqlAnalyzeRecordEntity> wrapper = new LambdaQueryWrapper<>();
    if (req.getDataSourceId() != null) {
      wrapper.eq(SqlAnalyzeRecordEntity::getDatasourceId, req.getDataSourceId());
    }
    wrapper.orderByDesc(SqlAnalyzeRecordEntity::getCreatedTime);
    Page<SqlAnalyzeRecordEntity> page = new Page<>(req.getPageNum(), req.getPageSize());
    Page<SqlAnalyzeRecordEntity> result = sqlAnalyzeRecordMapper.selectPage(page, wrapper);
    SqlHistoryPageRes pageRes = new SqlHistoryPageRes();
    pageRes.setTotal(result.getTotal());
    List<SqlHistoryRecordSummaryRes> records = result.getRecords().stream()
        .map(this::toSummary)
        .collect(Collectors.toList());
    pageRes.setRecords(records);
    return pageRes;
  }

  @Override
  public SqlAnalyzeRes historyDetail(SqlHistoryDetailReq req) {
    SqlAnalyzeRecordEntity entity = sqlAnalyzeRecordMapper.selectById(req.getId());
    if (entity == null) {
      throw new BizException("分析记录不存在");
    }
    if (!StringUtils.hasText(entity.getResultJson())) {
      throw new BizException("历史结果为空");
    }
    try {
      SqlAnalyzeRes res = OBJECT_MAPPER.readValue(entity.getResultJson(), SqlAnalyzeRes.class);
      res.setRecordId(entity.getId());
      return res;
    } catch (JsonProcessingException ex) {
      throw new BizException("历史结果解析失败");
    }
  }

  private SqlHistoryRecordSummaryRes toSummary(SqlAnalyzeRecordEntity entity) {
    SqlHistoryRecordSummaryRes summary = new SqlHistoryRecordSummaryRes();
    summary.setId(entity.getId());
    summary.setDataSourceId(entity.getDatasourceId());
    summary.setSqlFingerprint(entity.getSqlFingerprint());
    summary.setCostMs(entity.getCostMs());
    summary.setCreatedTime(entity.getCreatedTime());
    summary.setSqlText(truncate(entity.getSqlText(), SQL_LIST_PREVIEW_LEN));
    return summary;
  }

  private static String truncate(String text, int maxLen) {
    if (!StringUtils.hasText(text) || text.length() <= maxLen) {
      return text;
    }
    return text.substring(0, maxLen) + "...";
  }

  private static String serializeResult(SqlAnalyzeRes res) {
    try {
      return OBJECT_MAPPER.writeValueAsString(res);
    } catch (JsonProcessingException ex) {
      throw new BizException("分析结果序列化失败");
    }
  }
}
