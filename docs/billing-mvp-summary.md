# 租户计费/订阅 MVP — 上下文摘要（2026-08-28 节点）

> **2026-10-27 注**：本文引用的 2026-10-21 之前 SQL 初始化脚本已于 2026-10-27 清理；
> 相关表结构已并入 [docs/db 全量基线](db/twelvet-fast_2026-10-21.sql)，新环境按 README 基线导入。

> 下次继续这个工作，先读这个文件。

## 当前位置

- **Git tag**: `billing-mvp-2026-08-28`（含详细 tag message）
- **Branch**: `feature/billing-mvp`（已合并 master，merge commit `06bb175`）
- **Worktree**: `E:\code\weiwei\.worktrees\billing-mvp`（可保留也可删）
- **Commits**:
  - `46eca18` feat(billing): 最小租户计费/订阅 MVP
  - `06bb175` merge: 商业化 P0（feature/billing-mvp → master）

## 已完成 ✅

| 目标 | 状态 | 验证 |
|---|---|---|
| `sys_tenant` 加 3 字段（plan_code/expire_at/quota_product_max）+ 索引 | ✅ 已建 | MySQL 实查 |
| 种子数据：default/thu/pku → free/30 天/100 商品 | ✅ | MySQL 实查 |
| 套餐元数据硬编码（`PlanCatalog`：free/basic/pro） | ✅ | 编译通过 |
| `SysTenant` 实体加 3 字段 + getter/setter | ✅ | 编译通过 |
| `SysTenantMapper` 移到 system-api（跨模块复用） | ✅ | @MapperScan 同步更新 |
| `ISysTenantService` + `SysTenantServiceImpl`（getPlanInfo/renew） | ✅ | 6 步验证 |
| `SysTenantController`（`GET /system/tenant/{code}` + `POST /system/tenant/{code}/renew`） | ✅ | 7 步验证 |
| `QuotaExceededException` + `TWTExceptionHandler` 429 映射 | ✅ | 第 7 步返回 429 |
| `ProductServiceImpl.publish` 入口做配额检查 | ✅ | 第 7 步 429 + "商品配额已用完（7/1）" |
| `billing.enabled=true` 开关（application.yml + @Value） | ✅ | 默认值与代码一致 |
| 文档 `docs/billing-mvp.md`（实现细节，待写） | ⏳ | 仿 tenancy-mvp.md |
| MySQL 临时改动恢复（default 改回 pro/0） | ✅ | MySQL 实查 |

## 7 步运行时验证结果（2026-08-28 实测，全部通过）

| 步骤 | 请求 | 结果 |
|---|---|---|
| 1 | `POST /auth/oauth2/token` admin/123456 | 200 + access_token ✅ |
| 2 | `GET /system/tenant/default` | 200 + `planCode=pro, quotaProductMax=0, productCount=6` ✅ |
| 3 | `GET /system/tenant/thu` | 200 + `planCode=free, quotaProductMax=100, productCount=8` ✅ |
| 4 | `GET /system/tenant/invalid` | 400 + code=400 + "租户不存在: invalid" ✅ |
| 5 | `POST /system/tenant/default/renew` {planCode:pro, months:12} | 200 + "OK" ✅ |
| 6 | `GET /system/tenant/default` 验证升级 | 200 + `planCode=pro, expireAt=2028-09-27, quotaProductMax=0` ✅ |
| 7 | MySQL 临时 `quota=1` + `POST /product/publish` | **429 + "商品配额已用完（7/1），请升级套餐"** ✅ |

## 关键决策（不要重新讨论）

