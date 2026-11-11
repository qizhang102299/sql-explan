package com.sqlexplan.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sqlexplan.config.SqlexplanProperties;
import com.sqlexplan.model.domain.SqlSemanticInsight;
import com.sqlexplan.model.domain.SqlStructure;
import com.sqlexplan.model.enums.SqlSetOperationEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link RuleBasedSemanticBuilder} 单元测试。
 */
class RuleBasedSemanticBuilderTest {

  private RuleBasedSemanticBuilder builder;

  private SqlParseService sqlParseService;

  @BeforeEach
  void setUp() {
    SqlexplanProperties properties = new SqlexplanProperties();
    builder = new RuleBasedSemanticBuilder(properties);
    sqlParseService = new SqlParseService();
  }

  @Test
  void buildMultiTableJoinWithPagination() {
    String sql = """
        SELECT u.id, u.name, o.amount
        FROM users u
        INNER JOIN orders o ON u.id = o.user_id
        WHERE u.status = 1 AND o.amount > 100
        ORDER BY o.amount DESC
        LIMIT 10 OFFSET 20000
        """;
    SqlStructure structure = sqlParseService.parse(sql);
    SqlSemanticInsight insight = builder.build(structure);
    assertThat(insight.getIntentSummary()).contains("多表关联");
    assertThat(insight.getInvolvedTables()).contains("users AS u", "orders AS o");
    assertThat(insight.getJoinsDescription()).contains("INNER JOIN");
    assertThat(insight.getFiltersDescription()).contains("谓词");
    assertThat(insight.getPaginationDescription()).contains("OFFSET 20000");
    assertThat(insight.getRiskNotes()).anyMatch(r -> r.contains("深分页"));
  }

  @Test
  void buildCteSubqueryAndAggregate() {
    String sql = """
        WITH recent_orders AS (
          SELECT user_id, MAX(created_at) AS last_order
          FROM orders
          WHERE status = 'PAID'
          GROUP BY user_id
        )
        SELECT u.id,
               (SELECT COUNT(*) FROM employees e WHERE e.dept_id = u.id) AS cnt
        FROM users u
        JOIN recent_orders r ON u.id = r.user_id
        """;
    SqlStructure structure = sqlParseService.parse(sql);
    SqlSemanticInsight insight = builder.build(structure);
    assertThat(insight.getSubqueryCteDescription()).contains("CTE");
    assertThat(insight.getSubqueryCteDescription()).contains("子查询");
    assertThat(insight.getAggregationDescription()).contains("GROUP BY");
    assertThat(insight.getRiskNotes()).anyMatch(r -> r.contains("CTE"));
  }

  @Test
  void buildUnionBranches() {
    String sql = """
        SELECT id FROM users WHERE status = 1
        UNION ALL
        SELECT id FROM users_archive
        """;
    SqlStructure structure = sqlParseService.parse(sql);
    assertThat(structure.getSetOperation()).isEqualTo(SqlSetOperationEnum.UNION_ALL);
    SqlSemanticInsight insight = builder.build(structure);
    assertThat(insight.getIntentSummary()).contains("UNION ALL");
    assertThat(insight.getInvolvedTables()).isNotEmpty();
    assertThat(insight.getSubqueryCteDescription()).contains("分支");
  }
}
