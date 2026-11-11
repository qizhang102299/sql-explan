package com.sqlexplan.manager;

import static org.assertj.core.api.Assertions.assertThat;

import com.sqlexplan.config.SqlexplanProperties;
import org.junit.jupiter.api.Test;

/**
 * AES 加解密往返测试。
 */
class AesCryptoManagerTest {

  @Test
  void encryptAndDecryptRoundTrip() {
    SqlexplanProperties properties = new SqlexplanProperties();
    properties.getCrypto().setAesKey("unit-test-aes-key");
    AesCryptoManager manager = new AesCryptoManager(properties);
    String plain = "secret-password-123";
    String cipher = manager.encrypt(plain);
    assertThat(cipher).isNotEqualTo(plain);
    assertThat(manager.decrypt(cipher)).isEqualTo(plain);
  }
}
