# 租户体系 MVP — 上下文摘要（2026-08-27 节点）

> 下次继续这个工作，先读这个文件。

## 当前位置

- **Git tag**: `tenant-mvp-2026-08-27`（含详细 tag message）
- **Branch**: `feature/tenant-mvp`
- **Worktree**: `E:\code\weiwei\.worktrees\tenant-mvp`
- **Commits**:
  - `81f72da` feat(tenant): 最小租户隔离 MVP
  - `a1c9eb5` fix(tenant): 验证发现的两个边界问题

## 已完成 ✅

| 目标 | 状态 | 验证 |
|---|---|---|
| `sys_tenant` 表 + 3 条种子数据（default/thu/pku） | ✅ 已建 | MySQL 实查 |
| `product.tenant_id` 列 + 索引 | ✅ 已加 | MySQL 实查 |
| `BaseEntity.tenantId` 字段 | ✅ 已加 | 编译通过 |
| `TenantContext`（ThreadLocal） | ✅ | 编译通过 |
| `TenantFilter`（读 X-Tenant-Code） | ✅ | 8 步验证全过 |
| `TenantFieldInterceptor`（INSERT 自动写） | ✅ | 编译通过 |
| `ProductMapper` XML 全部加 `AND tenant_id` 过滤 | ✅ | 8 步验证 |
| `ProductServiceImpl` / `UserProfileServiceImpl` / `TradeOrderServiceImpl` 透传 tenantId | ✅ | 编译通过 |
| `ProductController.detail` 跨租户 404 | ✅ | 步骤 6/8 实测 |
| `application.yml` `tenant.enabled=true` 开关 | ✅ | 8 步验证 |
| 文档 `docs/tenancy-mvp.md` | ✅ | 在 worktree 中 |

## 8 步运行时验证结果（2026-08-27 实测，全部通过）

| 步骤 | 请求 | 结果 |
|---|---|---|
| 1 | `X-Tenant-Code: thu` 列商品 | 8 条全 `tenant=2` ✅ |
| 2 | `X-Tenant-Code: pku` 列商品 | 7 条全 `tenant=3` ✅ |
| 3 | `X-Tenant-Code: default` 列商品 | 6 条全 `tenant=1` ✅ |
| 4 | 无 Header 列商品（兼容老调用） | 21 条三个租户 ✅ |
| 5 | `X-Tenant-Code: invalid` | HTTP 404 ✅ |
| 6 | pku 访问 thu 商品 ID=2 | HTTP 404 ✅ |
| 7 | thu 访问 thu 商品 ID=2 | HTTP 200 + 查到 KALLAX ✅ |
| 8 | thu 访问 pku 商品 ID=21 | HTTP 404 ✅ |

## 关键决策（不要重新讨论）

| 决策 | 选择 | 理由 |
|---|---|---|
| 隔离粒度 | 仅 `product` 表 | MVP 范围，其他表后置 |
| 识别方式 | HTTP Header `X-Tenant-Code` | 简单，未来换子域名只改 Filter |
| 关闭开关 | `tenant.enabled=false` | 零侵入回滚 |
| 跨租户响应 | HTTP 404（不是 403） | 不泄露存在性，避免被 ID 枚举探测 |
| 非法 code | HTTP 404（不是 400） | 同上，租户是否存在不暴露 |
| 数据隔离方式 | XML `<if test="tenantId != null">` 条件式 | MyBatis 非 Plus，关闭时整个 if 不命中 |
| 写入隔离 | `TenantFieldInterceptor` 反射注入 | 业务代码无感 |

## 验证过程中踩过的坑（已修复，避免重蹈）

