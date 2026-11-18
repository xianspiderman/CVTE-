-- Non-destructive and repeatable. Run against the dedicated project database.
CREATE TABLE IF NOT EXISTS data_source (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(100) NOT NULL UNIQUE,
 type VARCHAR(20) NOT NULL, endpoint VARCHAR(1000), request_method VARCHAR(10) DEFAULT 'GET',
 timeout_ms INT DEFAULT 3000, mapping_config JSON, refresh_interval INT DEFAULT 30,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS scm_todo (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, source_id BIGINT NOT NULL, source_record_id VARCHAR(128) NOT NULL,
 business_no VARCHAR(128) NOT NULL, title VARCHAR(255) NOT NULL, business_type VARCHAR(32) NOT NULL,
 status VARCHAR(32) NOT NULL, owner VARCHAR(100), priority VARCHAR(20), risk_level VARCHAR(20),
 due_at DATETIME, business_url VARCHAR(1000), source_updated_at DATETIME,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 UNIQUE KEY uk_todo_source(source_id,source_record_id), KEY idx_todo_status_due(status,due_at),
 FOREIGN KEY (source_id) REFERENCES data_source(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS import_batch (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, source_id BIGINT NOT NULL, file_name VARCHAR(255),
 success_count INT DEFAULT 0, failure_count INT DEFAULT 0, duplicate_count INT DEFAULT 0,
 imported_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY(source_id) REFERENCES data_source(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS import_error (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, batch_id BIGINT NOT NULL, `row_number` INT NOT NULL,
 reason VARCHAR(1000) NOT NULL, FOREIGN KEY(batch_id) REFERENCES import_batch(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS system_link_template (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, source_id BIGINT NOT NULL, page_code VARCHAR(100) NOT NULL,
 url_template VARCHAR(1000) NOT NULL, UNIQUE KEY uk_link(source_id,page_code),
 FOREIGN KEY(source_id) REFERENCES data_source(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS workbench_config (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, user_key VARCHAR(100) NOT NULL UNIQUE, config_json JSON NOT NULL,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS operation_log (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, operation_type VARCHAR(50) NOT NULL, target VARCHAR(255),
 result VARCHAR(50), created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_tool_log (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, tool_name VARCHAR(100) NOT NULL, arguments JSON, result JSON,
 duration_ms BIGINT, error_message TEXT, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO data_source(name,type,endpoint,mapping_config)
SELECT '本地采购协同 Mock','HTTP','http://127.0.0.1:4010/api/procurement',JSON_OBJECT()
WHERE NOT EXISTS (SELECT 1 FROM data_source WHERE name='本地采购协同 Mock');
INSERT INTO data_source(name,type,mapping_config)
SELECT '本地库存文件','FILE',JSON_OBJECT()
WHERE NOT EXISTS (SELECT 1 FROM data_source WHERE name='本地库存文件');
