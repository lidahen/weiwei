# Weiwei 可交付落地改造设计文档

| 项 | 内容 |
|---|---|
| 文档版本 | v1.0 |
| 日期 | 2026-10-13 |
| 状态 | **待评审**（批准后进入 writing-plans） |
| 范围 | 仓库级：`backend/`（Java）、`ai-service/`（Python）、`ui/`（前端）、`deploy/`、根级配置与文档 |
| 审计依据 | 三端并行交付就绪度审计（只读）+ 线上只读探测 + 本机实测 |
| 关联文档 | [部署交接手册.md](../../../deploy/部署交接手册.md)、[docs/ONBOARDING.md](../../ONBOARDING.md)、[agent.md](../../ai/agent.md)、[agent异常处理.md](../../ai/agent异常处理.md) |

> **本文档不含任何真实密钥值**，只写字段名与位置。这本身就是本次改造的一条纪律（见 D2）。

---

## 1. 背景

### 1.1 起点

Weiwei 是一个**已经在生产运行**的三端商城：

- 服务器 A：`backend`（Java 17 / Spring Boot 3.5.4 单体）+ MySQL + Redis + Caddy，systemd 托管
- 服务器 B：`ai-service`（FastAPI + LangGraph Agent）+ 独立 Redis
- `ui`：Umi Max 4 + React 18，静态产物由 Caddy 托管

也就是说，**功能层面是完整的、上线过的**，不属于 demo。问题不在"做了什么"，而在**"能不能被安全地交接、复现、维护"**。

### 1.2 触发本次改造的两个事实

1. **交付就绪度审计 8 项标准中 6 项不通过**，其中 3 项判定为"阻断交付"（凭据入库、测试实质为零、干净机器不可复现）。
2. **线上 AI 链路当前返回 502**（`http://<生产IP>:8001/health` 实测），而由于健康探针被 SPA 兜底吃掉，**监控不会告警**。

### 1.3 审计方法与证据等级

| 来源 | 方法 | 性质 |
|---|---|---|
| 三端并行审计 | 静态只读审查，逐条 `文件:行号` 核对 | 已核实 |
| 本机实测 | `pytest` 实跑、`git ls-files`、`git log` | 已实测 |
| 线上探测 | 只发 GET（首页 / actuator / 8001 健康 / 不存在路径） | 已实测 |
| 构建/测试实测 | `mvn -o test`、`mvn spring-javaformat:validate`、`jest`、`tsc --noEmit`、`pytest`（2026-10-13 补测） | 已实测（见 3.9） |

**仍然未确认的只剩一项**：根 `docker compose up` 的实际报错文本（按"缺 Dockerfile"这一事实文件核对推断，未实跑——因为实跑需要先补 Dockerfile，本身就是待办）。

**本文所有"未确认"项均已显式标注，不做推测性结论。**

---

## 2. 「可交付」的验收标准（Definition of Done）

这是全文的锚。每条都是**二值判断 + 可执行验证命令**，不通过就不能宣称交付完成。

| # | 标准 | 判定方法（命令即验收） |
|---|---|---|
| **A1** | **凭据零入库** | `git log --all -p -- ai-service/.env \| Select-String 'sk-\|LTAI'` 无真实值；工作区 `git ls-files` 无任何含密钥文件；`.env.example` 与 `Settings` 字段逐项一致 |
| **A2** | **质量门禁真实生效** | 一条命令跑完三端校验（`./verify.sh`），失败即非零退出；该命令被 CI 平台自动调用，并能贴出**一次真实运行记录** |
| **A3** | **测试可信** | 三端测试可一条命令跑完；无静默 skip；缺外部依赖是 `skip` 而非 `error`；登录、下单、支付回调、Agent 确认流程有集成测试 |
| **A4** | **干净机器可复现** | 在全新机器按 `README.md` 从零执行，能起三端并完成一次登录；文档中每条命令可直接复制执行 |
| **A5** | **一键起停 + 可回滚** | `docker compose up -d` 或等价单命令起全套；有明确回滚步骤；产物带 commit 标识 |
| **A6** | **健康探针真实** | `/actuator/health` 与 ai-service `/readyz` 返回 JSON 而非 SPA 首页；依赖挂掉时探针变红 |
| **A7** | **数据库变更版本化** | schema 变更有版本号、可升级、可回滚；新环境一条命令建出完整库；能回答"当前是哪个版本" |
| **A8** | **可观测 + 可运营** | 日志到 stdout 且有轮转上限；关键指标可查；有告警；有备份与恢复演练记录 |

---

## 3. 现状差距

### 3.1 总览

| 标准 | 判定 | 严重级别 | 关键证据 |
|---|---|---|---|
| A1 凭据零入库 | ❌ 不通过 | **阻断交付** | `ai-service/.env` 被跟踪（5 个历史提交）；同组 OSS AK/SK 另在 3 处重复；`deploy/部署交接手册.md` 以表格明文列出生产凭据 |
| A2 质量门禁 | ❌ 不通过 | 高 | 两处 workflow 位于 `backend/.github`、`ui/.github`（非仓库根）；**且远端是 Gitee，GitHub Actions 永远不会触发** |
| A3 测试可信 | ❌ 不通过 | **阻断交付** | backend 仅 **1 个测试文件 / 10 个 `@Test`**；ui 仅 **2 个用例 / 313 个源文件**；ai-service 全量测试**收集即失败**、4 个 async 用例静默跳过 |
| A4 干净机器可复现 | ❌ 不通过 | **阻断交付** | `application-dev.yml` 钉死作者本机 SSH 隧道端口并指向生产库，而它是 pom 默认 profile；README 教的却是本机 `3306` + 一个弱口令（`[REDACTED]`） |
| A5 一键起停 | ❌ 不通过 | 高 | 根 `docker-compose.yml` 引用 `./backend`、`./ui`，二者**均无 Dockerfile** |
| A6 健康探针 | ❌ 不通过 | 高 | 线上实测 `/actuator/health` 与**不存在路径**返回完全相同的 200 + 480B HTML；ai-service `/health` 恒返回 `{"status":"ok"}` |
| A7 数据库版本化 | ❌ 不通过 | **阻断交付** | 全仓无 Flyway/Liquibase；容器化只自动应用 3/26 个脚本；`twelvet.sql` 含 `DROP DATABASE` 却按字母序排第 24/27 |
| A8 可观测 | ❌ 不通过 | 高 | Java 侧无 actuator/micrometer；ai-service 侧**无任何 logging 配置**（应用 `logger.info` 全部丢弃）；两端均无指标与 traceId |

