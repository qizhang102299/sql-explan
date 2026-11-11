-- 演示库造数脚本：users 约 2 万行，orders 约 20 万行
-- 在目标库 demo（端口 3308）执行：mysql -h127.0.0.1 -P3308 -uroot -proot demo < docs/demo/seed.sql
SET SESSION cte_max_recursion_depth = 300000;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS users;
CREATE TABLE users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(50) NOT NULL,
  phone VARCHAR(20),
  created_time BIGINT NOT NULL
) ENGINE=InnoDB;
CREATE TABLE orders (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  status TINYINT NOT NULL,
  amount DECIMAL(10, 2) NOT NULL,
  remark VARCHAR(100),
  created_time BIGINT NOT NULL
) ENGINE=InnoDB;
INSERT INTO users (name, phone, created_time)
WITH RECURSIVE s(n) AS (
  SELECT 1 UNION ALL SELECT n + 1 FROM s WHERE n < 20000
)
SELECT CONCAT('user', n), CONCAT('138', LPAD(n, 8, '0')), 1700000000000 + n * 1000 FROM s;
INSERT INTO orders (user_id, status, amount, remark, created_time)
WITH RECURSIVE s(n) AS (
  SELECT 1 UNION ALL SELECT n + 1 FROM s WHERE n < 200000
)
SELECT
  1 + (n % 20000),
  n % 5,
  (n % 1000) / 10,
  CONCAT('r', n),
  1700000000000 + n * 1000
FROM s;
ANALYZE TABLE users, orders;
