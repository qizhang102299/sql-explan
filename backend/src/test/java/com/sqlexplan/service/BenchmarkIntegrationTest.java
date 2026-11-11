package com.sqlexplan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sqlexplan.config.SqlexplanProperties;
import com.sqlexplan.manager.TargetDataSourceManager;
import com.sqlexplan.model.domain.BenchmarkReport;
import com.sqlexplan.model.res.SqlSafetyCheckRes;
import com.sqlexplan.support.DockerTestEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Locale;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

/**
 * 集成测试：验证基准测试统计与临时索引收益（Docker 可用时启用 Testcontainers，否则回退本机 3308）。
 */
class BenchmarkIntegrationTest {

  static {
    DockerTestEnvironment.touch();
  }

  private static final String BENCH_TABLE = "bench_user";

  private static final String LOCAL_FALLBACK_HOST = "127.0.0.1";

  private static final int LOCAL_FALLBACK_PORT = 3308;

  private static MySQLContainer<?> mysqlContainer;

  private static boolean usingTestcontainers;

  private static JdbcTemplate jdbcTemplate;

  private static BenchmarkService benchmarkService;

  @BeforeAll
  static void init() {
    assumeTrue(resolveJdbcTemplate(), "无可用 MySQL：请启动 Docker 或本地 3308 demo 库");
    prepareBenchTable();
    SqlexplanProperties properties = new SqlexplanProperties();
    properties.getBenchmark().setAllowTempIndexVerification(true);
    SqlSafetyChecker safetyChecker = new SqlSafetyChecker();
    TargetDataSourceManager targetDataSourceManager = new TargetDataSourceManager(properties);
    ResultEquivalenceChecker equivalenceChecker =
        new ResultEquivalenceChecker(properties, safetyChecker);
    benchmarkService = new BenchmarkService(
        properties, safetyChecker, equivalenceChecker, targetDataSourceManager);
  }