### 3.2 A1 凭据 — 最严重，且时间不可倒流

| 位置 | 内容类型 |
|---|---|
| `ai-service/.env:1,6,11,20,24,32-33` | MySQL 口令、两个模型平台 API Key、Redis 口令、商汤 API Key、**阿里云 OSS AccessKeyId + SecretKey** |
| `backend/twelvet-admin/src/main/resources/application.yml:198-199` | 同一组 OSS AK/SK（作为默认值硬编码） |
| `deploy/application-pro.yml:7,21,27,108-109` | 生产 MySQL、从库、Redis 口令、OSS AK/SK |
| `backend/.../application-dev.yml:10,20,25,54-56,64` | DB/Redis 口令、支付宝与微信支付配置 |
| `backend/docker/docker-compose.yml:28,69` | 明文 `MYSQL_ROOT_PASSWORD` 与内嵌口令的 `DATABASE_URL` |
| `deploy/部署交接手册.md:69-70,78-84` | 以表格形式**再列一遍**生产 MySQL/Redis 口令与 OSS AK/SK |
| `backend/data/twelvet.sql:1023-1030,738` | 预置账号口令哈希（`admin`/`demo` 同哈希）+ 明文 OAuth client secret |
| `ui/src/pages/Login/index.tsx:253-257`、`service.ts:8` | 登录页预填 demo 账号 + 弱口令（`[REDACTED]`）+ 硬编码 Basic 口令，**已打进 `ui/dist`** |

**关键判断：**
- `.env` 在 **5 个提交**里被修改（`07d6bb4` 明确写 "track .env ... (private repo)" → 最近一次 2026-09-30），跨度一个多月。
- **`git rm --cached` 不解决问题** —— 历史里仍然有。唯一有效的止血是**轮换全部凭据**。
- `.gitignore:31-33` 与根 `.env.example:1` 的注释自相矛盾：前者说"有意跟踪"，后者说"not committed"。
- 讽刺的是 `deploy/b/setup-env.sh:7-10` 做对了（要求 `export REDIS_PASS`、不进 git）——**正确做法项目里已有先例，只是没推广**。

### 3.3 A3 测试 — "假绿"比"没测试"更危险

**backend**：`git ls-files` 下 `backend/**/src/test/**` **只有 1 个文件**（`RecommendScorerTest.java`，10 个 `@Test`，纯函数，无 Spring 上下文）。全仓无 `@SpringBootTest`/MockMvc/testcontainers/H2。而 CI 里跑的是 `mvn clean install -DskipTests`。

**ui**：真实用例 2 个（`ShoppingGuide/index.test.tsx`、`service.test.ts`），`jest.config.ts` 无覆盖率阈值。

**ai-service**：本机实测

```
py -3 -m pytest tests -q
→ Interrupted: 9 errors during collection   （9 个收集错误，0 个用例执行）
   3 × ModuleNotFoundError: respx
   6 × Form data requires "python-multipart"
剔除无法收集的模块后：288 passed, 5 skipped, 4 errors
   SKIPPED [4] async def function and no async plugin installed
              → tests/test_community_tools.py:61,66,70,78
   ERROR   4 × No module named 'fakeredis'
```

- 那 4 个被静默跳过的 async 用例里，**`:61,66,70` 正是租户隔离守卫测试**（"无租户上下文必须拒绝，绝不跨租户泄露"）——**从未执行过**。
- `respx`、`fakeredis` 两个测试依赖**未声明**在 `requirements.txt`/`requirements.lock.txt` 中。
- `requirements.lock.txt` 虽存在却**缺 8 个直接依赖**，且 `Dockerfile:10-11` 只装 `requirements.txt` → **lock 从未生效**。
- 无 `pytest.ini` → 无 `asyncio_mode`，这是静默跳过的直接原因。

### 3.4 A4 干净机器不可复现 — 头号阻塞

- `backend/twelvet-admin/src/main/resources/application-dev.yml:1-3` 注释要求先建到生产 IP 的 SSH 隧道；`:8-10` 连 `localhost:13306` + **生产 MySQL 口令（明文，`[REDACTED]`）**；`:24-25` 连 `16379` + **生产 Redis 口令（同上，`[REDACTED]`）**。
  > 2026-10-13 一手核实补充：**同一生产口令在主库与从库（`dynamic.datasource.slave`）两处重复**；且第 1-3 行注释本身就在指示"本地直连时把 13306→3306、16379→6379、**密码改回弱口令/留空即可**" —— 即**配置里既有生产口令、又在教人用弱口令**。
  > 纪律提醒：本文档 v1.0 起草时曾把上述口令原文抄入，靠"文档自检脚本"拦下。**所有文档与提交都必须过一遍密钥扫描**，这条要进 `verify.sh`（见 D1/D2）。
- `backend/pom.xml:541-548` 让 **dev 成为 `activeByDefault`** → `mvn spring-boot:run` 默认就走这条路。
- 但 `README.md:293-295,317` 与 `docs/ONBOARDING.md:99-105` 教的是本机 `3306` + root 弱口令（`[REDACTED]`）、无密码 `6379`。
- **照文档做必然连不上；而 dev 配置里直接写着生产库口令** → 新人还可能误连生产。
- `ai-service/.env:1,20` 同样钉在 `13306`/`16379`，与 ONBOARDING 完全不一致。

### 3.5 A5/A6/A7 部署、探针、数据库

