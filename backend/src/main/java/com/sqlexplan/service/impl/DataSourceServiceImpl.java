package com.sqlexplan.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sqlexplan.exception.BizException;
import com.sqlexplan.manager.AesCryptoManager;
import com.sqlexplan.manager.TargetDataSourceManager;
import com.sqlexplan.mapper.DataSourceConfigMapper;
import com.sqlexplan.model.entity.DataSourceConfigEntity;
import com.sqlexplan.model.req.DataSourceDeleteReq;
import com.sqlexplan.model.req.DataSourceDetailReq;
import com.sqlexplan.model.req.DataSourcePageReq;
import com.sqlexplan.model.req.DataSourceSaveReq;
import com.sqlexplan.model.req.DataSourceTestReq;
import com.sqlexplan.model.res.DataSourcePageRes;
import com.sqlexplan.model.res.DataSourceRes;
import com.sqlexplan.model.res.DataSourceTestRes;
import com.sqlexplan.service.DataSourceService;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 目标数据源配置服务实现。
 */
@Service
@RequiredArgsConstructor
public class DataSourceServiceImpl implements DataSourceService {

  private final DataSourceConfigMapper dataSourceConfigMapper;

  private final AesCryptoManager aesCryptoManager;

  private final TargetDataSourceManager targetDataSourceManager;

  @Override
  public DataSourceRes save(DataSourceSaveReq req) {
    long now = System.currentTimeMillis();
    if (req.getId() == null) {
      validatePasswordForCreate(req.getPassword());
      DataSourceConfigEntity entity = new DataSourceConfigEntity();
      fillEntityFromReq(entity, req, true);
      entity.setPasswordEncrypted(aesCryptoManager.encrypt(req.getPassword()));
      entity.setCreatedTime(now);
      entity.setUpdatedTime(now);
      dataSourceConfigMapper.insert(entity);
      return toRes(entity);
    }
    DataSourceConfigEntity existing = dataSourceConfigMapper.selectById(req.getId());
    if (existing == null) {
      throw new BizException("数据源不存在");
    }
    targetDataSourceManager.evict(req.getId());
    fillEntityFromReq(existing, req, false);
    if (StringUtils.hasText(req.getPassword())) {
      existing.setPasswordEncrypted(aesCryptoManager.encrypt(req.getPassword()));
    }
    existing.setUpdatedTime(now);
    dataSourceConfigMapper.updateById(existing);
    return toRes(existing);
  }

  @Override
  public void delete(DataSourceDeleteReq req) {
    DataSourceConfigEntity existing = dataSourceConfigMapper.selectById(req.getId());
    if (existing == null) {
      throw new BizException("数据源不存在");
    }
    targetDataSourceManager.evict(req.getId());
    dataSourceConfigMapper.deleteById(req.getId());
  }

  @Override
  public DataSourceRes detail(DataSourceDetailReq req) {
    DataSourceConfigEntity entity = dataSourceConfigMapper.selectById(req.getId());
    if (entity == null) {
      throw new BizException("数据源不存在");
    }
    return toRes(entity);
  }

  @Override
  public DataSourcePageRes page(DataSourcePageReq req) {
    LambdaQueryWrapper<DataSourceConfigEntity> wrapper = new LambdaQueryWrapper<>();
    if (StringUtils.hasText(req.getNameKeyword())) {
      wrapper.like(DataSourceConfigEntity::getName, req.getNameKeyword().trim());
    }
    wrapper.orderByDesc(DataSourceConfigEntity::getUpdatedTime);
    Page<DataSourceConfigEntity> page = new Page<>(req.getPageNum(), req.getPageSize());
    Page<DataSourceConfigEntity> result = dataSourceConfigMapper.selectPage(page, wrapper);
    DataSourcePageRes pageRes = new DataSourcePageRes();
    pageRes.setTotal(result.getTotal());
    List<DataSourceRes> records = result.getRecords().stream()
        .map(this::toRes)
        .collect(Collectors.toList());
    pageRes.setRecords(records);
    return pageRes;
  }

  @Override
  public DataSourceTestRes testConnection(DataSourceTestReq req) {
    if (req.getDataSourceId() != null) {
      DataSourceConfigEntity entity = dataSourceConfigMapper.selectById(req.getDataSourceId());
      if (entity == null) {
        throw new BizException("数据源不存在");
      }
      String plainPassword = aesCryptoManager.decrypt(entity.getPasswordEncrypted());
      return targetDataSourceManager.testConnection(
          entity.getHost(),
          entity.getPort(),
          entity.getDbName(),
          entity.getUsername(),
          plainPassword);
    }
    validateDirectConnectionParams(req);
    return targetDataSourceManager.testConnection(
        req.getHost(),
        req.getPort(),
        req.getDbName(),
        req.getUsername(),
        req.getPassword());
  }

  private void validatePasswordForCreate(String password) {
    if (!StringUtils.hasText(password)) {
      throw new BizException("新增数据源时密码不能为空");
    }
  }

  private void validateDirectConnectionParams(DataSourceTestReq req) {
    if (!StringUtils.hasText(req.getHost())
        || req.getPort() == null
        || !StringUtils.hasText(req.getDbName())
        || !StringUtils.hasText(req.getUsername())
        || !StringUtils.hasText(req.getPassword())) {
      throw new BizException("请提供 dataSourceId，或完整的连接参数");
    }
  }

  private void fillEntityFromReq(
      DataSourceConfigEntity entity,
      DataSourceSaveReq req,
      boolean isCreate) {
    entity.setName(req.getName().trim());
    entity.setHost(req.getHost().trim());
    entity.setPort(req.getPort());
    entity.setDbName(req.getDbName().trim());
    entity.setUsername(req.getUsername().trim());
    if (!isCreate) {
      entity.setId(req.getId());
    }
  }

  private DataSourceRes toRes(DataSourceConfigEntity entity) {
    DataSourceRes res = new DataSourceRes();
    res.setId(entity.getId());
    res.setName(entity.getName());
    res.setHost(entity.getHost());
    res.setPort(entity.getPort());
    res.setDbName(entity.getDbName());
    res.setUsername(entity.getUsername());
    res.setCreatedTime(entity.getCreatedTime());
    res.setUpdatedTime(entity.getUpdatedTime());
    return res;
  }
}
