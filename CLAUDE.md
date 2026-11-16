# weiwei — Project Instructions

- 做任何工作都需要先阅读Superpowers新功能开发指令.md，按照规范去工作
- 项目整体架构参考 docs/ONBOARDING.md（`.atomcode.md` 只在最初那台开发机上、未随仓库交付；`deploy/` 部署手册已于 2026-10-21 删除，本项目只在本地开发）
- 需要我确认的情况，默认按照 yes 执行

---

## 项目概述

**这是一个三端 monorepo**（2026-08-26 由三个独立仓库合并而来，见 docs/superpowers/specs/2026-08-26-weiwei-monorepo-design.md）：

| 目录 | 内容 | 技术栈 | 交付形态 |
|---|---|---|---|
| `backend/` | TwelveT Fast v3.5.4 —— 商城后端 | Java 17 + Spring Boot 3.5.4（**单体**，Maven 多模块，非微服务） | 唯一部署 JAR：`backend/twelvet-admin/target/twelvet-admin.jar` |
| `ai-service/` | AI 能力与 Agent（导购/创作/RAG/审核） | Python 3.11+ FastAPI + LangGraph + LangChain | 独立进程，默认 `:8001`，systemd 托管 |
| `ui/` | 管理端 + C 端前端 | Umi Max 4 + React 18 + TS + Ant Design 5（包管理器用 **npm**） | 静态产物 `ui/dist/`，由 Caddy 托管 |

**架构不变量（务必遵守）**：前端只与 Java BFF(8080) 通信，再由 Java 转发到 Python ai-service(8001)。
任何 `/api/**` 直连 8001 都是 bug —— 会绕过鉴权/租户/配额，且 Python 侧部分接口强制要求 `X-User-Id`
（只有 Java 会注入），直连必然 401。依据：`ui/config/proxy.ts` 的注释与
`backend/twelvet-ai/twelvet-ai-server/.../client/AiServiceClient.java`。

> ⚠️ **Java 侧没有 Spring AI 依赖**（已核实 backend 全部 pom.xml 无 `spring-ai`）。模型调用
> （Agnes / SiliconFlow / 商汤等）全部发生在 Python `ai-service`；Java 的 `twelvet-ai` 模块是
> **HTTP 转发层**（AiServiceClient + WebClient），不直连模型。

---

## 项目结构

```
weiwei/                          # 仓库根（三端 monorepo）
├── backend/                     # Java 单体（Maven 多模块：13 个聚合模块 / 45 个 pom）
│   ├── twelvet-admin/           # 启动入口 + 系统级 Controller（Login/Profile/Index）
│   ├── twelvet-framework/       # 框架核心（9 子模块）
│   │   ├── core        # BaseEntity、JsonResult、TWTController、CORS、Jackson、i18n 三级缓存
│   │   ├── security    # Sa-Token（SaInterceptor + SaPermissionImpl + SecurityUtils + LoginUser）
│   │   ├── redis       # Redisson + RedisUtils（⚠️ @TwSynchronized 未实现）
│   │   ├── datasource  # 动态数据源（baomidou + JdbcDynamicDataSourceProvider）
│   │   ├── datascope   # @SysDataScope + AOP（5 级数据权限过滤）
│   │   ├── log         # @Log + SysLogAspect + WebLogFilter + 异步事件持久化
│   │   ├── jdbc        # PageHelper + PageDomain + TableDataInfo
│   │   ├── swagger     # Knife4j + SpringDoc OpenAPI 3（8 个 API 分组）
│   │   └── utils       # 35+ 工具类（DateUtils、IpUtils、JacksonUtils 等）
│   ├── twelvet-system/         # 系统管理（用户/角色/菜单/部门/岗位/字典/日志/租户/i18n）
│   ├── twelvet-ai/             # AI **转发层**（对 ai-service 的 HTTP 封装，不直连模型）
│   ├── twelvet-product/        # 商品/二手交易 + 社交动态
│   ├── twelvet-trade/          # 交易/订单
│   ├── twelvet-payment/        # 支付（支付宝/微信，Strategy 模式）
│   ├── twelvet-im/             # 即时通讯 WebSocket
│   ├── twelvet-gen/            # 代码生成器（Velocity）
│   ├── twelvet-quartz/         # 定时任务
│   ├── twelvet-runner/         # 互助/悬赏
│   ├── twelvet-exchange/       # 交换广场（以物换物：物品/意向/双边履约/匹配/信用）
│   ├── twelvet-dfs/            # 文件存储（阿里云 OSS）
│   ├── sql/                    # 基线后增量（交换广场 schema/menu/match、互助文案迁移）
│   ├── data/                   # 基线后增量（Agent 记忆、C 端手机号登录）
│   └── docker/                 # 遗留 Docker 编排（2026-08-26 合并入仓时带入，已无对应 compose，仅存档）
├── ai-service/                 # Python AI 服务
│   ├── app/                    # FastAPI 应用（routers / services / schemas / models）
│   │   └── services/agent/     # 导购 Agent（LangGraph 图 + 工具 + checkpointer）
│   ├── data/                   # FAISS 向量索引（data/faiss_indexes/）+ RAG 语料（data/rag_corpus/）
│   ├── skills/、scripts/       # 技能目录与运维脚本
│   └── tests/                  # pytest（测试依赖见 requirements-dev.txt）
├── ui/                         # 前端（Umi Max）
├── docs/                       # 文档（ONBOARDING.md 为新人入口；superpowers/ 存设计与计划）
└── docs/db/                    # 数据库全量导出（twelvet-fast_2026-10-21.sql，107 表）与导入说明
```