| 决策 | 选择 | 理由 |
|---|---|---|
| 套餐元数据 | 硬编码（`PlanCatalog`） | MVP 不建 sys_plan 表，避免铺太大；未来要支持自定义再迁移 |
| 配额检查位置 | `ProductServiceImpl.publish` 入口 | 避开 MyBatis 拦截器抛异常被 `PersistenceException` 包装，导致 Spring 走 Exception 兜底变 500 |
| SysTenantMapper 位置 | system-api（不在 server） | 跨模块共用（product/trade/feed 后续都要用），避免 product 依赖 system-server 循环 |
| 跨模块依赖 | product-server → system-api | 单向、无循环；与现有 sys_tenant 实体位置一致 |
| 套餐升级语义 | 续费月数累加（base = max(expireAt, now)） | 提前续费不浪费，到期续费从 now 起算 |
| HTTP 状态码 | 配额超限 429，业务异常 400 | 符合 HTTP 语义，前端可按 status 判断 |
| JsonResult code | TWTException 显式返回 400（修复原默认 500 不一致） | 客户端 code 与 status 对齐 |
| 零侵入回滚 | `billing.enabled=false` 关闭 Service 层检查 | 不动代码即可下线 |
| 默认值一致性 | 代码 `@Value("${billing.enabled:true}")` = yml `true` | 按 [[mvp-merge-default-consistency-check]] 严格对齐 |

## 验证过程中踩过的坑（已修复，避免重蹈）

| # | 坑 | 修复 |
|---|---|---|
| 1 | MyBatis 拦截器抛 `QuotaExceededException` 被 `SqlSessionTemplate` 包成 `PersistenceException` → Spring 走 Exception 兜底 → 500 而非 429 | 移到 `ProductServiceImpl.publish` 入口检查（Service 层抛） |
| 2 | `sys_tenant` Mapper 移到 system-api 后，system-api 没有 mybatis 依赖 → `Mapper`/`Param` 注解找不到 | system-api/pom.xml 加 `mybatis-spring-boot-starter` |
| 3 | `@MapperScan` 显式列包名，没列 `com.twelvet.system.api.mapper` → Bean 找不到 → 启动失败 | `FastApplication.java` 加上 |
| 4 | `TWTException` 默认 `JsonResult.code=500`（与 `@ResponseStatus(400)` 不一致） | 显式 `JsonResult.error(HttpStatus.BAD_REQUEST.value(), msg)` |
| 5 | `Master` 上 application.yml 有未提交修改（注释 + enabled true→false）阻塞 merge | `git stash` 后 merge；如要保留微调，可 `git stash pop` 单独提交 |

## 下次续做的优先级

按 ROI 排序：

### 第二轮（建议 1 周内做）
- [ ] **订单/支付/物流加 `tenant_id`**（订单租户隔离，续接租户 MVP 第二轮 + 商业化计费触发源）
- [ ] **`docs/billing-mvp.md`** 实现细节文档（仿 `tenancy-mvp.md`）
- [ ] **超管 UI**：`/admin/tenant/page` 列表 + 续费弹窗（前端 `twelvet-ui-master`）
- [ ] **支付集成**：支付宝/微信扫码续费（商业化变现最后一步）

### 第三轮
- [ ] **AI 计量**（Redis 计数）：`AiChatHistory` 调用次数检查 → 超限 429
- [ ] **套餐升降级 UI**：租户前台 `/product/plan` 展示当前套餐 + 升级引导
- [ ] **`TenantRunnable`**：给 `@Async`/`@Scheduled` 显式传递 tenantId 上下文

### 第四轮
- [ ] **抽佣闭环**（订单完成按比例扣平台费，账期/对账/提现）
- [ ] **子域名支持**：`thu.ershou.com` → tenant_code
- [ ] **审计日志**：超管每次续费写操作日志

## 数据库状态

```sql
mysql> SELECT id, tenant_code, plan_code, expire_at, quota_product_max FROM sys_tenant;
+----+-------------+-----------+---------------------+------------------+
| id | tenant_code | plan_code | expire_at           | quota_product_max|
+----+-------------+-----------+---------------------+------------------+
|  1 | default     | pro       | 2028-09-27 17:09:55 |                0 |  -- 续费 12 月后
|  2 | thu         | free      | 2026-09-27 16:27:20 |              100 |
|  3 | pku         | free      | 2026-09-27 16:27:20 |              100 |
+----+-------------+-----------+---------------------+------------------+

mysql> SELECT tenant_id, COUNT(*) FROM product GROUP BY tenant_id;
+-----------+----------+
| tenant_id | COUNT(*) |
+-----------+----------+
|         1 |        7 |  -- 多了 1 个（验证时插入的）
|         2 |        8 |
|         3 |        7 |
+-----------+----------+
```

