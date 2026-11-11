package com.sqlexplan.manager;

import com.sqlexplan.constant.ExplainConstants;
import com.sqlexplan.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 在目标库执行 EXPLAIN FORMAT=JSON（只读 JDBC）。
 */
@Slf4j
@Component
public class TargetExplainManager {

  /**
   * 对已通过安全校验的 SELECT 执行 EXPLAIN FORMAT=JSON，返回 JSON 文本。
   *
   * @param jdbcTemplate 目标库只读 JdbcTemplate
   * @param selectSql    单条 SELECT SQL（不含 EXPLAIN 前缀）
   * @return MySQL 返回的 JSON 字符串
   */
  public String fetchExplainJson(JdbcTemplate jdbcTemplate, String selectSql) {
    if (jdbcTemplate == null) {
      throw new BizException("JdbcTemplate 不能为空");
    }
    if (!StringUtils.hasText(selectSql)) {
      throw new BizException("SQL 不能为空");
    }
    String explainSql = ExplainConstants.EXPLAIN_PREFIX + selectSql.trim();
    try {
      return jdbcTemplate.query(
          explainSql,
          rs -> {
            if (!rs.next()) {
              throw new BizException("EXPLAIN 未返回结果");
            }
            String json = rs.getString(1);
            if (!StringUtils.hasText(json)) {
              throw new BizException("EXPLAIN 结果为空");
            }
            return json;
          });
    } catch (DataAccessException ex) {
      log.warn("目标库 EXPLAIN 执行失败");
      throw new BizException("EXPLAIN 执行失败: " + ex.getMostSpecificCause().getMessage());
    }
  }
}
