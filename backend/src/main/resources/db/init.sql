-- 本系统元数据库初始化脚本（时间字段均为 BIGINT 毫秒时间戳）

CREATE TABLE IF NOT EXISTS datasource_config (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    name VARCHAR(128) NOT NULL COMMENT '数据源名称',
    host VARCHAR(255) NOT NULL COMMENT '主机地址',
    port INT NOT NULL COMMENT '端口',
    db_name VARCHAR(128) NOT NULL COMMENT '数据库名',
    username VARCHAR(128) NOT NULL COMMENT '用户名',
    password_encrypted VARCHAR(512) NOT NULL COMMENT '加密后的密码',
    created_time BIGINT NOT NULL COMMENT '创建时间（毫秒）',
    updated_time BIGINT NOT NULL COMMENT '更新时间（毫秒）',
    PRIMARY KEY (id),
    KEY idx_datasource_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='目标数据源配置';

CREATE TABLE IF NOT EXISTS sql_analyze_record (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    datasource_id BIGINT NOT NULL COMMENT '数据源 ID',
    sql_text TEXT NOT NULL COMMENT '原始 SQL',
    sql_fingerprint VARCHAR(64) NOT NULL COMMENT 'SQL 指纹',
    result_json LONGTEXT COMMENT '分析结果 JSON',
    cost_ms BIGINT COMMENT '分析总耗时（毫秒）',
    created_time BIGINT NOT NULL COMMENT '创建时间（毫秒）',
    PRIMARY KEY (id),
    KEY idx_record_datasource_id (datasource_id),
    KEY idx_record_fingerprint (sql_fingerprint),
    KEY idx_record_created_time (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SQL 分析历史记录';