**⚠️ 数据库初始化（2026-11-03 更新）**：**新环境只需导入 `docs/db/twelvet-fast_2026-11-03.sql`
（120 表全量基线，含数据）**——该基线已把 2026-10-21 之后的所有增量脚本效果包含在内，
建库后导入这一个文件即可，无需再跑任何增量脚本。

```bash
mysql -uroot -p123456 -e "CREATE DATABASE IF NOT EXISTS \`twelvet-fast\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -uroot -p123456 --default-character-set=utf8mb4 twelvet-fast < docs/db/twelvet-fast_2026-11-03.sql
# 验证：SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='twelvet-fast';  → 120
```

导出与还原的实测记录、表清单（`_表清单与行数.txt`）见 `docs/db/_导入说明.txt`；
旧基线 `twelvet-fast_2026-10-21.sql`（107 表）保留用于追溯差异。

**下列增量脚本的效果已全部包含在该基线内，保留备查、不必执行**
（重放时顺序不能颠倒，括号内为该脚本的贡献）：

1. `backend/sql/exchange_schema.sql` → `exchange_menu.sql` → `exchange_match.sql`（交换广场 7 表）
2. `backend/data/agent-memory-init.sql`（Agent 长期记忆表）
3. `backend/sql/runner_rename_mutual_aid.sql`（社区跑腿 → 社区互助 文案迁移）
4. `backend/sql/permission_align.sql`（菜单权限对齐；漂移检查用 `scripts/check-perms.ps1`）
5. `backend/sql/refund_feature.sql`（退款售后表结构/菜单/授权/定时任务）
6. `backend/sql/funnel_event.sql`（漏斗事件表/社区漏斗菜单/角色授权，可重复执行）
7. `backend/sql/settlement_schema.sql`（结算五表；重建空的 trade_withdraw，可重复执行）
8. `backend/sql/settlement_menu.sql`（结算菜单/权限/定时任务，**必须晚于 settlement_schema.sql**）
9. `backend/sql/pay_biz_type.sql`（trade_pay.biz_type 列/索引/回填，**必须晚于 settlement_schema.sql**）
10. `backend/sql/runner_refund_reconcile.sql`（互助退款对账定时任务，幂等/默认暂停）

`backend/data/sms-auth-init.sql`、`backend/sql/report_feature.sql` 的效果同样已含在基线中，保留备查。

---

## 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 3.5.4 | 单体，Undertow（排除 Tomcat） |
| MyBatis + PageHelper | 3.0.3 / 2.1.0 | XML Mapper，无 MyBatis-Plus |
| Sa-Token + Redisson | 1.44.0 / 3.30.0 | 非 Spring Security。**会话确实持久化在 Redis**（键名 `Authorization:login:token:<t>`，由 `sa-token.token-name` 决定，2026-10-13 实测） |
| ~~Spring AI~~ | — | ❌ **Java 侧无此依赖**（已核实全部 pom.xml）。AI 在 Python `ai-service`：FastAPI + LangGraph + LangChain，模型供应商默认 Agnes（备用 SiliconFlow / 商汤） |
| Alipay SDK + WeChat Pay v3 | 4.38.80 | Strategy 模式（PaymentGateway 接口） |
| 阿里云 OSS | 3.0.0 | pig4cloud OSS Starter |
| Knife4j + SpringDoc | 4.5.0 / 2.8.9 | API 文档 |
| Jasypt | 3.0.5 | 配置加解密（PBEWithMD5AndDES） |
| mica-xss | 3.1.5.1 | XSS 防护 |
| Velocity | 2.4 | 代码生成（支持 MySQL/PG/Oracle/SQLServer/DB2/达梦/瀚高） |
| Spring Java Format | 0.0.39 | 代码格式规范 |

