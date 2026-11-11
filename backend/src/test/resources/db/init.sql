-- Testcontainers 初始化脚本（与主库结构一致）

CREATE TABLE IF NOT EXISTS datasource_config (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    host VARCHAR(255) NOT NULL,
    port INT NOT NULL,
    db_name VARCHAR(128) NOT NULL,
    username VARCHAR(128) NOT NULL,
    password_encrypted VARCHAR(512) NOT NULL,
    created_time BIGINT NOT NULL,
    updated_time BIGINT NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sql_analyze_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    datasource_id BIGINT NOT NULL,
    sql_text TEXT NOT NULL,
    sql_fingerprint VARCHAR(64) NOT NULL,
    result_json LONGTEXT,
    cost_ms BIGINT,
    created_time BIGINT NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
