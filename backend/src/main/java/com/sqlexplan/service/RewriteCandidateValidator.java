package com.sqlexplan.service;

import com.sqlexplan.model.domain.AiOptimizedSqlValidation;
import com.sqlexplan.model.domain.SqlRewriteResult;
import com.sqlexplan.model.domain.SqlRewriteSuggestion;
import com.sqlexplan.model.domain.ValidatedRewriteCandidate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 规则改写候选校验：安全 → EXPLAIN → 等价；仅返回通过项。
 */
@Service
@RequiredArgsConstructor
public class RewriteCandidateValidator {

  private final AiOptimizedSqlValidator aiOptimizedSqlValidator;

  /**
   * 校验改写结果中的候选 SQL，仅保留通过的候选。
   *
   * @param jdbcTemplate 目标库
   * @param originalSql  原始 SQL
   * @param rewriteResult 规则改写结果
   * @return 通过校验的候选列表
   */
  public List<ValidatedRewriteCandidate> validatePassedOnly(
      JdbcTemplate jdbcTemplate,
      String originalSql,
      SqlRewriteResult rewriteResult) {
    List<ValidatedRewriteCandidate> passed = new ArrayList<>();
    if (rewriteResult == null) {
      return passed;
    }
    Set<String> seen = new HashSet<>();
    collectIfPassed(jdbcTemplate, originalSql, rewriteResult.getRecommendedSql(), null, null, seen, passed);
    if (rewriteResult.getSuggestions() != null) {
      for (SqlRewriteSuggestion suggestion : rewriteResult.getSuggestions()) {
        if (suggestion == null || !StringUtils.hasText(suggestion.getRewrittenSql())) {
          continue;
        }
        collectIfPassed(
            jdbcTemplate,
            originalSql,
            suggestion.getRewrittenSql(),
            suggestion.getRuleType(),
            suggestion.getDescription(),
            seen,
            passed);
      }
    }
    return passed;
  }

  /**
   * 在已通过候选中选取推荐 SQL（优先与规则引擎 recommended 一致且已通过者）。
   *
   * @param originalSql     原始 SQL
   * @param rewriteResult   规则改写结果
   * @param passedCandidates 已通过列表
   * @return 推荐优化 SQL，无则返回原 SQL
   */
  public String pickBestOptimizedSql(
      String originalSql,
      SqlRewriteResult rewriteResult,
      List<ValidatedRewriteCandidate> passedCandidates) {
    if (passedCandidates == null || passedCandidates.isEmpty()) {
      return originalSql;
    }
    String recommended = rewriteResult == null ? "" : nullToEmpty(rewriteResult.getRecommendedSql()).trim();
    if (StringUtils.hasText(recommended)
        && !recommended.equalsIgnoreCase(originalSql.trim())
        && containsSql(passedCandidates, recommended)) {
      return recommended;
    }
    for (ValidatedRewriteCandidate candidate : passedCandidates) {
      String sql = candidate.getRewrittenSql().trim();
      if (!sql.equalsIgnoreCase(originalSql.trim())) {
        return sql;
      }
    }
    return passedCandidates.get(0).getRewrittenSql().trim();
  }

  /**
   * 对最终优化 SQL 做等价校验。
   *
   * @param jdbcTemplate 目标库
   * @param originalSql  原始 SQL
   * @param optimizedSql 优化 SQL
   * @return 校验结果
   */
  public AiOptimizedSqlValidation validateFinal(
      JdbcTemplate jdbcTemplate, String originalSql, String optimizedSql) {
    return aiOptimizedSqlValidator.validate(jdbcTemplate, originalSql, optimizedSql);
  }

  private void collectIfPassed(
      JdbcTemplate jdbcTemplate,
      String originalSql,
      String candidateSql,
      com.sqlexplan.model.enums.SqlRewriteRuleTypeEnum ruleType,
      String description,
      Set<String> seen,
      List<ValidatedRewriteCandidate> passed) {
    if (!StringUtils.hasText(candidateSql)) {
      return;
    }
    String normalized = candidateSql.trim();
    String dedupeKey = normalized.toLowerCase(Locale.ROOT);
    if (!seen.add(dedupeKey)) {
      return;
    }
    if (normalized.equalsIgnoreCase(originalSql.trim())) {
      return;
    }
    AiOptimizedSqlValidation validation =
        aiOptimizedSqlValidator.validate(jdbcTemplate, originalSql, normalized);
    if (!validation.isPassed()) {
      return;
    }
    ValidatedRewriteCandidate item = new ValidatedRewriteCandidate();
    item.setRuleType(ruleType);
    item.setDescription(description);
    item.setRewrittenSql(normalized);
    passed.add(item);
  }

  private static boolean containsSql(List<ValidatedRewriteCandidate> passed, String sql) {
    String target = sql.trim().toLowerCase(Locale.ROOT);
    for (ValidatedRewriteCandidate candidate : passed) {
      if (candidate.getRewrittenSql() != null
          && candidate.getRewrittenSql().trim().toLowerCase(Locale.ROOT).equals(target)) {
        return true;
      }
    }
    return false;
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
