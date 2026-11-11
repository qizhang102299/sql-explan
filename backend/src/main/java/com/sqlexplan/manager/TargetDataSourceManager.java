package com.sqlexplan.manager;

import com.sqlexplan.config.SqlexplanProperties;
import com.sqlexplan.constant.TargetDataSourceConstants;
import com.sqlexplan.exception.BizException;
import com.sqlexplan.model.res.DataSourceTestRes;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 目标库动态只读连接池管理（按数据源 ID 缓存）。
 */
@Slf4j
@Component
public class TargetDataSourceManager {

  private final SqlexplanProperties sqlexplanProperties;

  private final Map<Long, HikariDataSource> poolCache = new ConcurrentHashMap<>();

  public TargetDataSourceManager(SqlexplanProperties sqlexplanProperties) {
    this.sqlexplanProperties = sqlexplanProperties;
  }

  /**
   * 获取或创建目标库 JdbcTemplate。
   *
   * @param dataSourceId 数据源 ID
   * @param host         主机
   * @param port         端口
   * @param dbName       库名
   * @param username     用户名
   * @param plainPassword 明文密码
   * @return JdbcTemplate
   */
  public JdbcTemplate getJdbcTemplate(
      Long dataSourceId,
      String host,
      Integer port,
      String dbName,
      String username,
      String plainPassword) {
    HikariDataSource dataSource = poolCache.computeIfAbsent(
        dataSourceId,
        id -> createPool(host, port, dbName, username, plainPassword));
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.setQueryTimeout(sqlexplanProperties.getTargetDatasource().getQueryTimeoutSeconds());
    return jdbcTemplate;
  }

  /**
   * 配置变更后驱逐缓存连接池。
   *
   * @param dataSourceId 数据源 ID
   */
  public void evict(Long dataSourceId) {
    HikariDataSource removed = poolCache.remove(dataSourceId);
    if (removed != null) {
      closeQuietly(removed);
      log.info("已驱逐目标库连接池, dataSourceId={}", dataSourceId);
    }
  }

  /**
   * 测试数据库连通性（使用临时连接池，测完即关）。
   *
   * @param host     主机
   * @param port     端口
   * @param dbName   库名
   * @param username 用户名
   * @param password 明文密码
   * @return 测试结果
   */
  public DataSourceTestRes testConnection(
      String host,
      Integer port,
      String dbName,
      String username,
      String password) {
    long start = System.currentTimeMillis();
    DataSourceTestRes res = new DataSourceTestRes();
    HikariDataSource dataSource = null;
    try {
      dataSource = createPool(host, port, dbName, username, password);
      try (Connection connection = dataSource.getConnection()) {
        if (!connection.isValid(3)) {
          res.setSuccess(false);
          res.setMessage("连接无效");
        } else {
          res.setSuccess(true);
          res.setMessage("连接成功");
        }
      }
    } catch (SQLException ex) {
      log.warn("数据源连通性测试失败, host={}, db={}", host, dbName);
      res.setSuccess(false);
      res.setMessage("连接失败: " + ex.getMessage());
    } finally {
      if (dataSource != null) {
        closeQuietly(dataSource);
      }
      res.setElapsedMs(System.currentTimeMillis() - start);
    }
    return res;
  }

  /**
   * 构造只读 Hikari 数据源（不纳入缓存，供一次性使用）。
   *
   * @param host          主机
   * @param port          端口
   * @param dbName        库名
   * @param username      用户名
   * @param plainPassword 明文密码
   * @return DataSource
   */
  public DataSource createReadOnlyDataSource(
      String host,
      Integer port,
      String dbName,
      String username,
      String plainPassword) {
    return createPool(host, port, dbName, username, plainPassword, true);
  }

  /**
   * 创建可写连接（不缓存），仅用于临时索引 DDL 等短时操作。
   *
   * @param host          主机
   * @param port          端口
   * @param dbName        库名
   * @param username      用户名
   * @param plainPassword 明文密码
   * @return JdbcTemplate
   */
  public JdbcTemplate createWritableJdbcTemplate(
      String host,
      Integer port,
      String dbName,
      String username,
      String plainPassword) {
    HikariDataSource dataSource = createPool(host, port, dbName, username, plainPassword, false);
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.setQueryTimeout(sqlexplanProperties.getTargetDatasource().getQueryTimeoutSeconds());
    return jdbcTemplate;
  }

  /**
   * 关闭一次性数据源（与 {@link #createWritableJdbcTemplate} 配对使用）。
   *
   * @param jdbcTemplate 由一次性数据源创建的模板
   */
  public void closeDisposableJdbcTemplate(JdbcTemplate jdbcTemplate) {
    if (jdbcTemplate == null) {
      return;
    }
    DataSource dataSource = jdbcTemplate.getDataSource();
    if (dataSource instanceof HikariDataSource hikari) {
      closeQuietly(hikari);
    }
  }

  private HikariDataSource createPool(
      String host,
      Integer port,
      String dbName,
      String username,
      String plainPassword) {
    return createPool(host, port, dbName, username, plainPassword, true);
  }

  private HikariDataSource createPool(
      String host,
      Integer port,
      String dbName,
      String username,
      String plainPassword,
      boolean readOnly) {
    if (host == null || port == null || dbName == null || username == null || plainPassword == null) {
      throw new BizException("连接参数不完整");
    }
    SqlexplanProperties.TargetDatasource dsProps = sqlexplanProperties.getTargetDatasource();
    HikariConfig config = new HikariConfig();
    config.setDriverClassName(TargetDataSourceConstants.DRIVER_CLASS_NAME);
    config.setJdbcUrl(String.format(TargetDataSourceConstants.JDBC_URL_TEMPLATE, host, port, dbName));
    config.setUsername(username);
    config.setPassword(plainPassword);
    config.setReadOnly(readOnly);
    config.setConnectionTimeout(dsProps.getConnectionTimeoutMs());
    config.setMaximumPoolSize(dsProps.getMaximumPoolSize());
    config.setMinimumIdle(dsProps.getMinimumIdle());
    config.setPoolName("target-ds-" + host + "-" + dbName);
    config.setConnectionTestQuery(TargetDataSourceConstants.VALIDATION_QUERY);
    return new HikariDataSource(config);
  }

  private static void closeQuietly(HikariDataSource dataSource) {
    try {
      dataSource.close();
    } catch (Exception ex) {
      log.warn("关闭目标库连接池异常", ex);
    }
  }
}
