# weiwei · 后端（`backend/`）

Weiwei 商城的 Java 后端：TwelveT Fast v3.5.4 —— **单体**应用，Maven 多模块（13 个聚合模块 / 45 个 pom）。

> 本 README 于 2026-10-13 重写。此前这里是上游 `twelvet-projects/twelvet` 的模板 README，
> 内容与本仓库不符（指向 `twelvet-s/twelvet-ui` 仓库、上游演示站 `admin/123456`、
> 内存要求写 "> 16" 而线上实际是 2C2G、教用的 `./deploy.sh nginx` 子命令在本仓库并不存在）。

---

## 环境要求

| 工具 | 版本 | 备注 |
|---|---|---|
| JDK | **17** | 必须（Spring Boot 3.5.4） |
| Maven | 3.8+ | 本仓库 `backend/mvnw.sh` 记录了 Git Bash 下 Maven 脚本会 `ClassNotFoundException`，**Windows 请直接用 `mvn.cmd`** |

---

## 构建与运行

```bash
cd backend

# 单元测试（离线；本地仓库已预热）
mvn -o test

# 打包（唯一部署产物）
mvn -o clean package -DskipTests
# → backend/twelvet-admin/target/twelvet-admin.jar

# 本地开发启动（dev profile，8080）
mvn spring-boot:run -pl twelvet-admin

# 直接跑打好的 jar
java -jar twelvet-admin/target/twelvet-admin.jar
```

**依赖**：MySQL 8（库名 `twelvet-fast`）+ Redis。启动**不依赖** ai-service（AI 接口调用时才需要）。

---

## 配置

| 文件 | 说明 |
|---|---|
| `twelvet-admin/src/main/resources/application.yml` | 通用配置：端口 8080、`context-path: /`、Sa-Token、MyBatis |
| `.../application-dev.yml` | 开发环境。**已与生产库解耦**（2026-10-13）：全部走环境变量占位符 |
| `.../application-pro.yml` | 生产环境。部署时以**外部文件**放到 jar 同级目录覆盖，优先级更高 |

**开发环境支持的环境变量覆盖**（2026-10-13 新增，便于连本地或隧道后的 MySQL/Redis）：

```bash
DB_HOST=127.0.0.1 DB_PORT=3306 DB_USERNAME=root DB_PASSWORD=123456 \
REDIS_HOST=127.0.0.1 REDIS_PORT=6379 REDIS_PASSWORD= \
java -jar twelvet-admin/target/twelvet-admin.jar
```

> 设计意图：**需要连远程/生产时用环境变量注入，不要把生产地址写回 `application-dev.yml`。**
> 该机制已在真实场景验证过（经 SSH 隧道连生产 MySQL/Redis 启动成功并完成登录）。

---

## 模块地图

| 模块 | 职责 | Controller / 方法级映射 |
|---|---|---|
| `twelvet-admin` | 启动入口 + 登录/登出/个人资料 | 3 / 10 |
| `twelvet-framework` | 框架核心（9 子模块：core / security / redis / datasource / datascope / log / jdbc / swagger / utils） | 1 / 0 |
| `twelvet-system` | 用户/角色/菜单/部门/岗位/字典/日志/租户/i18n | 14 / 82 |
| `twelvet-ai` | AI **转发层**（→ Python ai-service，不直连模型） | 14 / 80 |
| `twelvet-product` | 商品 / 社交动态 / 收藏 / 关注 / 内容审核 | 8 / 60 |
| `twelvet-gen` | 代码生成器（Velocity） | 5 / 35 |
| `twelvet-trade` | 订单 / 评价 / 物流 | 5 / 32 |
| `twelvet-runner` | 互助悬赏 / 接单 / 信用 | 4 / 20 |
| `twelvet-exchange` | 交换广场（以物换物）：物品 / 意向 / 双边履约 / 匹配 / 信用 | 5 / 29 |
| `twelvet-payment` | 支付单 / 租户续费 / 支付回调 | 3 / 7 |
| `twelvet-quartz` | 定时任务 | 2 / 13 |
| `twelvet-im` | 即时通讯 WebSocket | 1 / 8 |
| `twelvet-dfs` | 文件存储（阿里云 OSS） | 1 / 4 |

（计数方式：`*Controller.java` 文件数 / 方法级 `@(Get|Post|Put|Delete|Patch)Mapping` 数；2026-10-27 实测合计 **66 / 380**）

---

## 内部接口（服务间调用）