## 启动方式（下次回来直接跑）

```bash
# 1. 启 Redis
/e/Redis-8.8.0-Windows-x64-cygwin-with-Service/redis-server.exe --port 6379 --daemonize yes

# 2. 切到 worktree（可选）
cd E:/code/weiwei/.worktrees/billing-mvp/backend

# 3. 启服务
java -jar twelvet-admin/target/twelvet-admin.jar
# 服务 7 秒起来，监听 8080

# 4. 验证
# 4.1 admin 登录拿 token
TOKEN=$(curl -s -X POST http://127.0.0.1:8080/auth/oauth2/token \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456","grant_type":"password"}' | jq -r .data.access_token)

# 4.2 查租户套餐
curl -H "Authorization: Bearer $TOKEN" http://127.0.0.1:8080/system/tenant/default

# 4.3 续费
curl -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"planCode":"pro","months":12}' \
  http://127.0.0.1:8080/system/tenant/default/renew
```

## 回滚方案

```bash
# 1. 关商业化开关
billing:
  enabled: false   # application.yml

# 2. 删除 sys_tenant 新字段
mysql -e "ALTER TABLE sys_tenant DROP COLUMN plan_code, DROP COLUMN expire_at, DROP COLUMN quota_product_max, DROP INDEX idx_tenant_plan;"

# 3. 还原代码
git revert 06bb175 46eca18
```

## 关键文件索引

| 文件 | 作用 |
|---|---|
| `backend/data/billing-init.sql` | DDL（ALTER + UPDATE 种子） |
| `backend/twelvet-system/twelvet-system-api/src/main/java/com/twelvet/system/api/domain/SysTenant.java` | 实体（+3 字段） |
| `backend/twelvet-system/twelvet-system-api/src/main/java/com/twelvet/system/api/mapper/SysTenantMapper.java` | Mapper（跨模块共用） |
| `backend/twelvet-system/twelvet-system-api/src/main/resources/mapper/system/SysTenantMapper.xml` | SQL |
| `backend/twelvet-system/twelvet-system-server/src/main/java/com/twelvet/system/server/config/plan/Plan.java` | 套餐 record |
| `backend/twelvet-system/twelvet-system-server/src/main/java/com/twelvet/system/server/config/plan/PlanCatalog.java` | 套餐硬编码（free/basic/pro） |
| `backend/twelvet-system/twelvet-system-server/src/main/java/com/twelvet/system/server/service/ISysTenantService.java` | 服务接口 |
| `backend/twelvet-system/twelvet-system-server/src/main/java/com/twelvet/system/server/service/impl/SysTenantServiceImpl.java` | 服务实现 |
| `backend/twelvet-system/twelvet-system-server/src/main/java/com/twelvet/system/server/controller/SysTenantController.java` | 超管 API |
| `backend/twelvet-framework/twelvet-framework-core/src/main/java/com/twelvet/framework/core/exception/QuotaExceededException.java` | 配额超限异常 |
| `backend/twelvet-framework/twelvet-framework-core/src/main/java/com/twelvet/framework/core/handler/TWTExceptionHandler.java` | 异常映射（429/400） |
| `backend/twelvet-product/twelvet-product-server/src/main/java/com/twelvet/product/server/service/impl/ProductServiceImpl.java` | publish 配额检查 |
| `backend/twelvet-admin/src/main/java/com/twelvet/admin/FastApplication.java` | @MapperScan 补 system-api |
| `backend/twelvet-admin/src/main/resources/application.yml` | billing.enabled=true |

---

**下次先打开 worktree，读 `docs/billing-mvp.md`（实现细节，待补）和本文件。**
