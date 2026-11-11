# SQL 智能分析与优化系统 开发计划

## 1. 目标

输入一段 SQL，输出：

1. SQL 语义分析
2. 执行计划解释
3. 索引建议
4. 优化后的 SQL
5. 优化前后执行耗时对比

## 2. 技术选型（默认，可调整）

| 层 | 选型 | 说明 |
| --- | --- | --- |
| 语言/框架 | Java 17 + Spring Boot 3.x | 符合团队技术栈 |
| SQL 解析 | JSqlParser | 提取表、列、JOIN、WHERE、GROUP BY、ORDER BY、子查询 |
| 目标数据库 | MySQL 8.0（首期只支持 MySQL） | 使用 `EXPLAIN FORMAT=JSON` 与 `EXPLAIN ANALYZE` |
| 访问方式 | 原生 JDBC（JdbcTemplate） | 动态连接用户配置的目标库，不用 ORM |
| 元数据存储 | 本系统自有库（MySQL）+ MyBatis-Plus | 存数据源配置、分析历史 |
| AI 能力 | Claude API（claude-sonnet-5-5） | 语义解释、执行计划解读、SQL 改写；规则引擎做兜底 |
| 接口文档 | springdoc-openapi（Swagger 注解） | |
| 前端 | Vue 3 + Vite + Element Plus + Monaco Editor | SQL 编辑器、结果分栏展示 |

## 3. 总体架构

```
前端 → AnalyzeController → AnalyzeOrchestrator（编排）
        ├─ SqlParseService        SQL 解析与语义结构提取（JSqlParser）
        ├─ SqlSafetyChecker       安全校验（只允许单条 SELECT）
        ├─ SchemaMetaService      表结构、索引、行数、基数统计
        ├─ ExplainService         执行 EXPLAIN，结构化执行计划
        ├─ PlanInterpreter        执行计划规则解读（全表扫描、filesort、临时表等）
        ├─ IndexAdvisor           索引建议（规则 + AI）
        ├─ SqlRewriter            SQL 改写（规则 + AI）
        ├─ BenchmarkService       优化前后耗时对比
        └─ LlmClient              Claude API 封装（超时、重试、降级）
```

处理流程：

1. 校验：仅允许单条 SELECT，拒绝多语句、DDL、DML、`INTO OUTFILE`、`SLEEP` 等。
2. 解析：得到表、字段、条件、连接、聚合、排序、子查询结构。
3. 拉取元数据：涉及表的 DDL、索引、行数、字段基数。
4. 执行 EXPLAIN：结构化计划，规则识别问题点。
5. 调用 LLM：输入「SQL + 结构 + 表结构 + 执行计划」，输出语义说明、计划解释、索引建议、改写 SQL（要求 JSON 结构化输出）。
6. 验证：对改写 SQL 再 EXPLAIN，并校验结果等价性。
7. 基准测试：对比耗时，生成报告。

## 4. 关键设计要点

### 4.1 安全（最高优先级）

- 目标库使用只读账号；连接设置 `readOnly=true`。
- 执行前强校验：单条 `SELECT`，禁止 `FOR UPDATE`、`INTO`、写函数。
- 每次执行设置超时（如 `MAX_EXECUTION_TIME` 提示或 `Statement.setQueryTimeout`）与最大行数。
- 数据源密码加密存储，日志与 LLM 请求中不得出现密码与业务数据行。
- 发给 LLM 的只有 SQL、表结构、执行计划，不含真实数据。

### 4.2 索引建议

- 规则：WHERE 等值列优先 → 范围列 → ORDER BY/GROUP BY 列；JOIN 关联列；覆盖索引机会；最左前缀；识别冗余/重复索引。
- 建议以 `CREATE INDEX` 语句形式给出，并说明理由与预期收益、写入代价。
- 可选：用 MySQL 8 不可见索引或在影子库验证，不直接在目标库建索引。

### 4.3 SQL 改写

常见规则：`SELECT *` 精简、函数作用于索引列、隐式类型转换、`OR` 改 `UNION ALL`、`IN` 子查询改 `JOIN/EXISTS`、深分页改延迟关联、`LIKE '%x'`、无谓的 `DISTINCT`/`ORDER BY`。
AI 改写结果必须通过：语法校验 → EXPLAIN → 结果等价校验，否则丢弃。

### 4.4 耗时对比

- 预热 1 次，之后各执行 N 次（默认 5），取中位数、P95、最小、最大。
- 原 SQL 与优化 SQL 交替执行，降低缓存偏差。
- 对比返回行数与结果集校验和（对结果排序后哈希）判断等价。
- 大结果集只计数不拉取明细，或包一层 `SELECT COUNT(*)`/校验和。
- 索引建议的收益：在测试/影子库建索引后再测，或仅给出基于 `EXPLAIN` 的预估，需在报告中标明。

### 4.5 降级

LLM 不可用时，仍返回规则引擎的解析、计划解读、索引与改写建议，并标注「未使用 AI」。

## 5. 接口设计（命名遵循 Req/Res，时间一律 Long 毫秒）

