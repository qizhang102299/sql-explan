package com.sqlexplan.manager;

import com.sqlexplan.constant.ExplainConstants;
import lombok.Getter;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 对目标库执行 EXPLAIN 探测（供语法校验语义层使用）。
 */
@Component
public class TargetExplainProbe {

  /**
   * 执行 EXPLAIN FORMAT=JSON，返回是否成功及失败原因。
   *
   * @param jdbcTemplate 只读 JdbcTemplate
   * @param selectSql    SELECT 语句
   * @return 探测结果
   */
  public ExplainProbeResult probe(JdbcTemplate jdbcTemplate, String selectSql) {
    if (jdbcTemplate == null || !StringUtils.hasText(selectSql)) {
      return ExplainProbeResult.failed(null, "JdbcTemplate 或 SQL 无效");
    }
    String explainSql = ExplainConstants.EXPLAIN_PREFIX + selectSql.trim();
    try {
      jdbcTemplate.query(
          explainSql,
          rs -> {
            if (!rs.next()) {
              throw new IllegalStateException("EXPLAIN 未返回结果");
            }
            return rs.getString(1);
          });
      return ExplainProbeResult.ok();
    } catch (DataAccessException ex) {
      return ExplainProbeResult.failed(ex, ex.getMostSpecificCause().getMessage());
    } catch (Exception ex) {
      return ExplainProbeResult.failed(ex, ex.getMessage());
    }
  }

  /**
   * EXPLAIN 探测结果。
   */
  @Getter
  public static final class ExplainProbeResult {

    private final boolean success;

    private final Throwable error;

    private final String message;

    private ExplainProbeResult(boolean success, Throwable error, String message) {
      this.success = success;
      this.error = error;
      this.message = message;
    }

    public static ExplainProbeResult ok() {
      return new ExplainProbeResult(true, null, null);
    }

    public static ExplainProbeResult failed(Throwable error, String message) {
      return new ExplainProbeResult(false, error, message);
    }
  }
}