---

## 安全架构（Sa-Token）

**认证流程：** 请求 → SaInterceptor（StpUtil.checkLogin）→ AuthIgnoreConfig 白名单 → @SaCheckPermission 细粒度权限

**Token 配置：** Authorization / Bearer / 86400s / UUID 风格 / 允许并发登录 / Redisson 存储

**权限模式：**
- **B 端模块**（system/gen/ai/quartz）：`@SaCheckPermission` 细粒度权限字符串
- **C 端模块**（product/trade/im）：`@SaIgnore` 公开接口 + `StpUtil` 写操作仅验证登录
- **DFS**：混合（部分端点 @SaCheckPermission）

**白名单：** 静态资源、Swagger、商品浏览、用户资料、字典查询、支付回调

---

## API 端点（实测：66 个 Controller / 380 个方法级映射）

> **测量方式（2026-10-27，可复现）**：类数 = `backend/` 下 `*Controller.java` 文件数；
> 端点数 = 方法级注解 `@(Get|Post|Put|Delete|Patch)Mapping` 出现次数（不含类级 `@RequestMapping`）。
> 原文档写的「39 Controller / 218 端点」已过时；2026-10-27 补入 exchange 模块并重测。

| 模块 | Controller | 方法级映射 | 核心端点 |
|------|-----------|-----------|---------|
| twelvet-system | 14 | 82 | 用户/角色/菜单/部门/岗位/字典/日志/租户/i18n |
| twelvet-ai | 14 | 80 | 模型配置/对话/图片/视频/音乐/语音克隆/创作工作台/日报（**转发**至 ai-service） |
| twelvet-product | 8 | 60 | 商品/社交动态/收藏/关注/AI 导购/内容审核 |
| twelvet-gen | 5 | 35 | 代码生成/数据源/模板/分组/字段类型 |
| twelvet-trade | 5 | 32 | 订单/评价/物流 |
| twelvet-runner | 4 | 20 | 互助悬赏/接单/评价/信用 |
| twelvet-exchange | 5 | 29 | 换物物品/意向/双边履约/匹配/信用 |
| twelvet-admin | 3 | 10 | 登录/登出/个人资料 |
| twelvet-payment | 3 | 7 | 支付单/租户续费/支付回调 |
| twelvet-quartz | 2 | 13 | 定时任务/任务日志 |
| twelvet-im | 1 | 8 | 会话/消息/通知 |
| twelvet-dfs | 1 | 4 | 上传/删除/列表 |
| twelvet-framework | 1 | 0 | 框架级（无端点） |
| **合计** | **66** | **380** | |

---

## 数据模型

**继承关系：** BaseEntity（审计字段 createBy/createTime/updateBy/updateTime/remark）→ 大部分实体；Serializable → 关联表（SysUserRole 等）、TradeOrder、PayOrder、FeedLike

**核心实体：** SysUser（userType: 00=ops/01=CS/02=normal）、SysRole（dataScope 5 级）、SysMenu（M=目录/C=菜单/F=按钮）、Product（商品+状态）、TradeOrder（4 状态：unpaid→shipped→done）、AiModel（4 类型：LLM/EMBEDDING/IMAGE/VIDEO）、ImMessage（5 类型：text/image/bargain/system/product_card）

**注意事项：**
- Feed 实体已统一为 twelvet-product-api 版本（含 forwardCount/hotScore/auditStatus 等字段）；旧 twelvet-feed 模块已删除
- 无 MyBatis-Plus 注解，纯 POJO + XML Mapper
- CategoryController（product 模块）使用 `/system/dict` 前缀，与 system 模块字典命名空间重叠

---

## 框架核心机制

**自定义注解：** `@Log`（操作日志 ✅）、`@EnableDynamicDataSource`（动态数据源 ✅）、`@SysDataScope`（数据权限 ✅）、`@TwSynchronized`（分布式锁 ⚠️ 未实现）

**AOP 切面：** SysLogAspect（操作日志 + 异步事件）、SysDataScopeAspect（SQL 数据权限注入，5 级：ALL/CUSTOM/DEPT/DEPT_AND_CHILD/SELF）

**过滤器：** SaInterceptor（认证）、ClearTtlDataSourceFilter（数据源上下文清理）、WebLogFilter（请求/响应日志）

**异常处理（TWTExceptionHandler）：** NotLoginException→401、NotRoleException→400、校验异常→400、TWTException→500、Exception→500

