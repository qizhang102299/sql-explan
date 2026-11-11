package com.sqlexplan.manager;

import com.sqlexplan.config.SqlexplanProperties;
import com.sqlexplan.model.domain.LlmStructuredAnalysis;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * LLM 结构化分析结果内存缓存（键：SQL 指纹 + schema 版本）。
 */
@Slf4j
@Component
public class LlmResponseCacheManager {

  private final SqlexplanProperties sqlexplanProperties;

  private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

  public LlmResponseCacheManager(SqlexplanProperties sqlexplanProperties) {
    this.sqlexplanProperties = sqlexplanProperties;
  }

  /**
   * 构建缓存键。
   *
   * @param sqlFingerprint SQL 指纹
   * @param schemaVersion  表结构版本
   * @return 缓存键
   */
  public static String buildCacheKey(String sqlFingerprint, String schemaVersion) {
    return sqlFingerprint + ":" + schemaVersion;
  }

  /**
   * 读取缓存。
   *
   * @param cacheKey 缓存键
   * @return 命中则返回副本语义上的同一对象，未命中返回 null
   */
  public LlmStructuredAnalysis get(String cacheKey) {
    if (!StringUtils.hasText(cacheKey)) {
      return null;
    }
    long ttlMs = sqlexplanProperties.getLlm().getCacheTtlSeconds() * 1000L;
    long now = System.currentTimeMillis();
    CacheEntry entry = cache.get(cacheKey);
    if (entry == null || entry.expiresAtMs <= now) {
      if (entry != null) {
        cache.remove(cacheKey);
      }
      return null;
    }
    log.debug("命中 LLM 分析缓存");
    return entry.analysis;
  }

  /**
   * 写入缓存。
   *
   * @param cacheKey 缓存键
   * @param analysis 结构化分析
   */
  public void put(String cacheKey, LlmStructuredAnalysis analysis) {
    if (!StringUtils.hasText(cacheKey) || analysis == null) {
      return;
    }
    long ttlMs = sqlexplanProperties.getLlm().getCacheTtlSeconds() * 1000L;
    long expiresAt = System.currentTimeMillis() + ttlMs;
    cache.put(cacheKey, new CacheEntry(analysis, expiresAt));
  }

  private record CacheEntry(LlmStructuredAnalysis analysis, long expiresAtMs) {
  }
}
