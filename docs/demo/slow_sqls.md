# 演示慢 SQL 示例（目标库 demo / users + orders）

造数后导入数据源（host `127.0.0.1`，port `3308`，database `demo`），在分析页逐条执行下列 SQL 验证规则命中。

| # | SQL | 预期命中规则 |
| --- | --- | --- |
| 1 | `SELECT * FROM orders WHERE user_id = 123` | `FULL_TABLE_SCAN` / `LARGE_ROWS_EXAMINED`；索引建议 `CREATE_INDEX`（`user_id`） |
| 2 | `SELECT id, status, amount FROM orders WHERE status = 2` | 全表或大范围扫描；`COVERING_INDEX` 或联合索引建议（`status` + 投影列） |
| 3 | `SELECT o.id, o.amount FROM orders o JOIN users u ON o.user_id = u.id WHERE u.phone LIKE '%8888'` | `LIKE_LEADING_WILDCARD`；JOIN 侧缺索引时 `INDEX_NOT_USED` |
| 4 | `SELECT user_id, COUNT(*) FROM orders GROUP BY user_id ORDER BY COUNT(*) DESC` | `FILESORT` 或 `TEMPORARY_TABLE`；`GROUP BY`/`ORDER BY` 联合索引提示 |
| 5 | `SELECT id FROM orders WHERE status IN (1, 2, 3) OR amount > 500` | `NON_SARGABLE_OPERATOR`（`OR`）；`SqlRewriter` 可能建议 `UNION ALL` |
| 6 | `SELECT id FROM orders ORDER BY id LIMIT 20 OFFSET 50000` | `DEEP_PAGINATION`；改写建议延迟关联 |
| 7 | `SELECT id FROM users WHERE YEAR(FROM_UNIXTIME(created_time / 1000)) = 2024` | `INDEX_COLUMN_FUNCTION`（函数包裹列） |
| 8 | `SELECT id FROM users WHERE phone = 13800138000` | `IMPLICIT_TYPE_CONVERSION`（`varchar` 列与未引号数字比较） |
| 9 | `SELECT id FROM orders WHERE user_id NOT IN (SELECT user_id FROM orders WHERE status = 4)` | `NON_SARGABLE_OPERATOR`（`NOT IN`）；关联子查询时 `CORRELATED_SUBQUERY` |
| 10 | `SELECT COUNT(*) FROM orders` | `COUNT_STAR_LARGE_TABLE`（大表精确 COUNT） |

## 使用说明

1. 执行 [seed.sql](./seed.sql) 完成造数。
2. 后端默认关闭 LLM，仅走规则引擎；需语义白话增强时再开启 `sqlexplan.llm.enabled=true` 并配置 `ANTHROPIC_API_KEY`。
3. 基准测试对目标库真实执行 SQL，请在测试库操作；临时索引验证需同时开启配置项 `SQLEXPLAN_BENCHMARK_ALLOW_TEMP_INDEX_VERIFICATION=true`。