给 Python `ai-service` 这类**没有用户登录态**的调用方使用，因此不适用于用户令牌，走服务间令牌。

| 端点 | 调用方 | 鉴权 |
|---|---|---|
| `POST /internal/trade/order/create` | ai-service（AI 导购 Agent 下单） | 请求头 `X-Internal-Token`，与 `twelvet.internal.token`（环境变量 `INTERNAL_API_TOKEN`）**常量时间**比对 |

- **失败关闭**：未配置 `INTERNAL_API_TOKEN` 时该端点一律 **403**，绝不出现"未配置即放行"。
- **租户来源**：`sys_user.tenant_id`（**不信任**请求体传入的任何租户标识），随后复用
  `ITradeOrderService#createOrder` —— 因此跨租户商品会自然地查不到并返回"商品不存在"，
  与前端公开路径行为一致。
- **请求体**：`{"userId": <买家ID>, "productId": <商品ID>}`；业务性拒绝返回 **400**（不是 500），
  便于把"预期结果"与"真实故障"在监控里区分开。
- **边缘拦截**：原 Caddy 配置对 `/api/internal/*` 与 `/internal/*` 直接返回 404（`deploy/` 已删除，此约定保留：内部接口不应暴露到公网）。
- **本地必做**：在 `application-dev.yml` 或环境变量中设置 `INTERNAL_API_TOKEN`，
  并在 ai-service 的 `.env` 中配置同值的 `INTERNAL_API_TOKEN` 与 `JAVA_BASE_URL`。

---

## 代码规范与门禁

```bash
# 格式校验 / 自动修复（CI 的第一步，未通过直接失败）
mvn spring-javaformat:validate
mvn spring-javaformat:apply
```

仓库根的 `verify.sh` 会一次跑完三端（backend test + javaformat + ui tsc + ui jest + ai-service pytest）：

```bash
cd /f/code/weiwei && bash verify.sh
```

---

## 已知问题（截至 2026-11-03，均经实跑确认）

1. ~~**i18n 的 Redis 缓存部分静默失效**：`RedisUtils` 未注册 JSR-310 序列化器，
   含 `LocalDateTime` 的条目永远进不了 Redis~~
   → **已修复（2026-11-03）**：`RedisUtils` / `RedisConfig` 的 ObjectMapper 均注册
   `JavaTimeModule`，并补了往返序列化测试。
2. **不存在的路径返回 HTTP 500 而非 404**：`TWTExceptionHandler` 的 `Exception` 兜底吞掉了
   `NoResourceFoundException`，并把 Spring 内部消息回吐给客户端（实测
   `{"code":500,"msg":"No static resource ..."}`）→ 监控无法区分真故障与打错路径。
3. **无健康探针端点**：未引入 actuator，Caddy 的 `try_files` 又会把任意路径兜成前端 HTML，
   导致探针无意义（实测随机路径与 `/actuator/health` 返回同样的 200 + SPA 页面）。
4. **未配置优雅停机**（无 `server.shutdown`）。
5. ~~**`test` / `staging` profile 是虚假承诺**：`pom.xml` 声明了但没有对应的
   `application-*.yml`~~
   → **已修复（2026-11-03）**：两个空壳 profile 已删除，只保留 `dev`（默认）与 `product`。
6. **Knife4j 无法用配置关闭**：`Knife4jOpenApiCustomizer` 以构造器强制注入
   `Knife4jProperties`，配置开关永远不生效。
7. ~~**AI Agent 下单绕过 Java 直写 `trade_order`** → `tenant_id` 落到 `DEFAULT 1`，跨租户订单泄漏~~
   → **已修复（2026-10-13）**：Agent 改为调用内部端点（见下方「内部接口」一节），
   租户/运费/商品快照由 Java 统一负责。

以上 1–7 的处置排期见 [../docs/superpowers/specs/2026-10-13-delivery-readiness-design.md](../docs/superpowers/specs/2026-10-13-delivery-readiness-design.md)。

---

## 相关文档

- 新人总入口：[../docs/ONBOARDING.md](../docs/ONBOARDING.md)
- 项目全貌（三端）：[../CLAUDE.md](../CLAUDE.md)
- 交换广场：[一期设计](../docs/交换广场模块设计方案.md) · [二期智能匹配与信用](../docs/交换广场二期-智能匹配与信用设计.md)
- 本地开发排查：[../docs/ONBOARDING.md](../docs/ONBOARDING.md) 的 Common Issues 一节
