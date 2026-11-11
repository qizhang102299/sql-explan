package com.sqlexplan.config;

import com.sqlexplan.constant.LlmConstants;
import com.sqlexplan.model.enums.LlmApiProtocolEnum;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 本系统业务配置项。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "sqlexplan")
public class SqlexplanProperties {

  private final Crypto crypto = new Crypto();

  private final TargetDatasource targetDatasource = new TargetDatasource();

  private final SchemaMeta schemaMeta = new SchemaMeta();

  private final Explain explain = new Explain();

  private final Llm llm = new Llm();

  private final ResultEquivalence resultEquivalence = new ResultEquivalence();

  private final Benchmark benchmark = new Benchmark();

  /**
   * 加解密相关配置。
   */
  @Getter
  @Setter
  public static class Crypto {

    /**
     * AES 密钥（Base64 编码的 32 字节，或任意长度字符串将经 SHA-256 派生）。
     */
    private String aesKey = "changeme-sqlexplan-dev-aes-key-32b";
  }

  /**
   * 目标库动态连接池配置。
   */
  @Getter
  @Setter
  public static class TargetDatasource {

    private int connectionTimeoutMs = 5000;

    private int queryTimeoutSeconds = 30;

    private int maximumPoolSize = 3;

    private int minimumIdle = 0;
  }

  /**
   * 目标库表元数据查询与缓存配置。
   */
  @Getter
  @Setter
  public static class SchemaMeta {

    private int cacheTtlSeconds = 60;
  }

  /**
   * EXPLAIN 与计划解读阈值配置。
   */
  @Getter
  @Setter
  public static class Explain {

    /**
     * 单表单次扫描行数超过该值时告警。
     */
    private long largeScanRowsThreshold = 100_000L;

    /**
     * OFFSET 不低于该值时视为深分页。
     */
    private long deepPaginationOffsetThreshold = 10_000L;
  }

  /**
   * LLM 调用与缓存配置（API Key 仅从环境变量读取，不在此配置）。
   */
  @Getter
  @Setter
  public static class Llm {

    /**
     * 是否启用 LLM 增强；关闭时全流程降级并标注未使用 AI。
     */
    private boolean enabled = false;

    /**
     * API 根地址（不含路径），Anthropic 默认 https://api.anthropic.com。
     */
    private String baseUrl = LlmConstants.DEFAULT_BASE_URL;

    /**
     * 模型标识。
     */
    private String modelId = LlmConstants.DEFAULT_MODEL_ID;

    /**
     * HTTP 协议：Anthropic Messages 或 OpenAI 兼容 Chat Completions。
     */
    private LlmApiProtocolEnum protocol = LlmApiProtocolEnum.ANTHROPIC;

    private int connectTimeoutMs = 10_000;

    private int readTimeoutMs = 120_000;

    private int maxOutputTokens = 8192;

    /**
     * LLM 结构化结果缓存 TTL（秒），键为 SQL 指纹 + 表结构版本。
     */
    private int cacheTtlSeconds = 3600;
  }

  /**
   * 结果等价校验阈值。
   */
  @Getter
  @Setter
  public static class ResultEquivalence {

    /**
     * 参与内容校验和的最大行数；超出则仅比对总行数。
     */
    private int maxRowsForChecksum = 5_000;
  }

  /**
   * 基准测试与索引实库验证配置。
   */
  @Getter
  @Setter
  public static class Benchmark {

    /**
     * 单次执行拉取的最大行数（通过 JDBC maxRows 限制）。
     */
    private int maxRowsPerExecution = 50_000;

    /**
     * 全局默认是否允许在目标库创建临时索引验证（请求参数可覆盖）。
     */
    private boolean allowTempIndexVerification = false;
  }
}
