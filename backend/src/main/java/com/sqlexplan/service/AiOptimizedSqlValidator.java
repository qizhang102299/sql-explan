package com.sqlexplan.service;

import com.sqlexplan.model.domain.AiOptimizedSqlValidation;
import com.sqlexplan.model.domain.ExplainPlan;
import com.sqlexplan.model.domain.ResultEquivalenceReport;
import com.sqlexplan.model.res.SqlSafetyCheckRes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * AI 改写 SQL 校验：安全 → EXPLAIN → 结果等价。
 */
@Slf4j
@Service
public class AiOptimizedSqlValidator {

  private final SqlSafetyChecker sqlSafetyChecker;

  private final ExplainService explainService;

  private final ResultEquivalenceChecker resultEquivalenceChecker;

  public AiOptimizedSqlValidator(
      SqlSafetyChecker sqlSafetyChecker,
      ExplainService explainService,
      ResultEquivalenceChecker resultEquivalenceChecker) {
    this.sqlSafetyChecker = sqlSafetyChecker;
    this.explainService = explainService;
    this.resultEquivalenceChecker = resultEquivalenceChecker;
  }

  /**
   * 校验 AI 给出的优化 SQL，未通过时返回失败原因且不采纳该 SQL。
   *
   * @param jdbcTemplate 目标库 JdbcTemplate
   * @param originalSql  原始 SQL
   * @param candidateSql 候选优化 SQL
   * @return 校验结果
   */
  public AiOptimizedSqlValidation validate(
      JdbcTemplate jdbcTemplate,
      String originalSql,
      String candidateSql) {
    AiOptimizedSqlValidation validation = new AiOptimizedSqlValidation();
    validation.setRejectedSql(candidateSql);
    if (!StringUtils.hasText(candidateSql)) {
      validation.setPassed(false);
      validation.setFailureReason("优化 SQL 为空");
      return validation;
    }
    String normalizedCandidate = candidateSql.trim();
    if (normalizedCandidate.equalsIgnoreCase(originalSql.trim())) {
      validation.setPassed(true);
      validation.setSafetyPassed(true);
      validation.setExplainPassed(true);
      validation.setEquivalencePassed(true);
      validation.setFailureReason("");
      ResultEquivalenceReport same = new ResultEquivalenceReport();
      same.setEquivalent(true);
      same.setMessage("与原 SQL 相同，跳过等价校验");
      validation.setEquivalenceReport(same);
      return validation;
    }
    SqlSafetyCheckRes safety = sqlSafetyChecker.check(normalizedCandidate);
    validation.setSafetyPassed(Boolean.TRUE.equals(safety.getPassed()));
    if (!validation.isSafetyPassed()) {
      validation.setPassed(false);
      validation.setFailureReason("安全校验未通过: " + safety.getReason());
      return validation;
    }
    try {
      ExplainPlan plan = explainService.explain(jdbcTemplate, normalizedCandidate);
      validation.setExplainPlan(plan);
      validation.setExplainPassed(true);
    } catch (Exception ex) {
      validation.setExplainPassed(false);
      validation.setPassed(false);
      validation.setFailureReason("EXPLAIN 失败: " + ex.getMessage());
      log.warn("AI 改写 SQL EXPLAIN 失败");
      return validation;
    }
    ResultEquivalenceReport equivalence = resultEquivalenceChecker.check(
        jdbcTemplate, originalSql, normalizedCandidate);
    validation.setEquivalenceReport(equivalence);
    validation.setEquivalencePassed(equivalence.isEquivalent());
    if (!validation.isEquivalencePassed()) {
      validation.setPassed(false);
      validation.setFailureReason(
          "结果等价校验未通过: " + (equivalence.getMessage() == null ? "" : equivalence.getMessage()));
      return validation;
    }
    validation.setPassed(true);
    validation.setFailureReason("");
    return validation;
  }
}
