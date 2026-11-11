package com.sqlexplan.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * {@link SqlTextUtil} 单元测试。
 */
class SqlTextUtilTest {

  @Test
  void stripsTrailingSemicolonAndSpaces() {
    assertEquals("SELECT * FROM users", SqlTextUtil.stripTrailingSemicolons("SELECT * FROM users ;"));
    assertEquals("SELECT 1", SqlTextUtil.stripTrailingSemicolons("  SELECT 1 ;; \n"));
  }

  @Test
  void keepsMiddleSemicolon() {
    assertEquals("SELECT 1; SELECT 2", SqlTextUtil.stripTrailingSemicolons("SELECT 1; SELECT 2;"));
  }

  @Test
  void handlesNullAndEmpty() {
    assertEquals("", SqlTextUtil.stripTrailingSemicolons(null));
    assertEquals("", SqlTextUtil.stripTrailingSemicolons(" ; "));
  }
}
