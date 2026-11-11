package com.sqlexplan.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger 文档配置。
 */
@Configuration
public class OpenApiConfig {

  /**
   * 注册 OpenAPI 元信息。
   *
   * @return OpenAPI 实例
   */
  @Bean
  public OpenAPI sqlExplanOpenApi() {
    return new OpenAPI()
        .info(new Info()
            .title("SQL 智能分析与优化系统")
            .description("SQL 语义分析、执行计划解读、索引建议与优化")
            .version("0.1.0")
            .contact(new Contact().name("sqlexplan")));
  }
}