- **compose 起不来**：根 `docker-compose.yml:18,30` `build: ./backend`、`build: ./ui` —— 全仓 Dockerfile 仅 4 个，这两个不存在。`README.md:53` 已自认不可用，文件却仍留在根目录。附带：`MYSQL_ROOT_PASSWORD` 为弱口令（`[REDACTED]`）、无数据卷、无 healthcheck、`depends_on` 无条件语法。
- **健康探针是假的**：线上实测 `/actuator/health` → `200, text/html, 480 bytes`，与 `/this-path-does-not-exist-xyz` **完全一致**；Java 侧也没有 actuator 依赖（`WebLogFilter.java:35` 甚至忽略了一个不存在的 `/actuator/health` 路径）。Caddy 的 SPA 回退把所有未知路径变成 200 → **任何监控拿到的都是假绿灯**。
- **数据库初始化不可靠（2026-10-13 逐文件核实）**：`backend/data/` 共 **27** 个脚本，按 initdb 字母序执行；`twelvet.sql:1` 是 `DROP DATABASE IF EXISTS`、`:3` 才 `CREATE DATABASE`，而它排在第 **24** 位。排在第 4 位的 `billing-init.sql` 就已经 `USE twelvet-fast` —— **此时库还不存在 → 全新挂载 `/docker-entrypoint-initdb.d` 会在第 4 个文件直接报 "Unknown database"**（另有 `neighborswap.sql`(#5)、`neighborswap-roles-v2.sql`(#9)、`tenant-mgmt-ui-init.sql`(#13)、`twelvet_gen.sql`(#25)、`twelvet_job.sql`(#26) 同样以 `USE` 开头）。此外 `README.md:307-311` 的清单漏列 8+ 个脚本、`backend/sql/` 8 个脚本文档完全未提；唯一被跟踪的全量导出比代码版本晚 16 天、少 5 张表。（本节最初写"前 23 个脚本以 USE 开头"，实测后修正为上述精确结论。）
- **生产升级靠手工**：`deploy/部署交接手册.md:665-675` 是手工 `mysqldump` + 导入；该文档自己记录了**一次真实翻车**（`:24` 登录不可用，因 schema 落后于 jar）。

### 3.6 业务正确性缺陷（审计新发现，必须单列）

`ai-service/app/services/agent/tools.py:613-623` 的 `confirm_order` **绕过 Java 域服务直接 INSERT `trade_order`**，与 `TradeOrderServiceImpl.java:122-157` 相比缺三样：

| 缺失 | 后果 |
|---|---|
| `tenant_id` 未写入 | DB 默认值 1（`tenant-isolation-2-init.sql:10`），而 `TradeOrderMapper.xml:63,71,77,84` 全按 `tenant_id` 过滤 → **AI 导购替租户 2 下的单落进租户 1**：租户 2 看不到，租户 1 看到别人的单 |
| `product_snapshot` 未写入 | 订单详情快照为空 |
| `freight` 硬编码 0 | 有运费商品**漏收运费**，`total_amount` 少算 |

另外 `tools.py:632` 等多处 `return json.dumps({"error": str(e)})` 把**原始异常（可能含 SQL 片段、表名、DSN）回吐到用户对话**，并作为不可信文本继续喂给 LLM。

> 这不是"容错不足"，而是**已经存在的数据正确性与跨租户隔离缺陷**，优先级与凭据同级。

**另一个已上线的功能性缺陷（本轮实测发现）**：`ui/src/pages/System/Tenant/index.tsx:352` 的「详情」按钮调用 `openDetail(row)`，但该函数在本文件中**从未定义、也未 import**（全仓 grep 仅此一处引用；同名函数只存在于另一个页面 `AI/DailyReport/index.tsx:196`）。→ **租户管理页每一行的「详情」按钮点下去都抛 `ReferenceError`**。`max build`（umi/webpack）不做类型检查，所以它一路进了生产，而 `tsc` 能抓到（见 3.9）。

### 3.7 运维能力

- Java 侧：无 actuator；无 `server.shutdown`（**重启会切断在途支付回调**，造成"支付成功但订单未履约"）；`logback-spring.xml:25-44` 无 `totalSizeCap`（磁盘无上限，而机器是 2C2G）。
- ai-service 侧：**无任何 logging 配置** → 应用所有 `logger.info` 被丢弃；`/health` 不探 DB/Redis；无优雅停机（`engine.dispose()`/Redis 关闭全缺）；`redis_client.py:13-37` 的探测结果**永久缓存**（一次启动竞态失败 → 整进程退化为内存态 → 幂等键丢失 → **重复下单窗口**）；`app/config.py` 82 个字段全有默认值、启动不校验 → 配错不报错。
- 两端皆无指标、无 traceId、无错误追踪。

### 3.8 三处需要纠正的既有认知

1. **"CI 只要挪到根目录就行"——不够。** 远端是 `gitee.com`，GitHub Actions 根本不适用于该仓库。需要 Gitee Go、自建 runner 或 Jenkins 三选一（见 D1）。
2. **`ui/.env` 不含密钥**（只有 2 行注释），真正的凭据问题在**登录页源码**（`Login/index.tsx`、`Login/service.ts`）。
3. **`backend/data/*.sql` 的中文没有乱码** —— 审计中出现的乱码是读取工具的编码假象，逐文件 UTF-8 校验后确认文件本身正常。这一点必须澄清，否则会去做无用功。

### 3.9 构建与测试实测基线（2026-10-13 补测）

上一版标注为"未确认"的构建验证已全部实测。**结论：三端的质量门禁今天没有任何一条能变绿。**

| 检查 | 命令 | 结果 | 判定 |
|---|---|---|---|
| backend 构建+测试 | `mvn -B -o test`（Maven 3.9.9 / JDK 17.0.8） | **BUILD SUCCESS**，42 模块，38.1s | ✅ 能编译 |
| backend 测试覆盖 | 同上 + `surefire-reports` | `Tests run: 10, Failures: 0`（1 个文件 / 10 个用例 / 0.065s） | ❌ 覆盖≈0 |
| backend 格式校验（**CI 第一条命令**） | `mvn -B -o spring-javaformat:validate` | **BUILD FAILURE**，3 个文件违规：`MessageSourceConfig.java`、`BillingKeys.java`、`TenantSupport.java` | ❌ 现有 CI 若运行会立刻失败 |
| ui 单测 | `node node_modules/jest/bin/jest.js --ci` | **1 failed, 1 passed**，耗时 **151.7s**；失败原因 `ReferenceError: CSS is not defined`（`ShoppingGuide/index.tsx:205`） | ❌ 唯一真实用例是红的 |
| ui 类型检查（属 `lint`） | `node node_modules/typescript/bin/tsc --noEmit` | **18 个类型错误 / 9 个文件**（含上述 `openDetail` 真缺陷） | ❌ `yarn lint` 必失败 |
| ai-service 测试 | `py -3 -m pytest tests -q` | 收集即失败（9 errors）；剔除后 288 passed / 5 skipped / 4 errors | ❌ 跑不完 |

**由实测新增的两点认识：**

1. **"接上 CI"不是第一优先级 —— "让门禁先能变绿"才是。** 现状即使把 workflow 挪到仓库根并接到 Gitee Go，也会在第一条命令（格式校验）就红，接着 tsc 红、jest 红。**先做 `verify.sh` 并把三端修到绿**，再接平台（这正是 D1 决策的实测依据）。
2. **`tsc` 是当前唯一能自动发现 `openDetail` 这类真缺陷的手段，而它没进 `max build`。** 也就是说：**UI 的类型检查必须进门禁**，这不是"洁癖"，它今天就能挡下一个已上线的坏按钮。
3. **但 18 个错误里只有 1 个是真运行时缺陷，不要一律当成 bug 去"修"（2026-10-13 复核）**：
   - **1 个真缺陷**：`openDetail` 未定义（点击必抛 `ReferenceError`）。
   - **3 个是 service 的「类型参数写错」**：`getAiUsage`（`service.ts:95-103`）声明 `request<{days;rows;total}>`、`getRenewStatus`/`createRenewOrder` 声明的是 payload 类型，而运行时 `request` 返回的是**完整信封** `{code,msg,data}`。
     **判断依据（已核实）**：全项目有 **154 处** `const {code,msg,data} = await xxxApi()` 解构，且 `ui/src/services/ant-design-pro/aimusic.ts:14` 注释明确写着「`@umijs/max` 的 request 默认返回响应体 `{ code, msg, data }`」。
     → **调用方是对的，AI 用量抽屉运行时正常工作**；修法是改**类型标注**，若反过来去"修正"调用方，会把正常功能改坏。
   - 其余为拼写/类型演进类问题（如 `motionEaseIn` → `motionEaseInOut`、antd 已移除的 `CheckboxValueType`）。
   → **因此阶段 0 的 T5 明确要求"修类型标注、不动调用方"**，避免把绿灯改成红灯。

**补充说明（关于 ui 测试失败的性质）**：`CSS.supports?.('height','100dvh')` 的 `?.` 只保护方法不存在，**不保护 `CSS` 这个全局变量不存在** —— jsdom 环境没有 `CSS` 全局，故抛 `ReferenceError`。真实浏览器都有 `CSS`，因此**生产环境大概率不受影响**；但这是该防护写法本身的缺陷（意图是"检测是否支持 100dvh"，却写成依赖全局存在），正确写法应为 `typeof CSS !== 'undefined' && CSS.supports?.(...)`。修测试环境与修这个写法**两件都要做**。

---

## 4. Non-Goals（本次明确不做）

- ❌ 不重写架构、不做微服务拆分（`docs/服务拆分重构方案.md` 是独立议题）
- ❌ 不改业务功能、不改 UI 视觉与交互设计
- ❌ 不迁移到 K8s、不引入服务网格
- ❌ 不做容量规划与压力测试（本次只建立**可测的基线**）
- ❌ 不重构支付渠道实现（**只做密钥注入与占位值替换**；真密钥需业务侧提供）
- ❌ 不清洗 git 历史（作为可选独立步骤，见 D2 理由）

---

## 5. 关键设计决策

### D1 · CI 平台与质量门禁

**问题**：远端在 Gitee，两处 workflow 放在子目录，即使放对位置也不触发；`ui/Jenkinsfile` 声明式与脚本式混用，语法非法。

**选项**

| 方案 | 优点 | 缺点 |
|---|---|---|
| (a) Gitee Go | 与托管平台原生集成，零额外基础设施 | 能力弱于 GH Actions，配置语法需学习 |
| (b) 自建 Jenkins（服务器 A） | 可复用于其他项目，能力强 | 需维护 Jenkins + 凭据，2C2G 机器再加负载 |
| (c) 仅 `verify.sh` + 提交前手动跑 | 零成本、立即可用 | 无人自动拦截，靠自觉 |

**决策：先 (c) 后 (a)。**

第一步交付**仓库级统一入口 `verify.sh`**（本机与任何 CI 都能跑），把"质量门禁的定义"与"CI 平台"解耦；第二步把它接到 Gitee Go。理由：

- 门禁的**内容**（跑什么、什么算通过）才是核心资产，换平台不应重写；
- 当前三端测试本身跑不起来，先接 CI 只会得到一个红色的门禁，反而让人忽略它；
- `verify.sh` 一次编写、本机可用，**立刻能给"改完→验证→拿证据"闭环**，这是本会话最缺的能力。

**影响面**：新增 `verify.sh`、`.workflow/`（Gitee Go 配置）；删除或修正 `backend/.github`、`ui/.github`、`ui/Jenkinsfile`。

---

### D2 · 密钥管理与轮换

**问题**：真实凭据已进入 git 历史（5 个提交），且同一组 OSS AK/SK 在 3 处重复。

**选项**

| 方案 | 说明 |
|---|---|
| (a) 仅 `git rm --cached` + 环境变量注入 | 工作区干净，但历史仍含明文 |
| (b) (a) + `git filter-repo` 清洗历史 | 历史也干净，但**强制改写历史**，所有协作者必须重新 clone，且 Gitee 上需强推 |
| (c) (a) + 真正启用 Jasypt 加密配置 | 密文入库，但**加密口令本身仍需外部注入**，且现状是"看起来加密了"的中间态（`application.yml:215` 口令为空、`ENC(PASSWORD)` 是字面占位符） |

**决策：(a) 为必做，(b) 为可选后置，(c) 明确废弃。**

理由：

1. **轮换是唯一真正的止血**，与是否清历史无关 —— 泄漏已发生，密钥必须视为已失陷。因此 P0 只做两件事：**轮换 + 出库**。
2. **不清历史**：仓库为私有、协作者极少，改写历史的代价（所有人重新 clone、强推风险）大于收益；且**轮换之后历史里的旧值已失效**，残留价值趋近于零。把 (b) 列为独立可选项交你决策。
3. **废弃 Jasypt**：当前是"配置块存在但口令为空 + 字面占位符 `ENC(PASSWORD)`"的状态，`algorithm: PBEWithHmacSHA256AndAES_256` 还配了 `NoIvGenerator`（CBC 需要 IV 的错误组合）。保留这种中间态比明文更危险——**它让人以为已经加密了**。改为纯环境变量注入，与 `deploy/b/setup-env.sh` 已有的正确做法一致。

**统一约定**：单一来源（环境变量）→ 三端各自读取；`.env.example` 必须与代码实际读取的字段**逐项对齐**（当前 ai-service 的 `Settings` 有 82 个字段，example 缺 60 个、且 `AI_BASE_URL` 示例值已过期会静默切模型供应商）。

---

### D3 · dev 环境与生产凭据解耦

**问题**：dev 是 pom 默认 profile，却钉死作者本机 SSH 隧道并含生产库口令。

**决策**：三层拆分。

| 层 | 文件 | 入库 | 内容 |
|---|---|---|---|
| 基线 | `application.yml` | ✅ | 结构与非敏感默认值；敏感项写 `${ENV:}` 无默认 |
| 本地 | `application-local.yml` | ❌（gitignore） | 各人本机的 DB/Redis/隧道端口 |
| 生产 | `application-pro.yml`（仓库内） | ✅ | **仅结构**，值全部来自环境变量 |

**并且**：`application-dev.yml` 改为"本机默认值"（`localhost:3306`、无密码 Redis），与 README/ONBOARDING 一致；隧道与生产连接方式移到 `docs/ONBOARDING.md` 作为**可选**章节。**dev 配置中不得出现任何生产凭据。**

---

### D4 · 数据库变更管理

**问题**：无迁移工具；容器化只应用 3/26 个脚本；`DROP DATABASE` 排在第 24/27；快照落后于代码。

**选项**：Flyway / Liquibase / 继续手工整理。

**决策：引入 Flyway，以当前生产库（107 表）为 baseline。**

理由：MySQL 单库、项目已用 Spring Boot（原生支持 Flyway）；Liquibase 的 XML/changelog 抽象对本项目是过度设计。

**baseline 取值（2026-10-13 一手核实，用 `CREATE TABLE` 计数）**

| 文件 | 建表数 | 是否被 git 跟踪 | 结论 |
|---|---|---|---|
| **`docs/db/twelvet-fast_2026-10-21.sql`** | **107** | ✅ 已跟踪 | ✅ **当前 baseline**（2026-10-21 由本机开发库重新导出） |
| ~~`db-export/twelvet-fast_2026-10-08.sql`~~ | 107 | — | 2026-10-21 已删除，由 `docs/db/` 的新导出取代 |
| ~~`db-export/twelvet-fast_2026-09-22.sql`~~ | 102 | — | 2026-10-21 已删除（落后 16 天、少 5 张表） |
| ~~`db-export/twelvet-fast_2026-09-22_fixed.sql`~~ | 102 | — | 2026-10-21 已删除（同上，乱码修复版） |
| ~~`weiwei(1).sql`（仓库根）~~ | 84 | — | 2026-10-21 已删除；旧版结构，`tenant_id` 全缺，**已废弃** |

> **结论：仓库里被跟踪的"数据库真相"比代码落后 16 天、少 5 张表；而最新的 107 表导出游离在版本控制之外。** baseline 必须用 2026-10-08 那份，并**同时纳入版本控制**——否则新环境按仓库建出来的库仍然是错的。

**具体做法**

1. `V1__baseline.sql` = `db-export/twelvet-fast_2026-10-08.sql`（107 表）整理后的结构。
2. 之后所有变更走 `V2__xxx.sql` 增量，**禁止再改 baseline**。
3. 现有 `backend/data/`（27 个）、`backend/sql/`（8 个）脚本整理归档为 `docs/legacy-sql/`（保留可追溯，但不再是执行入口）。
4. 剥掉 `DROP DATABASE`/`DROP TABLE`，新环境改为一条命令建库。
5. 生产升级：**升级前强制备份**（`mysqldump`）→ Flyway migrate → 校验表数为 107。

**验收**：全新空 MySQL 上一条命令建出与生产一致的结构；`flyway_schema_history` 能回答"当前是哪个版本"。

---

### D5 · Agent 下单写路径（业务正确性，最高优先级之一）

**问题**：`tools.py:613-623` 绕过 Java 域服务直写 `trade_order`，导致跨租户订单错乱 + 漏收运费 + 快照缺失。

**选项**

| 方案 | 优点 | 缺点 |
|---|---|---|
| (a) Agent 改调 Java `createOrder` 接口 | 与商城共用同一套不变量（租户、快照、运费、状态机），**不会再漂移** | 需加 Java↔ai-service 内部鉴权；需处理超时/重试 |
| (b) 补齐 INSERT 的三个字段 | 改动最小 | **仍在复制业务不变量**，Java 侧一改就会再漂移；运费规则、快照结构都要在 Python 里再实现一遍 |

**决策：(a)，并以 (b) 作为过渡兜底。**

理由：这是一个**已经被违反的领域边界**。选 (b) 等于认可"两套下单逻辑并存"，下一次 Java 侧调整运费或快照结构时会再次静默错账——而这次错的是**跨租户归属**，属于数据泄漏级别。过渡期若 (a) 未就绪，(b) 至少要补 `tenant_id`（从请求上下文取，**不允许用 DB 默认值**）。

#### D5 实现要点（2026-10-13 一手核实后修正）

**原方案"让 ai-service 直接调用现有 `/trade/order/create`"是不可行的**，核实结果：

| 核实项 | 事实 | 影响 |
|---|---|---|
| 现有下单接口 | `TradeOrderController.java:63-69` `POST /trade/order/create`，方法体第一行就是 `StpUtil.getLoginIdAsLong()` | **必须有用户本人的 Sa-Token**；ai-service 手上只有 `X-User-Id`，没有 token → 调用必然 401/NotLogin |
| 租户写入 | `TradeOrderServiceImpl.java:149` `order.setTenantId(tenantSupport.writeTid())` | 租户来自**请求上下文**，而 ai-service 的回调请求没有用户请求上下文 |
| 租户标识不一致 | Java→Python 透传的是 `X-Tenant-Id`（数字 id，`AiGuideController.java:128-131`），而 Java 侧解析用的是 `X-Tenant-Code`（`TenantFilter.java:36`） | **两者不是同一个标识**。若照搬"把收到的租户头回传"，会因对不上号而写错租户——正是当前 bug 的重演 |
| 防换头穿越 | `TenantBindInterceptor.java:16-17,34-37`：请求租户上下文与登录用户 `tenant_id` 不一致 → 400 | **这是安全控制，绝不能为打通链路面削弱它** |

**修正后的设计：新增"内部下单"端点，租户以用户档案为准，复用同一个 Service 方法。**

1. **Java 侧**：新增内部端点（如 `POST /trade/order/internal/create`），标注 `@SaIgnore` + 校验内部共享密钥（见 D9）；入参仅 `userId` + `productId`。
2. **租户来源改为权威数据**：由 `userId` 反查 `sys_user.tenant_id`，据此设置 `TenantContext`，再调用**现有的** `tradeOrderService.createOrder(userId, productId)`（`ITradeOrderService.java:44`）。
   → 租户、`product_snapshot`、真实运费、状态机**全部由 Java 一处保证**，Python 不再复制任何业务规则。
   → 且**不依赖任何可伪造的租户头**，天然免疫 `X-Tenant-Id`/`X-Tenant-Code` 不一致问题。
3. **为什么不破坏安全控制**：端点 `@SaIgnore` 后无登录态，`TenantBindInterceptor` 在 `getLoginIdDefaultNull() == null` 时提前返回（`:34`），不参与校验；`TenantBindInterceptor` 本身**一行不改**。
4. **ai-service 侧**：`confirm_order` 改为调用该端点（带共享密钥 + 已有的 `userId`），删除直写 SQL；失败时返回结构化错误而非 `str(e)`。
5. **过渡兜底**：若内部端点未就绪，临时把 `X-Tenant-Id` 写入 INSERT 的 `tenant_id` —— 但这**只是止血**，运费与快照仍然错，必须限期切换到 (a)。

**同时**：`str(e)` 直接回吐给用户/模型的做法全部换掉 —— 对外给安全文案 + 错误码，原文只进服务端日志。

---

### D6 · 部署形态统一

**现状**：存在三条互不兼容的路径 —— 根 `docker-compose.yml`（起不来）、`backend/docker/`（上游残留、路径错误）、`deploy/`（单机 systemd + Caddy 记录，含作者机器绝对路径）。

**决策**：**以 compose 为唯一入口，把 `deploy/` 降级为生产运维参考。**

- 补 `backend/Dockerfile`（多阶段：maven 构建 + `eclipse-temurin:17-jre` 运行，非 root、含 HEALTHCHECK）与 `ui/Dockerfile`（node 构建 + 静态托管）。
- compose 补：named volume 持久化、healthcheck、`depends_on: condition: service_healthy`、显式 `DATABASE_URL`/`REDIS_URL` 覆盖（现状容器内会走 dev 配置连 `localhost:13306`，必失败）。
- `backend/docker/` 的上游残留文件清理或明确标注 deprecated（其 `:64` 指向不存在的 `../../twelvet-ai-service`，`:58` 启动 compose 中不存在的 `twelvet-nginx`）。
- `deploy/` 中的作者机器绝对路径（`F:\apache-maven-3.9.9`、`/e/code/weiwei/...`、`~/.ssh/config` 别名）改为参数化，否则它不是可交付物。

**保留 systemd 路线**：生产当前就是这么跑的，不强行替换；但要求其步骤脚本化、参数化，且**能被第 4 节的 A4 验收覆盖**。

---

### D7 · 健康探针与 SPA 兜底

**问题**：探针被 SPA 回退吃掉，永远返回 200 HTML。

**根因（2026-10-13 一手核实 `deploy/Caddyfile`）**：第 55-58 行是兜底 `handle { try_files {path} {path}/ /index.html; file_server }` —— 任何未匹配的路径（含 `/actuator/health`）都被回退成 `index.html` 并返回 **200**。这就是线上实测"探针与不存在的路径返回完全相同的 480 字节 HTML"的原因。

**决策**：三层同时修。

1. **Caddy**：在兜底 `handle {}` **之前**新增显式块（`handle` 按书写顺序匹配，先匹配先赢，兜底必须留在最后）：
   - `handle /actuator/*` → `reverse_proxy 127.0.0.1:8080`（**不加 `handle_path`、不 strip 前缀**，保持 Java 侧收到 `/actuator/health`）
   - 参照文件内**已有的成功先例**：第 23-26 行 `@api_docs` + `handle @api_docs { respond 404 }`（注释「注意 2」已记录"必须写在 handle 块体系内"的踩坑教训，直接沿用该写法）
2. **Java**：引入 `spring-boot-starter-actuator`，暴露 `health`（含 DB/Redis indicator），并让 `WebLogFilter.java:35` 忽略的 `/actuator/health` 路径真实存在。**注意**：`/doc.html` 等文档路径当前是靠 Caddy 拦（因为 `Knife4jOpenApiCustomizer` 强依赖导致配置关不掉，见阶段 3.4），`/actuator/*` 不要复用这个绕行方案，要从代码层就可控。
3. **ai-service**：`/health`（`app/main.py:65-67`，恒返回 `{"status":"ok"}`）拆为 `/livez`（进程存活，恒 200）与 `/readyz`（真探 DB、Redis、FAISS 索引存在性），readyz 失败返回 **503**。

**验收**：把 Redis 停掉，`/readyz` 必须变红；同时随机路径仍需返回 SPA 首页（**二者不得互相回归**）。

---

### D8 · 可观测最小集

不做大而全，只做"出事能被发现"的最小集：

| 项 | Java | ai-service |
|---|---|---|
| 日志 | 生产只留 console（去掉重复落盘），补 `totalSizeCap` | **新增 logging 初始化**（级别/时间戳/traceId 到 stdout） |
| 请求 ID | 过滤器生成 + MDC + 日志 pattern 输出 | 中间件生成 + 日志输出 |
| 指标 | actuator metrics（HTTP QPS/错误率/延迟 + 连接池） | `/metrics`（Prometheus 文本，请求数/错误数/耗时 + Agent 的澄清率、超时率、工具失败率） |
| 错误追踪 | 全局异常处理器补 `NotPermissionException`(403) 等缺口 | 全局 `exception_handler` + 请求 ID 串联 |

**关键指标清单**（沿用既有设计文档 [agent.md](../../ai/agent.md) 7.3 的定义，本次只做落地）：`clarify_rate`、`human_escalation_rate`、`graph_completion_rate`、`recursion_limit_reached`、登录失败率、支付回调成功率。

---

### D9 · ai-service 内部鉴权

**问题**：`app/main.py` 全应用**零鉴权**，`music.py:42-45` 仅凭可伪造的 `X-User-Id` 头认人；`/docs` 无条件开放。项目自己的部署文档（`服务器B部署手册.md:205`）承认"8001 不要对 0.0.0.0/0 开放"——**安全模型全押在一条安全组规则上**。

**决策**：

- 加 FastAPI 中间件校验共享密钥（`X-Internal-Token`，值走环境变量），Java 侧调用时统一带上。
- 生产关闭 `/docs`、`/openapi.json`。
- CORS 从 `allow_origins=["*"]` 改为显式域名白名单。
- **保留**安全组限制作为纵深防御（不作为唯一防线）。

---

### D10 · 测试策略

**决策**：按"投入产出"分层补，不追求覆盖率数字。

| 层 | backend | ai-service | ui |
|---|---|---|---|
| 烟雾（必做） | `@SpringBootTest` 上下文可加载 + 登录接口 200 | `/livez`、`/readyz` | 首屏渲染 |
| 关键链路（必做） | 下单（含租户归属断言）、支付回调幂等、权限校验返回 403 | Agent 下单确认/取消/超时、租户隔离守卫（**修复现有静默跳过**） | 登录流 |
| 单元（按需） | 已有 `RecommendScorerTest` | 已有 35 个文件（先修可运行性） | — |

**基础设施**：backend 引入 testcontainers-mysql（当前 `application-dev.yml` 依赖隧道，测试无法独立运行）；ai-service 用 `fakeredis` + 依赖声明补齐。

---

## 6. 分阶段实施方案

### 阶段 0 · 止血（P0，阻断交付项）

| # | 任务 | 关键文件 |
|---|---|---|
| 0.1 | **轮换全部凭据**（OSS AK/SK、3 个模型平台 Key、MySQL/Redis 口令、OAuth client secret） | 需你在各控制台操作 |
| 0.2 | 凭据出库：`.env` 取消跟踪、三端改环境变量注入、`.env.example` 与代码字段对齐、交接手册脱敏 | `ai-service/.env`、`application*.yml`、`docker-compose.yml`、`deploy/部署交接手册.md` |
| 0.3 | 删除 UI 登录页预填凭据与硬编码 Basic 口令，重新构建 dist | `ui/src/pages/Login/index.tsx`、`service.ts` |
| 0.4 | 初始化 SQL 去掉预置口令与明文 secret，demo 账号移除 | `backend/data/twelvet.sql`、`neighborswap.sql` |
| 0.5 | 新增 `verify.sh`（三端校验统一入口） | 仓库根 |
| 0.6 | 修 ai-service 测试可运行性：补 `fakeredis`/`respx`/`python-multipart` 声明、加 `pytest.ini`(`asyncio_mode`)、lock 补齐 | `ai-service/requirements*.txt`、新增 `pytest.ini` |
| 0.7 | dev profile 与生产凭据解耦（D3） | `application-dev.yml`、新增 `application-local.yml`、`.gitignore` |
| 0.8 | 修 3 个文件的格式违规（跑 `spring-javaformat:apply` 后**逐一 review diff**，不做盲改） | `MessageSourceConfig.java`、`BillingKeys.java`、`TenantSupport.java` |
| 0.9 | 修 18 个 `tsc` 类型错误；其中 `openDetail` 未定义是**真实缺陷**，需补实现（或移除该按钮），不是加 `@ts-ignore` | `ui/src/pages/System/Tenant/index.tsx` 等 9 个文件 |
| 0.10 | 修 ui jest 红：补 jsdom 的 `CSS` 全局（jest setup）+ 修正 `CSS.supports?.()` 的防护写法 | `ShoppingGuide/index.tsx:205`、`tests/setupTests.jsx` |

**验收**：D2 的 A1 命令无命中；`verify.sh` 三端全绿（`mvn test` + `spring-javaformat:validate` + `tsc --noEmit` + `jest` + `pytest`），任一失败即非零退出；ai-service 全量测试 0 error、skip 数有据可查。

### 阶段 1 · 可复现（P1）

| # | 任务 |
|---|---|
| 1.1 | 补 `backend/Dockerfile`、`ui/Dockerfile`；修根 compose（卷/healthcheck/条件依赖/环境变量覆盖） |
| 1.2 | 清理 `backend/docker/` 上游残留；`deploy/` 脚本参数化 |
| 1.3 | Flyway baseline + 35 个脚本归档（D4） |
| 1.4 | README / ONBOARDING / CLAUDE.md 对齐真实结构（`backend/`、`ai-service/`、`ui/`；补 ai-service 章节；删对未入库 `.atomcode.md` 的引用） |
| 1.5 | `ui` 交付锁文件（当前 `.gitignore:21-23` 三种锁全忽略，磁盘上的 `package-lock.json` 未跟踪） |

**验收**：在干净机器按 README 从零起三端并登录成功（A4/A5/A7）。

### 阶段 2 · 可信（P2）

| # | 任务 |
|---|---|
| 2.1 | D7 健康探针三层修复 |
| 2.2 | D8 可观测最小集 |
| 2.3 | 优雅停机：Java `server.shutdown=graceful`；ai-service dispose engine/Redis；systemd `TimeoutStopSec` |
| 2.4 | D10 关键链路集成测试 |
| 2.5 | ai-service `redis_client` 改退避重试（消除"永久降级 + 幂等键丢失"）；启动 fail-fast 校验必填配置 —— **实测：82 个字段全部有默认值、0 个必填，所以"配错不报错"是结构性缺陷**，必须先**显式定义必填集合**（至少含 DB/Redis/模型 Key）才谈得上 fail-fast |
| 2.6 | **D5 Agent 下单改走 Java 域服务**（跨租户缺陷修复） |
| 2.7 | D9 ai-service 内部鉴权 + CORS 白名单 |
| 2.8 | 异常信息脱敏（`str(e)` → 安全文案 + 错误码） |

**验收**：停掉 Redis，`/readyz` 变红且告警触发；集成测试覆盖登录/下单/支付回调/Agent 确认；AI 导购下单后订单归属正确租户、运费正确、快照完整。

### 阶段 3 · 可运营（P3）

| # | 任务 |
|---|---|
| 3.1 | 限流（`/auth/login`、`/ai/**` 按 IP+账号）：现状零限流，`/ai/chat` 免认证可无限调用 LLM = **直接资金损失通道** |
| 3.2 | 登录失败锁定 + 验证码（当前 `Constants.java:106` 只剩一个无引用常量） |
| 3.3 | Sa-Token 生产策略：`active-timeout: -1` → 1800；`is-concurrent` 收敛；补会话吊销 |
| 3.4 | Knife4j 真正可关（`Knife4jOpenApiCustomizer` 构造函数强依赖导致"关了就启动失败"，改为 `@ConditionalOnProperty`） |
| 3.5 | 数据库定时备份 + 恢复演练 |
| 3.6 | CORS、端口收口（MySQL 改 `127.0.0.1`、Java `server.address`）、HTTPS |

---

## 7. 验收清单（逐条可勾选）

```
阶段 0
□ A1  git log --all -p -- ai-service/.env | 无真实密钥；三端 .env 均未跟踪
□ A1  .env.example 与代码 Settings 字段逐项一致（脚本比对，零差异）
□ A3  ai-service: pytest 全量 0 error，无静默 skip
□ A3  backend: mvn test 真实执行（非 -DskipTests）
□ A3  ui: jest 真实执行
□ A2  ./verify.sh 三端一键跑通，失败非零退出
   ├─ mvn -o test                → BUILD SUCCESS
   ├─ mvn spring-javaformat:validate → PASS（现为 3 文件违规）
   ├─ tsc --noEmit               → 0 error（现为 18）
   ├─ jest                       → 全绿（现为 1 failed）
   └─ pytest                     → 0 error（现为收集即失败）
□ A3  「租户管理 → 详情」按钮可点击且不抛异常（openDetail 真缺陷已修）
□ A4  application-dev.yml 无任何生产凭据；dev 本机可直接启动

阶段 1
□ A5  docker compose up -d 一条命令起全套（含 ui/backend）
□ A5  回滚步骤文档化并可执行
□ A7  空 MySQL 上一条命令建出与生产一致的结构；flyway_schema_history 可查版本
□ A4  干净机器按 README 起三端 + 登录成功（附实测记录）

阶段 2
□ A6  停 Redis → /readyz 变红；/actuator/health 返回 JSON 非 HTML
□ A8  三端日志到 stdout；ai-service INFO 不再丢失；有轮转上限
□ A8  关键指标可查（澄清率/超时率/支付回调成功率/登录失败率）
□ A3  集成测试覆盖：登录、下单（含租户归属断言）、支付回调、Agent 确认与取消
□ 业务 AI 导购下单 → tenant_id 正确、运费正确、product_snapshot 非空
□ 运维 重启不再切断在途支付回调（graceful shutdown 生效）

阶段 3
□ 安全 限流生效（压测 100 req/s 登录被限）
□ 安全 Knife4j 生产可关且关闭后服务能正常启动
□ 安全 Sa-Token 闲置超时生效；支持踢下线
□ 运维 备份恢复演练通过（附记录）
```

---

## 8. 回归与验证策略

1. **每个阶段结束必须产出可复现命令的真实输出**（铁律：不运行验证命令 + 不读取输出 = 不能声称通过）。
2. **阶段 1 完成后做一次完整"干净机器演练"**：新建一个目录 clone 仓库 → 按 README 执行 → 记录每一步实际输出与卡点。这是 A4 的唯一有效验收方式。
3. **阶段 2 的探针改造必须做"破坏性验证"**：停 Redis / 停 MySQL / 改错一个密钥，确认探针与告警真的反应。
4. **业务正确性（D5）以数据断言验收**：造两个租户的账号，AI 导购替租户 2 下单，然后断言该订单在租户 2 可见、租户 1 不可见。

---

## 9. 风险与回滚

| 风险 | 影响 | 缓解 |
|---|---|---|
| 轮换密钥时配置漏改一处 | 对应功能静默失效 | 轮换前后各跑一次 `verify.sh` + 端到端冒烟；改动点做成清单逐项核对 |
| Flyway baseline 与实际生产结构不一致 | 后续迁移冲突 | baseline 以**最新生产导出**为准（不是那份落后 16 天的快照）；先在临时库演练一遍 migrate |
| 改 dev 配置影响现有开发者 | 本地起不来 | 保留 `application-local.yml`（个人配置不入库），并在 ONBOARDING 写清迁移步骤 |
| Agent 下单改走 Java 接口引入超时/失败 | 下单失败率上升 | 保留旧路径作过渡兜底（但**必须补 tenant_id**）；灰度观察 |
| 清理 `backend/docker`、`ui/Jenkinsfile` 等"看似有用"的文件 | 误删他人依赖 | 先移动到 `docs/deprecated/` 而非直接删除 |
| 改 Caddy 探针规则写错位置（交接手册「坑 3」） | 整个站点 404 | 按既有教训写在 `handle` 块体系内；改前备份 Caddyfile；改后立即验证首页与探针两条路径 |

**整体回滚**：全部改动走分支 + 小步提交；生产侧保持**上一版 jar 与 dist 可回退**（`deploy/部署交接手册.md:950` 已有产物校验值的习惯，沿用）。

---

## 10. 交付物清单

| # | 交付物 | 形式 |
|---|---|---|
| 1 | 本设计文档 | `docs/superpowers/specs/2026-10-13-delivery-readiness-design.md` |
| 2 | 实施计划（任务级，含每步验证命令） | `docs/superpowers/plans/2026-10-13-delivery-readiness.md` |
| 3 | 三端统一质量门禁 | `verify.sh` |
| 4 | 交付验收报告（逐条标准 + 实测输出） | `docs/交付验收报告.md` |
| 5 | 可执行的重写版运行手册 | `README.md` + `docs/ONBOARDING.md` + `backend/README.md` |
| 6 | 密钥清单与轮换 SOP（不含密钥值） | `docs/密钥管理与轮换SOP.md` |
| 7 | 迁移与备份恢复演练记录 | `docs/数据库迁移与恢复演练.md` |

---

## 11. 下一步

1. **本文档评审**（你）：确认范围、优先级、以及 D2（是否清历史）、D5（Agent 下单改造方式）、D6（compose vs systemd）三个决策点。
2. 批准后进入 **writing-plans**：把阶段 0 拆成 2–5 分钟粒度的任务，每步含失败测试 → 实现 → 验证命令。
3. 阶段 0 完成后交付**第一次可验收证据**（`verify.sh` 的真实输出 + A1 检查结果）。

---

## 12. Decision Log

| # | 决策 | 理由 | 状态 |
|---|---|---|---|
| D1 | CI 先做 `verify.sh`，后接 Gitee Go | 门禁内容与平台解耦；当前测试跑不起来，先接 CI 只会得到假红 | 待评审 |
| D2 | 轮换为必做、清历史为可选、废弃 Jasypt | 泄漏已发生，历史残留因轮换而失效；Jasypt 当前是"看起来加密"的危险中间态 | 待评审 |
| D3 | dev/local/pro 三层配置拆分 | dev 是默认 profile 却含生产凭据，是新人不可复现的头号原因 | 待评审 |
| D4 | Flyway + 以最新生产结构为 baseline | 单库 + Spring Boot 原生支持；Liquibase 属过度设计 | 待评审 |
| D5 | Agent 下单改走 Java 域服务 | 避免两套下单不变量持续漂移；当前已造成跨租户订单错乱 | 待评审 |
| D6 | compose 为唯一入口，`deploy/` 降级为运维参考 | 消除三条互不兼容的部署路径 | 待评审 |
| D7 | 探针三层同修（Caddy/Java/ai-service） | 单修任一层都仍会得到假绿灯 | 待评审 |
| D8 | 可观测只做最小集 | 2C2G 机器，先保证"出事能被发现" | 待评审 |
| D9 | ai-service 加内部鉴权 + 生产关 /docs | 当前零鉴权，安全模型全押在一条安全组规则上 | 待评审 |
| D10 | 测试按"烟雾 + 关键链路 + 按需单元"分层 | 不追求覆盖率数字，优先覆盖资金与租户隔离路径 | 待评审 |
