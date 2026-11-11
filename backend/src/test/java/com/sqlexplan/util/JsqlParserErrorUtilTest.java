package com.sqlexplan.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link JsqlParserErrorUtil} 启发式检测单测。
 */
class JsqlParserErrorUtilTest {

  @Test
  void detectsUnbalancedParenthesis() {
    String msg = JsqlParserErrorUtil.detectUnbalancedParentheses("SELECT (1 + 2 FROM t");
    assertThat(msg).contains("括号");
  }

  @Test
  void detectsUnclosedQuote() {
    String msg = JsqlParserErrorUtil.detectUnbalancedParentheses("SELECT 'abc FROM t");
    assertThat(msg).contains("单引号");
  }

  @Test
  void detectsTrailingComma() {
    String msg = JsqlParserErrorUtil.detectTrailingCommaInSelectList("SELECT id, name, FROM users");
    assertThat(msg).contains("多余");
  }

  @Test
  void detectsWhereMissingExpression() {
    String msg = JsqlParserErrorUtil.detectClauseMissingExpression("SELECT * FROM users WHERE");
    assertThat(msg).contains("WHERE");
  }
}
