package com.sqlexplan.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置。
 */
@Configuration
@MapperScan("com.sqlexplan.mapper")
public class MybatisPlusConfig {
}
