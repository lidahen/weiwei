# Weiwei · 邻里二手

> 面向社区 / 园区的**多租户二手交易与社区服务平台**：二手交易、交换广场（以物换物）、社区动态、社区互助、即时通讯，以及贯穿全平台的 AI 能力与个性化推荐。
>
> 三端 monorepo（Java 单体 + Python AI 服务 + React 前端），约 **66 个 Controller / 380 个接口**，三端测试共 **1100+ 用例**。

![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5.4-6DB33F)
![Python](https://img.shields.io/badge/Python-3.11%2B-3776AB)
![React](https://img.shields.io/badge/React-18-61DAFB)
![MySQL](https://img.shields.io/badge/MySQL-8-4479A1)
![License](https://img.shields.io/badge/License-Apache--2.0-blue)

**项目状态**：功能完成度高，转入作品集收敛阶段 · 仅本地开发 / 演示环境 · 最近更新 2026-11-03

**代码仓库**：[github.com/lidahen/weiwei](https://github.com/lidahen/weiwei)

## 目录

- [功能特性](#功能特性)
- [技术亮点](#技术亮点)
- [系统架构](#系统架构)
- [技术栈](#技术栈)
- [目录结构](#目录结构)
- [环境要求](#环境要求)
- [快速开始](#快速开始)
- [配置说明](#配置说明)
- [API 文档](#api-文档)
- [测试与质量门禁](#测试与质量门禁)
- [文档导航](#文档导航)
- [已知限制](#已知限制)
- [贡献指南](#贡献指南)
- [安全](#安全)
- [许可证](#许可证)
- [致谢](#致谢)

## 功能特性

| 模块 | 主要能力 | 详细 |
|---|---|---|
| 二手市场 | 商品发布与审核、ngram 中文搜索、收藏降价提醒、订单 / 支付 / 物流 / 退款 / 评价全链路 | [详解](docs/功能模块详解.md#二手市场) |
| 交换广场 | 以物换物、交换意向、双边履约、面交码、双向评价、向量智能匹配、换物信用分 | [详解](docs/功能模块详解.md#交换广场) |
| 社区动态 | 发帖、点赞评论、热度排序、帖子关联商品卡片 | [详解](docs/功能模块详解.md#社区动态) |
| 社区互助 | 悬赏发布、接单流转、超时取消与退款、自动确认、状态通知、双向评价 | [详解](docs/功能模块详解.md#社区互助) |
| 即时通讯 | 用户会话、系统通知通道、未读数服务端闭环 | [详解](docs/功能模块详解.md#即时通讯) |
| AI 能力 | AI 导购 Agent、平台规则 RAG、内容生成与审核、识图填单、AI 估价、社区日报与管家 | [详解](docs/功能模块详解.md#ai-能力) |
| 推荐与个性化 | 六路召回、规则打分、兴趣画像、向量 / 协同召回、CTR 度量、A/B 实验 | [详解](docs/功能模块详解.md#推荐与个性化系统) |
| 管理后台与多租户 | 内容审核、举报与评价管理、推荐效果看板、租户套餐与配额、隐私开关 | [详解](docs/功能模块详解.md#管理后台) · [多租户](docs/功能模块详解.md#多租户与商业化) |

## 技术亮点

> 以下每一条都以「问题 → 做法 → 落地位置」组织，均可对照代码核验。

### 1. 三端分离的边界不变量，而不是口头约定

**问题**：AI 能力天然诱惑前端直连 Python 服务——一旦直连，鉴权、租户隔离、配额三道闸门同时失效。

**做法**：把「前端只与 Java BFF 通信」固化为架构不变量：Python 侧所有用户态接口强制要求 `X-User-Id`（只有 Java 会注入），直连必然 401——**用可失败的机制替代文档约束**。服务间调用另走 `X-Internal-Token`，令牌缺失时**失败关闭**（403）而非放行。

**落地**：`backend/.../client/AiServiceClient.java`、`ai-service/app/**` 身份依赖、`ui/config/proxy.ts`

### 2. AI Agent 的工具风险分级与运行时防护

**问题**：LLM 自主调用工具时，「查询」和「扣款」在模型眼里没有区别，一次幻觉就是资损。

**做法**：把工具按资损面分三级——**[L1] 查询**直接执行；**[L2] 修改**执行前必须向用户复述并取得**确认令牌**；**[L3] 资金**在 L2 之上额外携带**幂等键**（Redis 记录已用键，重复调用不重复扣款）。工具按域拆子图（售前 / 售中 / 售后 / 社区），由 LangGraph 编排，每个域只挂载该域工具。

**落地**：`ai-service/app/services/agent/tools.py`（分级约定）、`state.py:pending_confirm/idempotency_keys`、按域子图

### 3. 多租户隔离与 5 级数据权限

**问题**：SaaS 化后，「这个用户能看到哪些行」既要正确，又不能在每个 Service 里手写 `if`。

**做法**：租户上下文从 Host 子域名或 `X-Tenant-Code` 解析并绑定请求线程；数据权限用 `@SysDataScope` 注解 + AOP 在 SQL 层注入过滤条件（**全部 / 自定义 / 本部门 / 本部门及以下 / 仅本人**五级），业务代码零感知。租户又叠加套餐与配额（额度、并发、功能开关）。

**落地**：`twelvet-framework/twelvet-datascope`、`twelvet-system` 租户域

### 4. 资金链路的正确性收口（本项目含金量最高的一段）

**问题**：三类业务（商品交易 / 互助悬赏 / 租户续费）共用一张支付单表，`order_id` 指向不同域；用户付款与订单超时关闭可能同时发生——**迟到支付会让已关闭订单「复活」**。

**做法**：

- **多态外键消歧**：支付单增加 `biz_type`（`trade` / `runner` / `renew`），所有关联查询、结算 join、退款入口一律带 `biz_type` 谓词；DDL 幂等且带历史数据回填。参考 [设计文档](docs/superpowers/specs/2026-11-03-pay-biz-type-design.md)。
- **守卫式状态机**：`closeOrder` 加 `NOT EXISTS(paid)` 守卫，`markPayStatus` 只允许 `unpaid → paid`——**用 SQL 谓词把非法迁移挡在数据库层**，而不是靠调用顺序。
- **迟到支付兜底**：支付成功事件监听器发现订单已关闭时，不复活订单，改为**自动原路退款**并通知买家。
- **对账补偿**：退款失败的滞留任务由定时任务扫描兜底重试，幂等性由「非 `paid` 拒绝退款 + 仅 `paid` 可翻状态」双向保证。

**落地**：`backend/sql/pay_biz_type.sql`、`PaySuccessEventListener`、`SettlementOrderMapper.xml`、`runnerTask.reconcileStalePaidRefunds`

### 5. 结算账本：卖了钱什么时候能提

**问题**：交易完成后钱不能立刻给卖家（售后期），还要支持退款互斥、提现审核、对账差异。

**做法**：五张表构成账本（卖家账户 / 结算单 / 余额流水 / 提现 / 对账差异），资金按 **持有 → 解冻 → 可提现** 推进；解冻只作用于已 resolve 的结算行，退款与结算互斥；三个 Quartz 任务负责到期解冻、提现处理与对账——**全部默认暂停**，由运营显式启用。

**落地**：`backend/sql/settlement_schema.sql`、`twelvet-trade` 结算域、Phase 1B 运营页（4 页 + 卖家中心）

### 6. 检索与推荐

**问题**：中文二手商品的检索要能扛住口语化查询，首页又要冷启动就有内容。

**做法**：RAG 链路 **FAISS 向量 + BM25 关键词 + RRF 融合 + rerank**；推荐侧六路召回（热门 / 新品 / 兴趣 / 向量 / 协同 / 附近）+ 规则打分 + 兴趣画像，并带 **CTR 度量与 A/B 实验**闭环。

**落地**：`ai-service/data/faiss_indexes/`、[RAG Showcase](docs/rag-showcase/README.md)、[推荐设计](docs/二手市场首页推荐设计.md)

### 7. 工程细节：把「静默失效」变成「会响的失败」

**问题**：这类项目最贵的 bug 不是崩溃，而是**不报错但不生效**。

**做法**：逐条消灭静默失效——Redis 序列化器缺 JSR-310 导致含 `LocalDateTime` 的缓存条目永远写不进去（**修复并加往返测试**）；`@TwSynchronized` 注解声明了却没有实现（**补 Redisson AOP，锁获取失败显式抛异常**）；AI 配置项 82 个全有默认值、漏配静默降级（**启动时汇总缺失的关键配置并告警**）；声明了却没有配置文件、只会在启动时才炸的 `test` / `staging` profile（**删除**）；涉及资金动作的定时任务**一律以暂停状态创建**，由运营显式启用。

**落地**：`RedisTemplateConfiguration.java`、`TwSynchronizedAspect.java`、`ai-service/app/main.py:lifespan`

## 系统架构

```text
浏览器 / 移动端
      │  /api
      ▼
┌──────────────────┐        ┌────────────────────┐        ┌──────────────────────┐
│ ui (Umi Max)     │ ─────> │ backend (Java BFF) │ ─────> │ ai-service (FastAPI) │
│ :3000            │        │ :8080              │        │ :8001                │
└──────────────────┘        └─────────┬──────────┘        └──────────┬───────────┘
                                      │                              │
                                      └──────────┬───────────────────┘
                                                 ▼
                                     MySQL 8 (`twelvet-fast`) / Redis 7
```

> **架构不变量**：前端只与 Java BFF（8080）通信，再由 Java 转发到 Python `ai-service`（8001）；Python 直读同一个 MySQL 库，但**不直接面向浏览器**。
> 任何 `/api/**` 直连 8001 都会绕过鉴权 / 租户 / 配额，且 Python 侧部分接口强制要求 `X-User-Id`（只有 Java 会注入），直连必然 401。

| 端 | 技术栈 | 默认端口 | 职责 |
|---|---|---|---|
| `ui/` | Umi Max 4 · React 18 · TypeScript · Ant Design 5 | 3000 | C 端 + 管理后台，同仓单应用 |
| `backend/` | Java 17 · Spring Boot 3.5.4 · MyBatis · Sa-Token | 8080 | 单体 BFF：鉴权、租户、配额、业务域、转发 AI |
| `ai-service/` | Python 3.11+ · FastAPI · LangGraph · FAISS | 8001 | AI 能力与 Agent 实现层 |
| 存储 | MySQL 8 · Redis 7 | 3306 / 6379 | 业务库 `twelvet-fast` / 会话与缓存 |

后端为 **Maven 多模块单体**（非微服务），由 `twelvet-admin` 聚合启动。模块地图与 AI 能力分层见 [功能模块详解](docs/功能模块详解.md#后端模块地图)。

**一次 AI 导购请求的完整链路**（体现各层职责边界）：

```text
浏览器
  │  POST /api/product/ai/guide   （Cookie / Bearer 令牌）
  ▼
Java BFF :8080
  ├─ SaInterceptor          认证：校验 Sa-Token 会话（Redis 持久化）
  ├─ TenantFilter           租户：Host 子域 / X-Tenant-Code → 线程上下文
  ├─ @SysDataScope (AOP)    数据权限：按 5 级策略向 SQL 注入过滤条件
  ├─ 配额校验               套餐额度 / 功能开关
  ▼
twelvet-ai (转发层，不直连模型)
  │  WebClient + X-User-Id + X-Tenant-Id + X-Internal-Token
  ▼
ai-service :8001 (FastAPI)
  ├─ 内部令牌校验（失败关闭）
  ├─ LangGraph 图：entry → rag_retrieval → router → 域子图 → end
  ├─ 工具分级执行：L1 直接 / L2 确认令牌 / L3 幂等键
  ├─ 模型调用：Agnes（主）· SiliconFlow Embedding · 商汤音乐
  ├─→ 写操作统一回打 Java BFF 内部接口（禁止直写业务表）
  └─→ 只读查询可直接读 MySQL（共享同一业务库）
```

## 技术栈

| 层 | 主要技术 |
|---|---|
| 后端 | Java 17、Spring Boot 3.5.4、MyBatis 3 + PageHelper、Sa-Token 1.44、Redisson、Quartz、Knife4j / SpringDoc |
| AI 服务 | Python 3.11+、FastAPI、LangGraph / LangChain、FAISS、bge-m3、BM25 + RRF + rerank |
| 前端 | Umi Max 4、React 18、TypeScript、Ant Design 5、ECharts 5、antd-style、GSAP |
| 数据 | MySQL 8.0、Redis 7 |
| 构建 / 测试 | Maven、npm（**不使用 yarn**）、pytest、Jest、spring-javaformat |

## 目录结构

```text
weiwei/
├── backend/                    # Java 单体（Maven 多模块）
│   ├── twelvet-admin/          # 启动入口 + 系统级 Controller
│   ├── twelvet-framework/      # 框架核心（安全 / 缓存 / 数据权限 / 日志 / Swagger）
│   ├── twelvet-system/         # 用户 / 角色 / 菜单 / 租户 / 字典
│   ├── twelvet-product/        # 商品、动态、收藏、关注、推荐服务端
│   ├── twelvet-trade/          # 订单 / 评价 / 物流 / 退款
│   ├── twelvet-payment/        # 支付宝 / 微信支付
│   ├── twelvet-runner/         # 社区互助
│   ├── twelvet-exchange/       # 交换广场
│   ├── twelvet-im/             # 即时通讯
│   ├── twelvet-ai/             # AI 转发层（不直连模型）
│   ├── twelvet-quartz/         # 定时任务
│   └── data/ · sql/            # 基线后增量脚本（⚠️ 见「初始化数据库」）
├── ai-service/                 # Python AI 服务
│   ├── app/routers/            # chat / assist / knowledge / moderation / recommend...
│   ├── app/services/agent/     # 导购 Agent（LangGraph 图 + 工具 + checkpointer）
│   ├── data/rag_corpus/        # 平台规则 RAG 语料
│   ├── data/faiss_indexes/     # FAISS 索引（在线 bge-m3 / 离线 LSA）
│   └── tests/                  # pytest
├── ui/                         # Umi Max 前端（C 端 + 管理后台）
├── docs/                       # 设计文档、导出数据、RAG 报告
│   ├── ONBOARDING.md           # 首次环境搭建与排错
│   ├── 功能模块详解.md          # 各模块实现细节（README 的深度展开）
│   ├── db/                     # 数据库全量导出与导入说明
│   ├── rag-showcase/           # 平台规则 RAG 使用与评测
│   └── superpowers/            # 开发过程中的 specs / plans
├── verify.sh                   # 三端统一质量门禁
├── CLAUDE.md                   # 项目约定（含架构不变量）
└── Superpowers新功能开发指令.md  # 新功能开发流程规范
```

## 环境要求

| 依赖 | 版本 | 说明 |
|---|---|---|
| Git | 2.30+ | — |
| JDK | **17** | Spring Boot 3.5.4 必需 |
| Maven | 3.8+ | Windows Git Bash 请用 `mvn.cmd`（`mvn` 脚本有 classworlds 兼容问题） |
| Node.js | 22 LTS | 前端构建与开发 |
| npm | 10+ | 包管理器统一用 npm，**不要用 yarn** |
| Python | 3.11+ | AI 服务；Windows 建议 `py -3` 而非 `python`（避免 Store 别名） |
| MySQL | 8.0 | 业务库 `twelvet-fast`，字符集 utf8mb4 |
| Redis | 7 | 会话、缓存、Agent checkpointer |
| Docker | 可选 | 仅用于快速启动 MySQL / Redis |

Windows 用户建议在 **Git Bash** 中执行命令。

## 快速开始

> 完整首次搭建与常见问题见 [docs/ONBOARDING.md](docs/ONBOARDING.md)。以下为最短路径。

### 1. 克隆仓库

```bash
git clone https://github.com/lidahen/weiwei.git
cd weiwei
```

### 2. 启动 MySQL 与 Redis

```bash
# 方式 A：Docker（最快）
docker run -d --name weiwei-mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=123456 mysql:8.0.32
docker run -d --name weiwei-redis -p 6379:6379 redis:7.2.4

# 方式 B：本机安装 MySQL 8.0 + Redis 7，并确保端口为 3306 / 6379
```

### 3. 初始化数据库

仓库提供一份 **107 表全量基线**（2026-10-21 从开发库导出，含种子 / 演示数据，已实测可完整还原），再补上基线之后新增的模块脚本：

仓库提供一份 **120 表全量基线**（2026-11-03 从开发库导出，**含数据**，已实测可完整还原），它已经把此前各模块的增量脚本都包含进去了 —— **新环境导入这一个文件即可**：

```bash
# 本地开发默认口令为 123456，按实际修改
MYSQL="mysql -h127.0.0.1 -P3306 -uroot -p123456 --default-character-set=utf8mb4"

# 3.1 建库
$MYSQL -e "CREATE DATABASE IF NOT EXISTS \`twelvet-fast\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 3.2 导入全量基线（120 张表，含种子 / 演示数据）—— 一步到位，无需再跑增量脚本
$MYSQL twelvet-fast < docs/db/twelvet-fast_2026-11-03.sql
```

验证：

```bash
$MYSQL -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='twelvet-fast';"
# 应为 120
$MYSQL -N -e "SELECT COUNT(*) FROM \`twelvet-fast\`.sys_user;"
# 应为 8
```

> ⚠️ 基线含 `DROP TABLE IF EXISTS`，重复导入会覆盖同名表；已有数据请先备份。
>
> **`backend/sql/` 与 `backend/data/` 下的增量脚本，其效果已全部包含在这份基线内**，新环境不必执行（保留供追溯每一步变更）。
> 若将来需要重放，顺序不能颠倒：`settlement_schema.sql` 必须早于 `settlement_menu.sql`，`pay_biz_type.sql` 必须晚于 `settlement_schema.sql`——完整清单见 [CLAUDE.md](CLAUDE.md)。
>
> 表清单与逐表数据行数见 [docs/db/_表清单与行数.txt](docs/db/_表清单与行数.txt)，导入细节与注意事项见 [docs/db/_导入说明.txt](docs/db/_导入说明.txt)。

### 4. 配置并启动 ai-service

```bash
cd ai-service
python -m venv .venv
source .venv/Scripts/activate       # Windows Git Bash；macOS/Linux 用 .venv/bin/activate
pip install -r requirements.txt

# 仓库内不含 .env（已 gitignore），复制模板后填入自己的密钥
# cp .env.example .env

# 确认 DATABASE_URL / AI_API_KEY / EMBEDDING_API_KEY / INTERNAL_API_TOKEN 与本机一致
python start.py --port 8001
```

启动成功后可访问 `http://localhost:8001/docs`。

### 5. 启动 backend

```bash
# 若启用 AI Agent 写操作，先在 backend 与 ai-service 两侧设置同一个令牌
export INTERNAL_API_TOKEN=dev-internal-token

cd backend
mvn clean install -DskipTests
mvn spring-boot:run -pl twelvet-admin
```

> Windows Git Bash 把上面的 `mvn` 换成 `mvn.cmd`。后端启动不依赖 ai-service；只有调用 AI 接口时才需要。

### 6. 启动 ui

```bash
cd ui
npm ci
PORT=3000 npm run start:dev
```

> Umi 默认端口是 8000，会与 ai-service 冲突。除 `PORT=3000` 外，也可在 `ui/.env` 中设置 `PORT=3000`。

### 7. 访问

| 服务 | 地址 |
|---|---|
| 前端 | <http://localhost:3000> |
| 后端 | <http://localhost:8080> |
| Knife4j 接口文档 | <http://localhost:8080/doc.html> |
| ai-service Swagger | <http://localhost:8001/docs> |

启动顺序建议：**MySQL → Redis → ai-service → backend → ui**。

## 配置说明

### 后端

| 配置 | 位置 / 环境变量 | 说明 |
|---|---|---|
| 数据库 | `DB_HOST` / `DB_PORT` / `DB_USERNAME` / `DB_PASSWORD` | `application-dev.yml` 已改为占位符，默认本机 3306 / root |
| Redis | `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | 默认本机 6379 |
| AI 服务地址 | `ai.service.url`（`application-dev.yml`） | 默认 `http://localhost:8001`，必须与 ai-service 端口一致 |
| 服务间令牌 | `INTERNAL_API_TOKEN` | 必须与 ai-service 一致；留空时内部接口按**失败关闭**返回 403 |

### ai-service

配置集中在 `ai-service/.env`：

| 变量 | 用途 |
|---|---|
| `DATABASE_URL` | MySQL 连接串（`mysql+aiomysql://.../twelvet-fast`） |
| `REDIS_URL` | Redis（Agent 会话 / 确认令牌 / 幂等键） |
| `AI_BASE_URL` / `AI_API_KEY` / `AI_MODEL` | 对话模型（OpenAI 兼容，默认 Agnes） |
| `EMBEDDING_BASE_URL` / `EMBEDDING_API_KEY` / `EMBEDDING_MODEL` | Embedding（默认 SiliconFlow bge-m3） |
| `JAVA_BASE_URL` / `INTERNAL_API_TOKEN` | Agent 写操作统一经 Java BFF，禁止直写业务表 |
| `SENSEAUDIO_*` / `ASR_*` / `CREATION_*` | AI 音乐与创作台（可选，冻结区功能） |

> ⚠️ ai-service 的配置项**全部有默认值**，漏配不会报错、只会静默走默认。服务启动时会汇总缺失的**关键**配置项（`AI_API_KEY` / `INTERNAL_API_TOKEN` / `EMBEDDING_API_KEY`）并打 WARNING 日志，但非关键项仍需自行核对；修改模型或密钥后请实际调用一次验证。
> 真实密钥放在本机 i-service/.env（不入库，模板见 .env.example）。本仓库以占位符形式提供配置，未配置时相关功能会明确报错而不是静默降级。

## API 文档

- Knife4j：<http://localhost:8080/doc.html>
- Swagger UI：<http://localhost:8080/swagger-ui.html>
- OpenAPI JSON：<http://localhost:8080/v3/api-docs>

鉴权使用 Sa-Token：先调用 `POST /auth/oauth2/token` 获取 token，后续请求携带：

```http
Authorization: Bearer <token>
```

服务间接口（如 `POST /internal/trade/order/create`）不使用用户令牌，改为携带 `X-Internal-Token`，且必须与后端 `INTERNAL_API_TOKEN` 一致。

## 测试与质量门禁

**当前规模**（2026-11-03 实测）：Java **383** 用例（64 个测试类，全通过）· Python **646** 用例（645 通过 / 1 跳过）· 前端 **152** 用例（全通过），合计 **1181**。

优先使用仓库根的统一门禁，一次跑完三端 5 项检查（失败不中断）：

```bash
bash verify.sh
# 首次运行、Maven 依赖尚未缓存时：
# MVN_FLAGS="" bash verify.sh
```

分项命令：

| 范围 | 命令 |
|---|---|
| Java 全部单测 | `cd backend && mvn -o test` |
| Java 单个测试类 | `cd backend && mvn -o -pl twelvet-product/twelvet-product-server -am test -Dtest=RecommendScorerTest -Dsurefire.failIfNoSpecifiedTests=false` |
| Java 格式校验 / 修复 | `cd backend && mvn spring-javaformat:validate` / `mvn spring-javaformat:apply` |
| Python 全部测试 | `cd ai-service && python -m pytest tests -q`（Windows 用 `py -3`） |
| 前端类型检查 | `cd ui && node node_modules/typescript/bin/tsc --noEmit` |
| 前端单测 | `cd ui && node node_modules/jest/bin/jest.js --ci` |

Python 测试额外依赖（`respx` / `fakeredis`）在 `ai-service/requirements-dev.txt`：

```bash
cd ai-service && pip install -r requirements-dev.txt
```

## 文档导航

| 文档 | 内容 |
|---|---|
| [功能模块详解](docs/功能模块详解.md) | 后端模块地图、AI 能力分层、各业务模块实现细节、推荐系统全量设计 |
| [环境搭建 ONBOARDING](docs/ONBOARDING.md) | 首次安装、启动顺序、常见问题排查 |
| [人工测试清单](docs/人工测试清单.md) | 已交付但尚未人工冒烟的功能清单：账号、接口、库表判据、逐项预期与加速技巧 |
| [数据库导入说明](docs/db/_导入说明.txt) | 107 表全量导出的导入命令、字符集与版本注意事项 |
| [二手市场首页推荐设计](docs/二手市场首页推荐设计.md) | 召回 / 打分 / 画像 / A-B / CTR 详细设计 |
| [交换广场模块设计方案](docs/交换广场模块设计方案.md) | 以物换物一期：物品 / 意向 / 双边履约 / 面交码 / 评价 |
| [交换广场二期设计](docs/交换广场二期-智能匹配与信用设计.md) | 供给 / 需求双 FAISS 索引、每日匹配推送、换物信用分 |
| [RAG Showcase](docs/rag-showcase/README.md) | 平台规则 RAG 使用说明、完成度、在线 / 离线评测 |
| [AI 文档目录](docs/ai/) | Agent 架构、模型调用契约、AI 功能评估 |
| [产品战略与路线图](docs/superpowers/specs/2026-11-02-product-strategy-design.md) | 定位、用户与指标、四层架构、Phase 0–2 路线图 |
| [支付多态外键设计](docs/superpowers/specs/2026-11-03-pay-biz-type-design.md) | 三类业务共用支付单的消歧方案与迟到支付处理 |
| [设计与计划存档](docs/superpowers/) | 每个功能先出 spec（设计）再出 plan（任务拆分），按日期归档 |
| [密钥管理与轮换 SOP](docs/密钥管理与轮换SOP.md) | 密钥出库、轮换与应急流程 |
| [CLAUDE.md](CLAUDE.md) | 项目架构不变量与协作约定 |
| [Superpowers 新功能开发指令](Superpowers新功能开发指令.md) | 新功能从设计到验证的流程规范 |

## 已知限制

- **仅本地开发 / 演示**：不含部署编排；支付走 **mock 网关**（支付宝 / 微信为 Strategy 实现，未接真实商户号），无真实资金流。
- **数据库尚无 Flyway baseline**：新环境依赖全量导出 + 基线后增量脚本（清单见「快速开始」）；2026-10-21 之前的旧初始化脚本已于 2026-10-27 清理。
- **`ai-service/.env` 含开发密钥且被仓库跟踪**：公开或开源前必须移出并轮换，流程见 [密钥管理与轮换 SOP](docs/密钥管理与轮换SOP.md)。
- **缺少健康探针**：未引入 actuator；进程存活只能通过业务接口间接判断。
- **平台规则 RAG 语料为虚构演示数据**：仅用于能力展示与评测，不可作为真实平台规则依据。
- **结算与看板的界面冒烟未补齐**：结算、退款、漏斗看板有单元 / 组件测试与后端全量用例覆盖，但**端到端手工冒烟尚未逐项走完**。
- **定时任务默认暂停**：结算三任务（到期解冻 / 提现处理 / 对账）与互助任务（超时扫描 / 自动确认 / 退款对账）建库时 `status='1'`（暂停），需在后台显式启用——这是本地开发的刻意默认，避免误触发资金动作。

后端更完整的已知问题清单见 [backend/README.md](backend/README.md)。

## 贡献指南

1. 开始任何新功能前，先阅读 [CLAUDE.md](CLAUDE.md) 与 [Superpowers 新功能开发指令](Superpowers新功能开发指令.md)，按「设计 → 批准 → 实现 → 验证」流程推进。
2. 从 `master` 拉出功能分支（如 `feat/xxx`、`fix/xxx`），不要直接在主分支开发。
3. 前端包管理器统一用 **npm**；提交前执行 `bash verify.sh`，Java 代码先跑 `mvn spring-javaformat:apply`。
4. 新增数据库脚本请保持幂等，并在脚本头部注明前置条件、执行顺序与回滚方式；同步更新 [数据库导入说明](docs/db/_导入说明.txt)。
5. 涉及架构不变量的改动（尤其是前端直连 ai-service、跨租户数据访问）必须在 PR 描述中说明理由与验证方式。
6. 问题与建议优先在 [Gitee 仓库](https://github.com/lidahen/weiwei/issues) 提交 Issue；安全问题请按「安全」一节的私下渠道反馈。

## 安全

- 请勿在 Issue / PR 中提交真实密钥、数据库口令或用户数据。
- i-service/.env 不入库；请从 .env.example 复制后填入自己的密钥，切勿提交真实凭据。
- 发现安全问题时请私下联系维护者，不要在公开渠道披露。

## 许可证

本项目基于 [Apache License 2.0](LICENSE) 发布，第三方组件与上游项目的署名见 [NOTICE](NOTICE)。

后台基于 [TwelveT Fast](https://gitee.com/twelvet-projects/twelvet) 二次开发，上游许可证随本仓库一并适用；若与上游存在冲突，以上游为准。

> 仓库内 `docs/db/*.sql` 为虚构演示数据，不含真实用户信息；其中的 API Key 字段已替换为占位符，不可用于调用任何真实服务。

## 致谢

- [TwelveT Fast](https://gitee.com/twelvet-projects/twelvet) — 后端基础框架
- [Umi Max](https://umijs.org/) · [Ant Design](https://ant.design/) — 前端框架与组件库
- [FastAPI](https://fastapi.tiangolo.com/) · [LangGraph](https://langchain-ai.github.io/langgraph/) · [FAISS](https://github.com/facebookresearch/faiss) — AI 服务与检索
- [BAAI bge-m3](https://huggingface.co/BAAI/bge-m3) — Embedding 模型