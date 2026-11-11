package com.sqlexplan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sqlexplan.model.domain.SqlStructure;
import com.sqlexplan.model.enums.JoinTypeEnum;
import com.sqlexplan.model.enums.SqlSetOperationEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link SqlParseService} 单元测试。
 */
class SqlParseServiceTest {

  private SqlParseService sqlParseService;

  @BeforeEach
  void setUp() {
    sqlParseService = new SqlParseService();
  }

  @Test
  void parseMultiTableJoin() {
    String sql = """
        SELECT u.id, u.name, o.amount
        FROM users u
        INNER JOIN orders o ON u.id = o.user_id
        WHERE u.status = 1 AND o.amount > 100
        ORDER BY o.amount DESC
        LIMIT 20, 10
        """;
    SqlStructure structure = sqlParseService.parse(sql);
    assertThat(structure.getBody()).isNotNull();
    assertThat(structure.getBody().getFromTables()).hasSize(1);
    assertThat(structure.getBody().getFromTables().get(0).getTableName()).isEqualTo("users");
    assertThat(structure.getBody().getFromTables().get(0).getAlias()).isEqualTo("u");
    assertThat(structure.getBody().getJoins()).hasSize(1);
    assertThat(structure.getBody().getJoins().get(0).getJoinType()).isEqualTo(JoinTypeEnum.INNER);
    assertThat(structure.getBody().getJoins().get(0).getRightTableName()).isEqualTo("orders");
    assertThat(structure.getBody().getWherePredicates()).isNotEmpty();
    assertThat(structure.getBody().getOrderByColumns()).contains("o.amount");
    assertThat(structure.getBody().getOrderByAscFlags()).containsExactly(false);
    assertThat(structure.getBody().getOffset()).isEqualTo(20L);
    assertThat(structure.getBody().getLimit()).isEqualTo(10L);
    assertThat(sqlParseService.extractPhysicalTableNames(structure))
        .containsExactlyInAnyOrder("users", "orders");
  }

  @Test
  void parseSubqueryAndAggregate() {
    String sql = """
        SELECT d.dept_name,
               (SELECT COUNT(*) FROM employees e WHERE e.dept_id = d.id) AS cnt
        FROM departments d
        WHERE d.id IN (SELECT dept_id FROM projects WHERE budget > 5000)
        """;
    SqlStructure structure = sqlParseService.parse(sql);
    assertThat(structure.getBody().getSelectColumns()).hasSizeGreaterThanOrEqualTo(2);
    assertThat(structure.getBody().getSubqueries()).isNotEmpty();
    assertThat(structure.getAllTables()).extracting("tableName").contains("departments");
  }

  @Test
  void parseCte() {
    String sql = """
        WITH recent_orders AS (
          SELECT user_id, MAX(created_at) AS last_order
          FROM orders
          WHERE status = 'PAID'
          GROUP BY user_id
        )
        SELECT u.id, u.email, r.last_order
        FROM users u
        JOIN recent_orders r ON u.id = r.user_id
        """;
    SqlStructure structure = sqlParseService.parse(sql);
    assertThat(structure.getCtes()).hasSize(1);
    assertThat(structure.getCtes().get(0).getName()).isEqualTo("recent_orders");
    assertThat(structure.getCtes().get(0).getBody().getBody().getGroupByColumns()).contains("user_id");
    assertThat(structure.getCtes().get(0).getBody().getBody().getAggregateFunctions()).isNotEmpty();
    assertThat(sqlParseService.extractPhysicalTableNames(structure))
        .contains("users", "orders");
  }

  @Test
  void parseUnion() {
    String sql = """
        SELECT id, name FROM users WHERE status = 1
        UNION ALL
        SELECT id, name FROM users_archive WHERE migrated = 0
        """;
    SqlStructure structure = sqlParseService.parse(sql);
    assertThat(structure.getSetOperation()).isEqualTo(SqlSetOperationEnum.UNION_ALL);
    assertThat(structure.getUnionBranches()).hasSize(2);
    assertThat(sqlParseService.extractPhysicalTableNames(structure))
        .containsExactlyInAnyOrder("users", "users_archive");
  }

  @Test
  void parseLimitCountOffsetForm() {
    String sql = """
        SELECT id, name FROM users
        WHERE status = 1
        ORDER BY id
        LIMIT 10 OFFSET 20000
        """;
    SqlStructure structure = sqlParseService.parse(sql);
    assertThat(structure.getBody().getLimit()).isEqualTo(10L);
    assertThat(structure.getBody().getOffset()).isEqualTo(20000L);
  }

  @Test
  void parseRejectsNonSelect() {
    assertThatThrownBy(() -> sqlParseService.parse("UPDATE users SET name = 'x'"))
        .hasMessageContaining("仅支持 SELECT");
  }
}
