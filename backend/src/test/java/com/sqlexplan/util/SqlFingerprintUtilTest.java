package com.sqlexplan.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link SqlFingerprintUtil} 单元测试。
 */
class SqlFingerprintUtilTest {

  @Test
  void parameterizeReplacesLiterals() {
    String sql = "SELECT * FROM users WHERE id = 1 AND name = 'alice'";
    String parameterized = SqlFingerprintUtil.parameterize(sql);
    assertThat(parameterized).doesNotContain("'alice'");
    assertThat(parameterized).contains("?");
    assertThat(parameterized.toUpperCase()).contains("FROM USERS");
  }

  @Test
  void fingerprintStableForSameShape() {
    String a = "SELECT id FROM t WHERE x = 1 AND y = 'a'";
    String b = "SELECT id FROM t WHERE x = 99 AND y = 'zzz'";
    assertThat(SqlFingerprintUtil.fingerprint(a)).isEqualTo(SqlFingerprintUtil.fingerprint(b));
  }

  @Test
  void fingerprintDiffersWhenStructureDiffers() {
    String a = "SELECT id FROM t WHERE x = 1";
    String b = "SELECT id FROM t WHERE x = 1 AND z = 2";
    assertThat(SqlFingerprintUtil.fingerprint(a)).isNotEqualTo(SqlFingerprintUtil.fingerprint(b));
  }
}