  @AfterAll
  static void tearDown() {
    if (jdbcTemplate != null) {
      try {
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + BENCH_TABLE);
      } catch (Exception ignored) {
        // 清理失败不导致测试失败
      }
    }
    if (mysqlContainer != null) {
      mysqlContainer.stop();
    }
  }

  @Test
  void benchmarkAlternatingExecutionProducesStats() {
    String sqlA = "SELECT id FROM " + BENCH_TABLE + " WHERE category = 3";
    String sqlB = "SELECT id, payload FROM " + BENCH_TABLE + " WHERE category = 3";
    BenchmarkReport report = benchmarkService.benchmark(
        jdbcTemplate, sqlA, sqlB, 3, false, null, null);
    assertThat(report.isExecuted()).isTrue();
    assertThat(report.getOriginalStats().getExecutionCount()).isEqualTo(3);
    assertThat(report.getOptimizedStats().getExecutionCount()).isEqualTo(3);
    assertThat(report.getImprovementRatio()).isNotNull();
  }

  @Test
  void tempIndexImprovesFilteredQuery() {
    String sql = "SELECT COUNT(*) FROM " + BENCH_TABLE + " WHERE category = 7";
    SqlSafetyCheckRes check = new SqlSafetyChecker().check(sql);
    assertThat(check.getPassed()).isTrue();

    long before = medianLatency(sql, 3);
    String indexName = "sqlexplan_tmp_it_" + System.currentTimeMillis();
    try {
      jdbcTemplate.execute(
          String.format(
              Locale.ROOT,
              "CREATE INDEX `%s` ON %s (category)",
              indexName,
              BENCH_TABLE));
      long after = medianLatency(sql, 3);
      assertThat(after).isLessThanOrEqualTo(before);
    } finally {
      jdbcTemplate.execute(
          String.format(Locale.ROOT, "DROP INDEX `%s` ON %s", indexName, BENCH_TABLE));
    }
  }

  private long medianLatency(String sql, int iterations) {
    java.util.List<Long> samples = new java.util.ArrayList<>();
    for (int i = 0; i < iterations; i++) {
      long start = System.nanoTime();
      jdbcTemplate.queryForObject(sql, Long.class);
      samples.add((System.nanoTime() - start) / 1_000_000L);
    }
    return benchmarkService.toStats(samples).getMedianMs();
  }

  private static boolean resolveJdbcTemplate() {
    if (isDockerDaemonReachable()) {
      try {
        mysqlContainer =
            new MySQLContainer<>("mysql:8.0.36")
                .withDatabaseName("bench")
                .withUsername("test")
                .withPassword("test");
        mysqlContainer.start();
        SqlexplanProperties properties = new SqlexplanProperties();
        TargetDataSourceManager manager = new TargetDataSourceManager(properties);
        jdbcTemplate =
            manager.createWritableJdbcTemplate(
                mysqlContainer.getHost(),
                mysqlContainer.getFirstMappedPort(),
                mysqlContainer.getDatabaseName(),
                mysqlContainer.getUsername(),
                mysqlContainer.getPassword());
        jdbcTemplate.setQueryTimeout(120);
        usingTestcontainers = true;
        return true;
      } catch (Exception ex) {
        if (mysqlContainer != null) {
          mysqlContainer.close();
          mysqlContainer = null;
        }
      }
    }
    return tryLocalComposeMysql();
  }

  private static boolean isDockerDaemonReachable() {
    if (!DockerClientFactory.instance().isDockerAvailable()) {
      return false;
    }
    try {
      return DockerClientFactory.instance().client().pingCmd().exec() != null;
    } catch (Exception ex) {
      return false;
    }
  }

  private static boolean tryLocalComposeMysql() {
    String url =
        String.format(
            Locale.ROOT,
            "jdbc:mysql://%s:%d/demo?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
            LOCAL_FALLBACK_HOST,
            LOCAL_FALLBACK_PORT);
    try (Connection connection = DriverManager.getConnection(url, "root", "root")) {
      if (!connection.isValid(3)) {
        return false;
      }
      SqlexplanProperties properties = new SqlexplanProperties();
      TargetDataSourceManager manager = new TargetDataSourceManager(properties);
      jdbcTemplate =
          manager.createWritableJdbcTemplate(
              LOCAL_FALLBACK_HOST, LOCAL_FALLBACK_PORT, "demo", "root", "root");
      jdbcTemplate.setQueryTimeout(120);
      usingTestcontainers = false;
      return true;
    } catch (Exception ex) {
      return false;
    }
  }

  private static void prepareBenchTable() {
    jdbcTemplate.execute("DROP TABLE IF EXISTS " + BENCH_TABLE);
    jdbcTemplate.execute(
        "CREATE TABLE "
            + BENCH_TABLE
            + " ("
            + "id BIGINT PRIMARY KEY AUTO_INCREMENT,"
            + "category INT NOT NULL,"
            + "payload VARCHAR(64) NOT NULL"
            + ") ENGINE=InnoDB");
    int batch = 5000;
    int totalRows = usingTestcontainers ? 100_000 : 20_000;
    for (int offset = 0; offset < totalRows; offset += batch) {
      StringBuilder sql = new StringBuilder("INSERT INTO ").append(BENCH_TABLE).append("(category, payload) VALUES ");
      for (int i = 0; i < batch && offset + i < totalRows; i++) {
        if (i > 0) {
          sql.append(',');
        }
        int row = offset + i;
        int category = row % 10;
        sql.append('(').append(category).append(",'p").append(row).append("')");
      }
      jdbcTemplate.execute(sql.toString());
    }
    if (!usingTestcontainers) {
      Path note =
          Path.of(System.getProperty("user.dir")).resolve("target/benchmark-it-fallback.txt");
      try {
        Files.writeString(
            note,
            "BenchmarkIntegrationTest 使用本机 3308/demo 回退执行（Testcontainers 未连接 Docker API）\n");
      } catch (Exception ignored) {
        // 说明文件写入失败不影响测试
      }
    }
  }
}
