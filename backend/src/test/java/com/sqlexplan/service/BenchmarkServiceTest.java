package com.sqlexplan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sqlexplan.config.SqlexplanProperties;
import com.sqlexplan.model.domain.BenchmarkLatencyStats;
import com.sqlexplan.model.domain.BenchmarkReport;
import com.sqlexplan.model.domain.ResultEquivalenceReport;
import com.sqlexplan.model.res.SqlSafetyCheckRes;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

/**
 * {@link BenchmarkService} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BenchmarkServiceTest {

  private BenchmarkService benchmarkService;

  private JdbcTemplate jdbcTemplate;

  private SqlSafetyChecker sqlSafetyChecker;

  @BeforeEach
  void setUp() {
    SqlexplanProperties properties = new SqlexplanProperties();
    sqlSafetyChecker = mock(SqlSafetyChecker.class);
    ResultEquivalenceChecker equivalenceChecker = mock(ResultEquivalenceChecker.class);
    when(sqlSafetyChecker.check(anyString())).thenAnswer(invocation -> {
      SqlSafetyCheckRes res = new SqlSafetyCheckRes();
      res.setPassed(true);
      return res;
    });
    ResultEquivalenceReport eq = new ResultEquivalenceReport();
    eq.setEquivalent(true);
    when(equivalenceChecker.check(org.mockito.ArgumentMatchers.any(), anyString(), anyString()))
        .thenReturn(eq);
    benchmarkService = new BenchmarkService(
        properties,
        sqlSafetyChecker,
        equivalenceChecker,
        mock(com.sqlexplan.manager.TargetDataSourceManager.class));
    jdbcTemplate = mock(JdbcTemplate.class);
    org.mockito.Mockito.doAnswer(invocation -> {
      RowCallbackHandler handler = invocation.getArgument(1);
      return null;
    }).when(jdbcTemplate).query(anyString(), org.mockito.ArgumentMatchers.any(RowCallbackHandler.class));
  }

  @Test
  void statsMedianAndP95() {
    BenchmarkLatencyStats stats = benchmarkService.toStats(List.of(10L, 20L, 30L, 40L, 100L));
    assertThat(stats.getMedianMs()).isEqualTo(30L);
    assertThat(stats.getP95Ms()).isEqualTo(100L);
    assertThat(stats.getMinMs()).isEqualTo(10L);
    assertThat(stats.getMaxMs()).isEqualTo(100L);
  }

  @Test
  void benchmarkSkipsWhenSqlIdentical() {
    BenchmarkReport report = benchmarkService.benchmark(
        jdbcTemplate,
        "SELECT 1",
        "SELECT 1",
        3,
        false,
        null,
        null);
    assertThat(report.isExecuted()).isFalse();
  }
}