| # | 坑 | 修复 |
|---|---|---|
| 1 | `res.sendError(404)` 触发 Tomcat 重走拦截器链 → SaToken 上下文未初始化 → 500 | 改用 `res.setStatus(404) + getWriter().write(...)` |
| 2 | `throw ResponseStatusException(NOT_FOUND, ...)` 被全局 `@ExceptionHandler(Exception)` 劫持成 500 | 改用 `ResponseEntity<JsonResult>` 手动设状态 |
| 3 | MySQL 客户端默认 latin1，写入中文失败 | 导入和初始化都加 `SET NAMES utf8mb4` |
| 4 | Redis 8.8 在 `E:\Redis-8.8.0-Windows-x64-cygwin-with-Service\` | 启动命令：`redis-server.exe --port 6379 --daemonize yes` |
| 5 | i18n 启动时有 Jackson LocalDateTime 序列化错误 | 非致命，不影响主流程，先放着 |

## 下次续做的优先级

按 ROI 排序：

### 第二轮（建议 1 周内做）
- [ ] **`trade_order` / `trade_pay` / `trade_logistics` 加 `tenant_id`**（订单租户隔离）
- [ ] **`feed` / `feed_comment` 加 `tenant_id`**（动态租户隔离）
- [ ] **`im_conversation` / `im_message` 加 `tenant_id`**（IM 租户隔离）
- [ ] **`product_favorite` 加 `tenant_id`**（收藏租户隔离）
- [ ] **封装 `TenantRunnable`**，给 `@Async` / `@Scheduled` / WebSocket 显式传递上下文

### 第三轮
- [ ] `sys_user` 加 `tenant_id` + `global_user_id` 关联（1 用户跨 N 租户）
- [ ] 写单元测试（当前 0 测试）

### 第四轮
- [ ] 平台超管 UI（`/admin/tenants` CRUD）
- [ ] 子域名支持（`thu.ershou.com` → tenant_code）
- [ ] 套餐 / 计费 / 审计日志

## 数据库状态

```
$ mysql -h127.0.0.1 -uroot -p123456 twelvet-fast
mysql> SELECT * FROM sys_tenant;
+----+-------------+--------+--------+
| id | tenant_code | name   | status |
+----+-------------+--------+--------+
|  1 | default     | default| 0      |
|  2 | thu         | thu    | 0      |
|  3 | pku         | pku    | 0      |
+----+-------------+--------+--------+

mysql> SELECT tenant_id, COUNT(*) FROM product GROUP BY tenant_id;
+-----------+----------+
| tenant_id | COUNT(*) |
+-----------+----------+
|         1 |        6 |
|         2 |        8 |
|         3 |        7 |
+-----------+----------+
```

## 启动方式（下次回来直接跑）

```bash
# 1. 启 Redis
/e/Redis-8.8.0-Windows-x64-cygwin-with-Service/redis-server.exe --port 6379 --daemonize yes

# 2. 切到 worktree
cd E:/code/weiwei/.worktrees/tenant-mvp/backend

# 3. 启服务
java -jar twelvet-admin/target/twelvet-admin.jar
# 服务 7 秒起来，监听 8080

# 4. 验证
curl -H "X-Tenant-Code: thu" http://127.0.0.1:8080/product/home/recommend
```

## 回滚方案

如果需要完全回滚这次改动（合并到 master 后也可以做）：

```bash
# 1. 关开关
tenant:
  enabled: false   # application.yml

# 2. 删除 sys_tenant（可选，业务表无影响）
mysql -e "DROP TABLE sys_tenant;"

# 3. 删除 product.tenant_id（可选）
mysql -e "ALTER TABLE product DROP COLUMN tenant_id;"

# 4. 还原代码
git revert a1c9eb5 81f72da
# 或：
git reset --hard <之前的 commit>
```

## 与商业化主线的衔接

这个 MVP 是商业化的 **Phase 1 P0**（租户体系），完成度可对接 **1-3 个种子客户**。

完整路线图见对话历史中的「weiwei 商业化改进分析报告」：
- Phase 1（4-6 周）：租户 + 计费 + AI 计量 + 看板
- Phase 2（6-8 周）：营销工具 + 推荐 + 消息 + 搜索
- Phase 3（8-12 周）：风控 + 开放平台 + 直播 + 跨境
- Phase 4（持续）：行业 SaaS 化

---

**下次先打开 worktree，读 `docs/tenancy-mvp.md`（实现细节）和本文件（状态/上下文）。**