**i18n：** Caffeine → Redis → .properties 三级缓存
> ⚠️ **Redis 这一层目前是部分失效的**（2026-10-13 实跑发现）：`RedisUtils` 的 Jackson 序列化器
> 未注册 `jackson-datatype-jsr310`，含 `LocalDateTime` 的实体（如 `SysDictData`）序列化即抛
> `InvalidDefinitionException`，而异常发生在 `I18nServiceImpl.initI18n` 的**线程池里** ——
> 不致命、**无任何告警**，对应条目永远进不了 Redis（每次重启都要回查 DB）。详见
> docs/superpowers/plans/2026-10-13-delivery-readiness-stage0.md 的实测记录。

---

## AI 集成（**Java 只转发，Python 才实现**）

**分工**：Java 侧负责鉴权 / 租户 / 配额 / 转发；模型调用与 Agent 编排全在 Python `ai-service`。

- **Java `twelvet-ai`（转发层，14 Controller / 80 映射）**：`AiServiceClient` + WebClient 转发到 `ai.service.url`，
  并注入身份头（`X-User-Id`，以及计费用的 `X-Tenant-Id`）。**不直连任何模型。**
- **Java `twelvet-product`**：AI 导购入口 `AiGuideController` 转发到 ai-service 的 Agent，再把商品卡片回传前端。
- **Python `ai-service`（实现层）**：
  - **导购 Agent**：LangGraph 图编排（`entry → rag_retrieval → router → 域子图 → end`），域子图 = pre_sale / in_sale / after_sale / community；
    工具按 **L1 查询 / L2 修改 / L3 资金**分级，L2/L3 需确认令牌，L3 另带幂等键。
  - **RAG**：`AiKnowledge → AiDoc → AiDocSlice` + FAISS 索引（`data/faiss_indexes/`）。
  - **创作域**：图片 / 视频 / 音乐 / 语音克隆 / TTS·ASR，供应商经 `vendor_router` 选择。
  - **模型供应商**：默认 Agnes（`AI_BASE_URL`），Embedding 用 SiliconFlow，音乐用商汤。
  - ⚠️ **配置项 82 个但全部有默认值（0 个必填）** —— 漏配不会报错、只会静默走默认值（2026-10-13 实测）。

---

## 支付集成

```
PaymentGateway 接口 → AlipayGateway（@Service("alipay")）/ WechatGateway（@Service("wechat")）
流程：创建订单 → PayOrderService → Gateway.createPayRequest() → 回调 → handlePaySuccess()
```

---

## 开发规范

- 代码格式：Spring Java Format（`mvn spring-javaformat:apply` 或 IDEA 插件）
- 环境配置：**dev（pom `activeByDefault`）/ pro 有配置文件**；`test` / `staging` 虽在 `backend/pom.xml` 中声明，但**没有对应的 `application-*.yml`** —— 用 `-Ptest` 打包会解析出 `spring.profiles.active=test` 却无数据源配置，属虚假承诺（阶段 A 待收敛）
- 三层架构：Controller → Service → Mapper
- 权限校验：B 端用 @SaCheckPermission，C 端用 StpUtil
- API 文档：Knife4j @Schema 注解
- 响应格式：JsonResult<T>（C 端）、TableDataInfo（分页）
- 实体继承 BaseEntity 获取审计字段
- Mapper XML 放各模块 `src/main/resources/mapper/`

---

## 前端项目（`ui/`）

**Umi Max 4.x + React 18 + TypeScript + Ant Design 5.x + ECharts 5.x | 包管理器：npm（⚠️ 不要用 yarn）**

**开发代理（`ui/config/proxy.ts`）**：dev 下 `/api/` → `http://localhost:8080`，`pathRewrite` 去掉 `/api` 前缀。
前端**只与 Java BFF 通信，不直连 ai-service**（见文首「架构不变量」）。

**核心配置（`ui/src/setting.tsx`）**：生产走同源 `/api`（`isDev ? 'http://localhost:8080' : '/api'`）、
accessToken 键名 `TWT_access_token`、上传地址 `/dfs/commonUpload`。

**启动 / 构建**：

```bash
cd ui
npm ci                # 严格按 package-lock.json 安装（依赖可复现）
npm run start:dev     # 开发；默认 8000 端口，与 ai-service 默认端口冲突，可用 PORT=3000 npm run start:dev
npm run build         # 生产产物 → ui/dist/
```

**当前已知问题**：登录页预填 demo 账号与口令、并硬编码了 Basic 认证串，且**已打进 `ui/dist`**
（属阶段 D 待处理项）；真实单测仅 2 个用例。
