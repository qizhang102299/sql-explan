package com.sqlexplan.manager;

import com.sqlexplan.exception.BizException;
import com.sqlexplan.mapper.DataSourceConfigMapper;
import com.sqlexplan.model.entity.DataSourceConfigEntity;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 按数据源 ID 解析目标库只读 JdbcTemplate。
 */
@Component
@RequiredArgsConstructor
public class TargetJdbcResolver {

  private final DataSourceConfigMapper dataSourceConfigMapper;

  private final AesCryptoManager aesCryptoManager;

  private final TargetDataSourceManager targetDataSourceManager;

  /**
   * 解析目标库连接。
   *
   * @param dataSourceId 数据源 ID
   * @return 连接上下文
   */
  public TargetConnectionContext resolve(Long dataSourceId) {
    if (dataSourceId == null) {
      throw new BizException("数据源 ID 不能为空");
    }
    DataSourceConfigEntity entity = dataSourceConfigMapper.selectById(dataSourceId);
    if (entity == null) {
      throw new BizException("数据源不存在");
    }
    String password = aesCryptoManager.decrypt(entity.getPasswordEncrypted());
    JdbcTemplate jdbcTemplate = targetDataSourceManager.getJdbcTemplate(
        entity.getId(),
        entity.getHost(),
        entity.getPort(),
        entity.getDbName(),
        entity.getUsername(),
        password);
    TargetConnectionContext context = new TargetConnectionContext();
    context.setDataSourceId(entity.getId());
    context.setJdbcTemplate(jdbcTemplate);
    context.setDbName(entity.getDbName());
    context.setHost(entity.getHost());
    context.setPort(entity.getPort());
    context.setUsername(entity.getUsername());
    context.setPlainPassword(password);
    return context;
  }

  /**
   * 目标库连接上下文（不含对外暴露的密码字段访问器用于日志）。
   */
  @Getter
  @Setter
  public static class TargetConnectionContext {

    private Long dataSourceId;

    private JdbcTemplate jdbcTemplate;

    private String dbName;

    private String host;

    private Integer port;

    private String username;

    private String plainPassword;

    /**
     * 创建一次性可写 JdbcTemplate（临时索引 DDL）。
     *
     * @param manager 连接管理器
     * @return 可写模板
     */
    public JdbcTemplate openWritableJdbcTemplate(TargetDataSourceManager manager) {
      return manager.createWritableJdbcTemplate(host, port, dbName, username, plainPassword);
    }
  }
}
