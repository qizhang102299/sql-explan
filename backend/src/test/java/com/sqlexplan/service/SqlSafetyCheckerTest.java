package com.sqlexplan.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sqlexplan.model.res.SqlSafetyCheckRes;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * SqlSafetyChecker 正反例单元测试。
 */
class SqlSafetyCheckerTest {

  private SqlSafetyChecker sqlSafetyChecker;

  @BeforeEach
  void setUp() {
    sqlSafetyChecker = new SqlSafetyChecker();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("safeSqlCases")
  void shouldAllowSafeSelect(String name, String sql) {
    SqlSafetyCheckRes res = sqlSafetyChecker.check(sql);
    assertThat(res.getPassed())
        .as(name)
        .isTrue();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("unsafeSqlCases")
  void shouldRejectUnsafeSql(String name, String sql) {
    SqlSafetyCheckRes res = sqlSafetyChecker.check(sql);
    assertThat(res.getPassed())
        .as(name)
        .isFalse();
    assertThat(res.getReason()).isNotBlank();
  }

  private static Stream<Arguments> safeSqlCases() {
    return Stream.of(
        Arguments.of("简单查询", "SELECT 1"),
        Arguments.of("带条件", "SELECT id, name FROM user WHERE status = 1"),
        Arguments.of("JOIN", "SELECT u.id FROM user u INNER JOIN order o ON u.id = o.user_id"),
        Arguments.of("子查询", "SELECT * FROM user WHERE id IN (SELECT user_id FROM order)"),
        Arguments.of("UNION", "SELECT id FROM user UNION ALL SELECT id FROM user_bak"),
        Arguments.of("CTE", "WITH cte AS (SELECT 1 AS x) SELECT x FROM cte"),
        Arguments.of("GROUP BY", "SELECT dept_id, COUNT(*) FROM employee GROUP BY dept_id"),
        Arguments.of("ORDER BY LIMIT", "SELECT * FROM user ORDER BY id DESC LIMIT 10 OFFSET 20"),
        Arguments.of("DISTINCT", "SELECT DISTINCT name FROM user"),
        Arguments.of("聚合", "SELECT MAX(created_time) FROM user"),
        Arguments.of("LEFT JOIN", "SELECT a.* FROM a LEFT JOIN b ON a.id = b.a_id WHERE a.id > 0"),
        Arguments.of("标量子查询", "SELECT (SELECT COUNT(*) FROM orders) AS cnt"));
  }

  private static Stream<Arguments> unsafeSqlCases() {
    return Stream.of(
        Arguments.of("空字符串", "   "),
        Arguments.of("INSERT", "INSERT INTO user(name) VALUES('a')"),
        Arguments.of("UPDATE", "UPDATE user SET name = 'x' WHERE id = 1"),
        Arguments.of("DELETE", "DELETE FROM user WHERE id = 1"),
        Arguments.of("DROP", "DROP TABLE user"),
        Arguments.of("CREATE", "CREATE TABLE t(id INT)"),
        Arguments.of("ALTER", "ALTER TABLE user ADD COLUMN x INT"),
        Arguments.of("TRUNCATE", "TRUNCATE TABLE user"),
        Arguments.of("REPLACE", "REPLACE INTO user(id, name) VALUES(1, 'a')"),
        Arguments.of("多语句", "SELECT 1; SELECT 2"),
        Arguments.of("多语句含 DDL", "SELECT 1; DROP TABLE user"),
        Arguments.of("FOR UPDATE", "SELECT * FROM user WHERE id = 1 FOR UPDATE"),
        Arguments.of("INTO OUTFILE", "SELECT * FROM user INTO OUTFILE '/tmp/u.csv'"),
        Arguments.of("INTO DUMPFILE", "SELECT * FROM user INTO DUMPFILE '/tmp/u.txt'"),
        Arguments.of("SLEEP", "SELECT * FROM user WHERE id = SLEEP(5)"),
        Arguments.of("SELECT SLEEP", "SELECT SLEEP(10)"),
        Arguments.of("BENCHMARK", "SELECT BENCHMARK(1000000, SHA1('x'))"),
        Arguments.of("LOAD_FILE", "SELECT LOAD_FILE('/etc/passwd')"),
        Arguments.of("EXPLAIN", "EXPLAIN SELECT * FROM user"),
        Arguments.of("SHOW", "SHOW TABLES"),
        Arguments.of("CALL", "CALL proc_demo()"),
        Arguments.of("GRANT", "GRANT SELECT ON db.* TO 'u'@'%'"),
        Arguments.of("LOCK IN SHARE MODE", "SELECT * FROM user WHERE id = 1 LOCK IN SHARE MODE"),
        Arguments.of("语法错误", "SELEC 1 FROM"),
        Arguments.of("LOAD DATA", "LOAD DATA INFILE '/tmp/t.csv' INTO TABLE user"),
        Arguments.of("双 SELECT 分号", "SELECT 1;DELETE FROM user"),
        Arguments.of("子查询中的危险函数", "SELECT * FROM user WHERE id = (SELECT SLEEP(1))"));
  }
}
