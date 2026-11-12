package com.sqlexplan.manager;

import com.sqlexplan.config.SqlexplanProperties;
import com.sqlexplan.exception.BizException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * 数据源密码 AES-GCM 加解密。
 */
@Component
public class AesCryptoManager {

  private static final String TRANSFORMATION = "AES/GCM/NoPadding";

  private static final int GCM_IV_LENGTH = 12;

  private static final int GCM_TAG_LENGTH = 128;

  private static final int MIN_KEY_LENGTH = 32;

  private static final String PLACEHOLDER_KEYWORD = "changeme";

  private final byte[] aesKeyBytes;

  public AesCryptoManager(SqlexplanProperties sqlexplanProperties) {
    this.aesKeyBytes = resolveKeyBytes(sqlexplanProperties.getCrypto().getAesKey());
  }

  /**
   * 加密明文密码。
   *
   * @param plainText 明文
   * @return Base64 密文（IV + 密文）
   */
  public String encrypt(String plainText) {
    if (plainText == null) {
      throw new BizException("密码不能为空");
    }
    try {
      byte[] iv = new byte[GCM_IV_LENGTH];
      SecureRandom secureRandom = new SecureRandom();
      secureRandom.nextBytes(iv);

      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      SecretKeySpec keySpec = new SecretKeySpec(aesKeyBytes, "AES");
      cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
      byte[] cipherBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

      byte[] combined = new byte[iv.length + cipherBytes.length];
      System.arraycopy(iv, 0, combined, 0, iv.length);
      System.arraycopy(cipherBytes, 0, combined, iv.length, cipherBytes.length);
      return Base64.getEncoder().encodeToString(combined);
    } catch (Exception ex) {
      throw new BizException("密码加密失败");
    }
  }

  /**
   * 解密密文密码。
   *
   * @param cipherText Base64 密文
   * @return 明文
   */
  public String decrypt(String cipherText) {
    if (cipherText == null || cipherText.isBlank()) {
      throw new BizException("密文无效");
    }
    try {
      byte[] combined = Base64.getDecoder().decode(cipherText);
      if (combined.length <= GCM_IV_LENGTH) {
        throw new BizException("密文格式无效");
      }
      byte[] iv = new byte[GCM_IV_LENGTH];
      System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
      byte[] cipherBytes = new byte[combined.length - GCM_IV_LENGTH];
      System.arraycopy(combined, GCM_IV_LENGTH, cipherBytes, 0, cipherBytes.length);

      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      SecretKeySpec keySpec = new SecretKeySpec(aesKeyBytes, "AES");
      cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
      byte[] plainBytes = cipher.doFinal(cipherBytes);
      return new String(plainBytes, StandardCharsets.UTF_8);
    } catch (BizException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new BizException("密码解密失败");
    }
  }

  private static byte[] resolveKeyBytes(String configuredKey) {
    if (configuredKey == null || configuredKey.isBlank()) {
      throw new IllegalStateException(
          "未配置 AES 密钥：请设置环境变量 SQLEXPLAN_AES_KEY（可用 openssl rand -base64 32 生成）");
    }
    if (configuredKey.toLowerCase(Locale.ROOT).contains(PLACEHOLDER_KEYWORD)) {
      throw new IllegalStateException("AES 密钥不能使用 changeme 等示例占位值，请换成随机生成的密钥");
    }
    if (configuredKey.length() < MIN_KEY_LENGTH) {
      throw new IllegalStateException("AES 密钥长度不足 " + MIN_KEY_LENGTH + " 个字符，请使用 openssl rand -base64 32 生成");
    }
    try {
      byte[] decoded = Base64.getDecoder().decode(configuredKey);
      if (decoded.length == 16 || decoded.length == 24 || decoded.length == 32) {
        return decoded;
      }
    } catch (IllegalArgumentException ignored) {
      // 非 Base64，走派生
    }
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return digest.digest(configuredKey.getBytes(StandardCharsets.UTF_8));
    } catch (Exception ex) {
      throw new BizException("AES 密钥派生失败");
    }
  }
}
