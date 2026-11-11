package com.sqlexplan.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 业务配置启用。
 */
@Configuration
@EnableConfigurationProperties(SqlexplanProperties.class)
public class SqlexplanConfiguration {
}
