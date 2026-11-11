package com.sqlexplan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.sqlexplan.config.SqlexplanProperties;
import com.sqlexplan.model.domain.ResultEquivalenceReport;
import com.sqlexplan.model.res.SqlSafetyCheckRes;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * {@link ResultEquivalenceChecker} 单元测试（Mock JdbcTemplate）。
 */
@ExtendWith(MockitoExtension.class)
class ResultEquivalenceCheckerTest {

  @Mock
  private JdbcTemplate jdbcTemplate;

  @Mock
  private SqlSafetyChecker sqlSafetyChecker;

  private ResultEquivalenceChecker checker;

  @BeforeEach
  void setUp() {
    SqlexplanProperties properties = new SqlexplanProperties();
    properties.getResultEquivalence().setMaxRowsForChecksum(10);
    checker = new ResultEquivalenceChecker(properties, sqlSafetyChecker);
    SqlSafetyCheckRes passed = new SqlSafetyCheckRes();
    passed.setPassed(true);
    when(sqlSafetyChecker.check(anyString())).thenReturn(passed);
  }

  @Test
  void equivalentWhenRowsAndChecksumMatch() {
    when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<String>>any()))
        .thenReturn(List.of("1\u0001a", "2\u0001b"))
        .thenReturn(List.of("2\u0001b", "1\u0001a"));
    ResultEquivalenceReport report = checker.check(jdbcTemplate, "SELECT 1", "SELECT 1");
    assertThat(report.isEquivalent()).isTrue();
    assertThat(report.getOriginalRowCount()).isEqualTo(2);
    assertThat(report.isChecksumSkipped()).isFalse();
  }

  @Test
  void notEquivalentWhenRowCountDiffers() {
    when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<String>>any()))
        .thenReturn(List.of("1\u0001a"))
        .thenReturn(List.of("1\u0001a", "2\u0001b"));
    ResultEquivalenceReport report = checker.check(jdbcTemplate, "SELECT 1", "SELECT 1");
    assertThat(report.isEquivalent()).isFalse();
    assertThat(report.getMessage()).contains("行数不一致");
  }

  @Test
  void checksumSkippedWhenOverLimit() {
    when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<String>>any()))
        .thenReturn(List.of(
            "r1", "r2", "r3", "r4", "r5", "r6", "r7", "r8", "r9", "r10", "r11"))
        .thenReturn(List.of(
            "r1", "r2", "r3", "r4", "r5", "r6", "r7", "r8", "r9", "r10", "r11"));
    when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(11L);
    ResultEquivalenceReport report = checker.check(jdbcTemplate, "SELECT 1", "SELECT 1");
    assertThat(report.isEquivalent()).isTrue();
    assertThat(report.isChecksumSkipped()).isTrue();
  }
}
