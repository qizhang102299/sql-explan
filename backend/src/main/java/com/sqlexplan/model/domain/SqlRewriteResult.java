package com.sqlexplan.model.domain;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * SQL 规则改写结果。
 */
@Data
public class SqlRewriteResult {

  private String originalSql;

  private List<SqlRewriteSuggestion> suggestions = new ArrayList<>();

  /**
   * 在可安全自动改写时给出的推荐 SQL；否则与 originalSql 相同。
   */
  private String recommendedSql;
}
