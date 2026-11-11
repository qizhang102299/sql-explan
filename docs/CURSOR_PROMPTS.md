# Cursor 分阶段提示词

## 使用前

1. 用 Cursor 打开目录 `/Users/zq/project/code/sql-explan`，确认 `.cursor/rules/java-conventions.mdc` 已生效。
2. 本机默认 JDK 是 8，而 Spring Boot 3 需要 17 及以上。已装 JDK 21，请先执行：
   `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`
   并在 Cursor 终端里保持该设置。
3. 本机没有 mysql 客户端，但有 Docker。开发用 MySQL 8 请用 `docker compose` 启动，端口避开已占用的 80 和 8080（后端用 8081，MySQL 用 3307 和 3308）。
4. 一次只发一个阶段，用 Agent 模式。每阶段结束先跑测试并 review，再进入下一阶段。
5. 每条提示词都隐含以下公共要求，已在规则文件中，不用重复：中文注释、Req/Res 命名、Swagger、Long 毫秒时间、禁止行尾注释、影响范围说明。

---

## 阶段 0：脚手架

```
请阅读 PLAN.md 与 .cursor/rules，实现「阶段 0」。

要求：
1. 在 backend/ 下创建 Maven 工程（Java 17，Spring Boot 3.x，包名 com.sqlexplan），依赖：web、validation、springdoc-openapi、mybatis-plus、mysql-connector-j、jsqlparser、lombok、spring-boot-starter-test、testcontainers(mysql)。
2. 统一返回体 Result<T>（code、message、data、timestamp 为 Long 毫秒），全局异常处理（BizException + 参数校验异常），traceId 日志。
3. 本系统库表脚本 backend/src/main/resources/db/init.sql：
   - datasource_config（id、name、host、port、db_name、username、password_encrypted、created_time、updated_time，时间为 BIGINT 毫秒）
   - sql_analyze_record（id、datasource_id、sql_text、sql_fingerprint、result_json、cost_ms、created_time）
4. 提供 docker-compose.yml：本系统库 MySQL 8（宿主端口 3307）与演示目标库 MySQL 8（宿主端口 3308）。
5. 服务端口 8081，Swagger 可访问。
6. 写启动与 Result 的单元测试。

完成后列出改动文件与影响范围，并给出启动验证步骤。
```

## 阶段 1：数据源与安全校验

```
请阅读 PLAN.md 的 4.1 与 5 节，实现「阶段 1」。

1. 数据源 CRUD 与连通性测试接口：DataSourceSaveReq、DataSourceRes、DataSourceTestReq、DataSourceTestRes。密码用 AES 加密存储，密钥来自配置，响应中永不返回密码。
2. 动态连接管理（按数据源 id 缓存 HikariDataSource，只读，连接超时与查询超时可配置）。
3. SqlSafetyChecker：基于 JSqlParser，仅允许单条 SELECT；拒绝多语句、DDL、DML、FOR UPDATE、INTO OUTFILE/DUMPFILE、SLEEP、BENCHMARK、LOAD_FILE 等；解析失败也拒绝并返回明确原因。
4. 单元测试：至少 30 条用例覆盖正反例。

完成后列出改动文件与影响范围。
```

## 阶段 2：SQL 解析与元数据

```
请阅读 PLAN.md，实现「阶段 2」。

1. SqlParseService：用 JSqlParser 提取表（含别名）、查询列、JOIN（类型与条件）、WHERE 条件（列、运算符、是否函数包裹）、GROUP BY、ORDER BY、LIMIT/OFFSET、子查询、CTE、聚合函数。输出结构化对象 SqlStructure。
2. SchemaMetaService：从目标库 information_schema 获取涉及表的列、索引、行数估计、字段基数（STATISTICS.CARDINALITY），带短期缓存。
3. SQL 指纹（参数化后哈希）工具类。
4. 单元测试：多表 JOIN、子查询、CTE、UNION 等。

完成后列出改动文件与影响范围。
```

## 阶段 3：执行计划与规则解读

