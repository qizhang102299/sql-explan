package com.sqlexplan.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sqlexplan.config.SqlexplanProperties;
import org.junit.jupiter.api.Test;

/**
 * AES 加解密与密钥校验测试。
 */
class AesCryptoManagerTest {

  private static final String VALID_KEY = "unit-test-aes-key-0123456789-abcdef";

  private static AesCryptoManager managerWithKey(String key) {
    SqlexplanProperties properties = new SqlexplanProperties();
    properties.getCrypto().setAesKey(key);
    return new AesCryptoManager(properties);
  }

  @Test
  void encryptAndDecryptRoundTrip() {
    AesCryptoManager manager = managerWithKey(VALID_KEY);
    String plain = "secret-password-123";
    String cipher = manager.encrypt(plain);
    assertThat(cipher).isNotEqualTo(plain);
    assertThat(manager.decrypt(cipher)).isEqualTo(plain);
  }

  @Test
  void defaultPropertiesHaveNoKey() {
    assertThat(new SqlexplanProperties().getCrypto().getAesKey()).isEmpty();
  }

  @Test
  void rejectsMissingKey() {
    assertThatThrownBy(() -> managerWithKey("")).isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("SQLEXPLAN_AES_KEY");
    assertThatThrownBy(() -> managerWithKey(null)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectsPlaceholderKey() {
    assertThatThrownBy(() -> managerWithKey("changeme-sqlexplan-dev-aes-key-32b"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("占位");
  }

  @Test
  void rejectsShortKey() {
    assertThatThrownBy(() -> managerWithKey("too-short-key")).isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("长度不足");
  }

  @Test
  void acceptsBase64Key() {
    String base64Key = java.util.Base64.getEncoder().encodeToString(new byte[32]);
    assertThat(managerWithKey(base64Key).decrypt(managerWithKey(base64Key).encrypt("x"))).isEqualTo("x");
  }
}