| 接口 | 入参 | 出参 |
| --- | --- | --- |
| `POST /api/datasource/save` | `DataSourceSaveReq` | `DataSourceRes` |
| `POST /api/datasource/test` | `DataSourceTestReq` | `DataSourceTestRes` |
| `POST /api/sql/analyze` | `SqlAnalyzeReq`（sql、dataSourceId、是否开启基准测试、重复次数） | `SqlAnalyzeRes` |
| `POST /api/sql/history/page` | `SqlHistoryPageReq` | `SqlHistoryPageRes` |
| `POST /api/sql/history/detail` | `SqlHistoryDetailReq` | `SqlAnalyzeRes` |

`SqlAnalyzeRes` 结构：

- `semantic`：语义分析（意图概述、涉及表、过滤、连接、聚合、排序、潜在风险）
- `plan`：执行计划（结构化节点、问题点、白话解释）
- `indexAdvices`：索引建议列表（DDL、理由、预期收益、写入代价）
- `optimizedSql`：优化后 SQL、改写说明、是否通过等价校验
- `benchmark`：优化前后耗时（中位数、P95、提升比例、行数是否一致）
- `createdTime`：Long 毫秒时间戳

## 6. 工程结构

```
sql-explan/
├─ backend/   Spring Boot 工程（com.xxx.sqlexplan）
│  ├─ controller / service / service.impl / manager / mapper
│  ├─ model/entity / model/req / model/res / model/enums
│  ├─ config / exception / util
│  └─ resources（application.yml、sql 初始化脚本、LLM prompt 模板）
├─ frontend/  Vue 3 工程
├─ docs/
├─ PLAN.md
└─ .cursor/rules/
```

## 7. 分阶段计划（每阶段可独立交付、可验收）

### 阶段 0：脚手架（0.5 天）
- 初始化 Spring Boot 工程、统一返回体、全局异常、Swagger、日志。
- 本系统库表：`datasource_config`、`sql_analyze_record`（时间字段均为 BIGINT 毫秒）。
- 验收：服务启动，Swagger 可访问。

### 阶段 1：数据源与安全校验（1 天）
- 数据源增删改查与连通性测试，密码加密。
- `SqlSafetyChecker` 及单元测试（覆盖各类危险语句）。
- 验收：非 SELECT 语句一律被拒绝。

### 阶段 2：SQL 解析 + 元数据（1.5 天）
- `SqlParseService` 提取结构；`SchemaMetaService` 获取表结构/索引/行数。
- 验收：复杂 SQL（多表 JOIN、子查询、CTE）可正确提取结构。

### 阶段 3：执行计划 + 规则解读（1.5 天）
- `ExplainService` 解析 `EXPLAIN FORMAT=JSON`；`PlanInterpreter` 规则识别问题。
- 验收：能识别全表扫描、未使用索引、filesort、临时表、扫描行数过大。

### 阶段 4：索引建议 + 规则改写（2 天）
- `IndexAdvisor`、`SqlRewriter` 规则版。
- 验收：典型坏 SQL 用例集（至少 20 条）给出正确建议。

### 阶段 5：LLM 集成（2 天）
- `LlmClient`、prompt 模板、JSON 结构化输出解析、重试与降级。
- 语义分析与计划白话解释；AI 增强索引与改写；改写结果的 EXPLAIN + 等价校验。
- 验收：关闭 LLM 时系统仍可用；AI 改写错误结果会被拦截。

### 阶段 6：耗时对比（1.5 天）
- `BenchmarkService`：预热、交替执行、统计、等价校验、超时保护。
- 验收：报告包含中位数/P95/提升比例，结果不一致时给出告警。

### 阶段 7：前端（2 天）
- Monaco 编辑器输入 SQL，选择数据源，分栏展示五类结果，耗时对比图表，历史记录。

### 阶段 8：测试与交付（1.5 天）
- 基于 Testcontainers MySQL 的集成测试；造数脚本（百万级表）用于基准测试演示。
- Docker Compose 一键启动；README 与演示用例。

合计约 13 天，可按阶段顺序逐个交给 Cursor。

## 8. 给 Cursor 的使用方式

1. 将本文件与 `.cursor/rules/` 放入项目，Cursor 会自动加载规则。
2. 每次只给 Cursor 一个阶段，提示词模板：
   > 请阅读 PLAN.md，实现「阶段 N」。严格遵守 .cursor/rules 中的规范，完成后列出改动文件与影响范围，并补充单元测试。
3. 每个阶段结束先跑测试、人工 review，再进入下一阶段。
4. 阶段 5 的 prompt 模板单独评审，避免把真实业务数据发送给外部服务。

## 9. 风险与待确认

| 风险/待确认 | 处理建议 |
| --- | --- |
| 是否只支持 MySQL | 已确认：首期仅 MySQL，`ExplainService` 抽象接口，后续可扩展 PostgreSQL |
| 是否允许在目标库真实执行 SQL | 已确认：目标库为测试库，允许真实执行基准测试；仍保留只读账号、超时与行数上限 |
| 前端技术栈 | 已确认：Vue 3 + Vite + Element Plus + Monaco Editor |
| AI 改写语义不等价 | 必须经结果校验和人工确认，界面标注风险 |
| 慢 SQL 执行拖垮目标库 | 超时、行数上限、并发限制 |
| LLM 调用成本与时延 | 结果缓存（SQL 指纹 + 表结构版本），设置超时 |