```
请阅读 PLAN.md，实现「阶段 3」。

1. ExplainService：执行 EXPLAIN FORMAT=JSON，解析为 PlanNode 树（表、访问类型、key、rows、filtered、extra）。
2. PlanInterpreter 规则：全表扫描 ALL、索引未命中、filesort、临时表、扫描行数过大、隐式类型转换、索引列被函数包裹、深分页。每个问题含严重级别与白话说明。
3. 单元测试基于样例 JSON，不依赖真实数据库。

完成后列出改动文件与影响范围。
```

## 阶段 4：索引建议与规则改写

```
请阅读 PLAN.md 的 4.2、4.3，实现「阶段 4」。

1. IndexAdvisor：等值列→范围列→排序列的联合索引顺序；JOIN 关联列；覆盖索引机会；已有索引的最左前缀复用判断；冗余索引识别。输出 CREATE INDEX 语句、理由、预期收益、写入代价。
2. SqlRewriter 规则版：SELECT * 精简、OR 转 UNION ALL、IN 子查询转 EXISTS/JOIN、深分页延迟关联、去除无谓 DISTINCT/ORDER BY。
3. 建立 20 条以上坏 SQL 用例集（放在 src/test/resources），断言建议正确。

完成后列出改动文件与影响范围。
```

## 阶段 5：LLM 集成

```
请阅读 PLAN.md 的 4.5，实现「阶段 5」。

1. LlmClient：调用 Claude API（模型 claude-sonnet-5-5，API Key 从环境变量读取，不得写入代码或日志），支持超时、重试、降级。
2. prompt 模板放 resources/prompts，要求模型输出固定 JSON 结构；解析失败要重试一次再降级。
3. 只发送 SQL、表结构、执行计划，不发送任何数据行与数据源密码。
4. 生成语义分析、执行计划白话解释、增强索引建议、优化后 SQL。
5. 对 AI 改写的 SQL 依次做：安全校验→EXPLAIN→结果等价校验（阶段 6 提供），未通过则丢弃并说明。
6. 结果缓存：SQL 指纹 + 表结构版本。
7. 关闭 LLM 时（配置开关）整个流程仍可用，并在响应中标注未使用 AI。

完成后列出改动文件与影响范围。
```

## 阶段 6：耗时对比与总编排

```
请阅读 PLAN.md 的 4.4 与 5 节，实现「阶段 6」。

1. BenchmarkService：预热 1 次，原 SQL 与优化 SQL 交替各执行 N 次（默认 5，最大 20），统计中位数、P95、最小、最大、提升比例；单次超时与行数上限；结果集排序后做校验和对比行数与内容是否一致，不一致告警。
2. AnalyzeOrchestrator 串起全流程，实现 POST /api/sql/analyze（SqlAnalyzeReq / SqlAnalyzeRes）及历史分页、详情接口，记录落库。
3. 集成测试：Testcontainers MySQL，造数据（10 万行以上）验证建议索引与改写后耗时确有下降。

完成后列出改动文件与影响范围。
```

## 阶段 7：前端

```
请阅读 PLAN.md，在 frontend/ 实现「阶段 7」：Vue 3 + Vite + TypeScript + Element Plus + Monaco Editor。

1. 页面：数据源管理；分析页（SQL 编辑器、选择数据源、是否基准测试与次数、分析按钮）；历史记录。
2. 分析结果分五个标签：语义分析、执行计划（树形展示 + 问题高亮）、索引建议（可复制 DDL）、优化 SQL（左右差异对比）、耗时对比（柱状图 + 提升比例）。
3. 接口调用统一封装；时间字段是毫秒时间戳，前端格式化展示。
4. 开发代理指向 http://localhost:8081。

完成后给出启动步骤与截图说明。
```

## 阶段 8：测试与交付

```
请阅读 PLAN.md，实现「阶段 8」。

1. 演示用造数脚本（用户表、订单表等百万级）与 10 条典型慢 SQL 示例。
2. 后端与前端 Dockerfile，完善 docker-compose 一键启动（避开 80、8080 端口）。
3. README：架构图、启动方式、环境变量（含 API Key 和 AES 密钥）、安全说明、局限性。
4. 全量测试通过，列出覆盖情况。
```
