package com.sqlexplan.manager.browser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sqlexplan.config.SqlexplanProperties;
import com.sqlexplan.manager.TargetDataSourceManager;
import com.sqlexplan.model.enums.DbRoutineTypeEnum;
import com.sqlexplan.model.res.dbobject.DbObjectListRes;
import com.sqlexplan.model.res.dbobject.DbRoutineDetailRes;
import com.sqlexplan.model.res.dbobject.DbTableDetailRes;
import com.sqlexplan.model.res.dbobject.DbViewDetailRes;
import com.sqlexplan.support.DockerTestEnvironment;
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
 * 对象浏览器集成测试：覆盖 demo 库 users/orders，自建视图与存储过程后清理。
 */
class DbObjectBrowserIntegrationTest {

  static {
    DockerTestEnvironment.touch();
  }

  private static final String SCHEMA = "demo";

  private static final String LOCAL_HOST = "127.0.0.1";

  private static final int LOCAL_PORT = 3308;

  private static final String TEST_VIEW = "sqlexplan_it_v_user_orders";

  private static final String TEST_PROCEDURE = "sqlexplan_it_sp_count_users";

  private static MySQLContainer<?> mysqlContainer;

  private static JdbcTemplate jdbcTemplate;

  private static MysqlDbObjectBrowser browser;

  @BeforeAll
  static void init() {
    assumeTrue(resolveJdbcTemplate(), "无可用 MySQL：请启动 Docker 或本地 3308 demo 库");
    browser = new MysqlDbObjectBrowser();
    assumeTablesPresent();
    createTestObjects();
  }

  @AfterAll
  static void tearDown() {
    if (jdbcTemplate == null) {
      return;
    }
    try {
      jdbcTemplate.execute("DROP VIEW IF EXISTS " + TEST_VIEW);
      jdbcTemplate.execute("DROP PROCEDURE IF EXISTS " + TEST_PROCEDURE);
    } catch (Exception ignored) {
      // 清理失败不导致测试失败
    }
    if (mysqlContainer != null) {
      mysqlContainer.stop();
    }
  }

  @Test
  void listContainsUsersOrdersAndTestObjects() {
    DbObjectListRes list = browser.listObjects(jdbcTemplate, SCHEMA);
    assertThat(list.getTables().stream().map(t -> t.getName())).contains("users", "orders");
    assertThat(list.getViews().stream().map(v -> v.getName())).contains(TEST_VIEW);
    assertThat(list.getRoutines().stream().map(r -> r.getName())).contains(TEST_PROCEDURE);
  }

  @Test
  void tableDetailForUsersAndOrders() {
    DbTableDetailRes users = browser.getTableDetail(jdbcTemplate, SCHEMA, "users");
    assertThat(users.getBasic().getTableName()).isEqualTo("users");
    assertThat(users.getColumns()).isNotEmpty();
    assertThat(users.getCreateTableDdl()).containsIgnoringCase("CREATE TABLE");

    DbTableDetailRes orders = browser.getTableDetail(jdbcTemplate, SCHEMA, "orders");
    assertThat(orders.getBasic().getTableName()).isEqualTo("orders");
    assertThat(orders.getColumns().stream().anyMatch(c -> "user_id".equals(c.getName()))).isTrue();
  }

  @Test
  void viewAndRoutineDetail() {
    DbViewDetailRes view = browser.getViewDetail(jdbcTemplate, SCHEMA, TEST_VIEW);
    assertThat(view.getViewName()).isEqualTo(TEST_VIEW);
    assertThat(view.getColumns()).isNotEmpty();
    assertThat(view.getDefinitionSql()).isNotBlank();

    DbRoutineDetailRes routine =
        browser.getRoutineDetail(jdbcTemplate, SCHEMA, TEST_PROCEDURE, DbRoutineTypeEnum.PROCEDURE);
    assertThat(routine.getRoutineName()).isEqualTo(TEST_PROCEDURE);
    assertThat(routine.getSourceDefinition()).isNotBlank();
  }

  private static void assumeTablesPresent() {
    Integer users =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = 'users'",
            Integer.class,
            SCHEMA);
    Integer orders =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = 'orders'",
            Integer.class,
            SCHEMA);
    assumeTrue(users != null && users > 0 && orders != null && orders > 0, "demo 库缺少 users/orders，请先造数");
  }

  private static void createTestObjects() {
    jdbcTemplate.execute("DROP VIEW IF EXISTS " + TEST_VIEW);
    jdbcTemplate.execute(
        "CREATE VIEW "
            + TEST_VIEW
            + " AS SELECT u.id AS user_id, u.name, COUNT(o.id) AS order_cnt "
            + "FROM users u LEFT JOIN orders o ON o.user_id = u.id GROUP BY u.id, u.name");
    jdbcTemplate.execute("DROP PROCEDURE IF EXISTS " + TEST_PROCEDURE);
    jdbcTemplate.execute(
        "CREATE PROCEDURE "
            + TEST_PROCEDURE
            + "() READS SQL DATA BEGIN SELECT COUNT(*) FROM users; END");
  }

  private static boolean resolveJdbcTemplate() {
    if (isDockerDaemonReachable()) {
      try {
        mysqlContainer =
            new MySQLContainer<>("mysql:8.0.36")
                .withDatabaseName(SCHEMA)
                .withUsername("test")
                .withPassword("test")
                ;
        mysqlContainer.start();
        SqlexplanProperties properties = new SqlexplanProperties();
        TargetDataSourceManager manager = new TargetDataSourceManager(properties);
        jdbcTemplate =
            manager.createWritableJdbcTemplate(
                mysqlContainer.getHost(),
                mysqlContainer.getFirstMappedPort(),
                SCHEMA,
                mysqlContainer.getUsername(),
                mysqlContainer.getPassword());
        jdbcTemplate.setQueryTimeout(120);
        seedMinimalDemoForContainer();
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

  private static void seedMinimalDemoForContainer() {
    jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS users ("
        + "id BIGINT PRIMARY KEY AUTO_INCREMENT,"
        + "name VARCHAR(50) NOT NULL"
        + ") ENGINE=InnoDB");
    jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS orders ("
        + "id BIGINT PRIMARY KEY AUTO_INCREMENT,"
        + "user_id BIGINT NOT NULL"
        + ") ENGINE=InnoDB");
    jdbcTemplate.update("INSERT INTO users(name) VALUES ('u1'), ('u2')");
    jdbcTemplate.update("INSERT INTO orders(user_id) VALUES (1), (1), (2)");
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
            "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
            LOCAL_HOST,
            LOCAL_PORT,
            SCHEMA);
    try (Connection connection = DriverManager.getConnection(url, "root", "root")) {
      if (!connection.isValid(3)) {
        return false;
      }
      SqlexplanProperties properties = new SqlexplanProperties();
      TargetDataSourceManager manager = new TargetDataSourceManager(properties);
      jdbcTemplate =
          manager.createWritableJdbcTemplate(LOCAL_HOST, LOCAL_PORT, SCHEMA, "root", "root");
      jdbcTemplate.setQueryTimeout(120);
      return true;
    } catch (Exception ex) {
      return false;
    }
  }
}
