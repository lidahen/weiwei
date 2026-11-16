# 租户隔离第二轮（全业务表） — 上下文摘要（2026-08-28 节点）

> **2026-10-27 注**：本文引用的 2026-10-21 之前 SQL 初始化脚本已于 2026-10-27 清理；
> 相关表结构已并入 [docs/db 全量基线](db/twelvet-fast_2026-10-21.sql)，新环境按 README 基线导入。

> 下次继续这个工作，先读这个文件。

## 当前位置

- **Branch**: `feature/tenant-isolation-2`（worktree `E:\code\weiwei\.worktrees\tenant-isolation-2`，已合并 master 则删）
- **依赖**: tenant-mvp（`81f72da`）、billing-mvp（`46eca18`）、tenant-mgmt-ui（`c402455`）均已合 master

## 已完成 ✅（2026-08-28 实测全过）

| 目标 | 状态 |
|---|---|
| 9 张业务表加 `tenant_id` + 组合索引（trade_order/pay/logistics、favorite、feed/comment/like、im_conversation/message） | ✅ 实库已执行 |
| 存量回填（订单/支付/物流/收藏/带商品的动态与会话按商品归属） | ✅ MySQL 实查 |
| 存量 bug 修复：`trade_order.order_no varchar(32)→64`（代码生成 34 字符，C 端建单一直 Data too long） | ✅ 顺手修 |
| `TenantSupport.writeTid()`（framework-core）：非 BaseEntity/@Param 写路径守卫，与拦截器语义一致 | ✅ |
| trade/feed/favorite/im 全链路过滤（Mapper XML `<if>` + Service 透传） | ✅ |
| 跨租户写防护：收藏先查商品、点赞/评论先查动态（防 ID 枚举刷计数） | ✅ |
| PayOrderService（JdbcTemplate）INSERT 条件拼 tenant_id、SELECT 有上下文时过滤 | ✅ |
| ProductMapper INSERT 的 tenant_id 改 `<if>`（修复 `tenant.enabled=false` 时插 NULL 报错的回滚洞） | ✅ |
| UI 请求统一携带 `X-Tenant-Code`（`setting.tsx` TWT.tenantCode，默认 `'default'`） | ✅ |
| 平台运营视角豁免：marketplace 看板/纠纷/动态审核队列/订单统计 不过滤 | ✅ |

## 13 步运行时验证（全部通过，详见 docs/tenant-isolation-2.md）

thu 建订单/支付单/动态/点赞/评论/收藏/IM 全部落 tenant=2；pku 视角 detail/列表全空，跨租户点赞/评论/收藏被「不存在」拦截；无 Header 读兼容（21 条三租户）、写被拒；`tenant.enabled=false` 回滚后无 Header 写全部 200；配额 429 无回归。

## 关键决策（不要重新讨论）

| 决策 | 选择 | 理由 |
|---|---|---|
| 写入缺租户上下文 | 抛 TWTException（拦截器 500 / TenantSupport 400） | 严禁静默落 default 污染数据 |
| INSERT 的 tenant_id 列 | XML `<if>` 包裹 | 开关关闭时 DB `NOT NULL DEFAULT 1` 兜底，回滚真零侵入 |
| TenantSupport 位置 | framework-core（TenantContext 旁边） | 各 server 模块只依赖 framework-core，不引 system-server 循环 |
| 平台运营接口 | 不过滤 | 超管跨租户视角 |
| 支付回调 | 不过滤（按唯一键定位） | 网关请求永远没有租户 Header；回调当前未接线 |
| UI 租户 Header | 全局拦截器附加，`TWT.tenantCode` 配置 | 单前端单租户；子域名模式只改 Filter，UI 可置空 |
| TenantRunnable | 不做 | 全仓库 0 处 @Scheduled/WebSocket，@Async 不触业务表，没有传递场景 |
| order_no 修复 | 列放宽 64（不改代码生成格式） | 存量 bug 阻塞建单，最小改动 |

## 已知边界（不掩盖）

- 用户仍全局共享：跨租户 IM 会话对端不可见（第三轮 sys_user + global_user_id 解决）
- 无 Header 写操作在 tenant 开启时全部失败（设计如此，UI 已带 Header）
- UI 已浏览器实测（2026-08-28）：admin 登录后 `/home` 只显示 default 租户 6 个在售商品，thu/pku 商品不可见，截图 `docs/tenant-home-default-tenant.png`
- 单元测试 0

## 下次续做的优先级（按 ROI）

### P1
- [ ] **sys_user 加 tenant_id + global_user_id**（第三轮核心：用户-租户绑定、1 用户跨 N 租户）
- [ ] **过期租户自动降级**：`enforceProductQuota` 加 `expireAt < now → 429`（billing 已知洞）
- [ ] **AI 计量**（Redis 计数 → 超限 429）

### P2
- [ ] 支付集成（扫码续费 + 回调按 out_trade_no 反查租户落账）
- [ ] `GET /system/tenant/page` 分页端点 + Tenant 页去硬编码
- [ ] 子域名支持（`thu.ershou.com` → tenant_code，只改 TenantFilter）

### P3
- [ ] 审计日志、抽佣闭环、feed_report 等剩余表隔离按需补

## 启动方式（下次回来直接跑）

```bash
/e/Redis-8.8.0-Windows-x64-cygwin-with-Service/redis-server.exe --port 6379 --daemonize yes
cd E:/code/weiwei/.worktrees/tenant-isolation-2/backend
java -jar twelvet-admin/target/twelvet-admin.jar   # tenant.enabled=true 默认
# UI: cd ui && node_modules/.bin/max dev  →  http://localhost:8000（UI 已带 X-Tenant-Code: default）
TOKEN=$(curl -s -X POST http://127.0.0.1:8080/auth/oauth2/token -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456","grant_type":"password"}' | jq -r .data.access_token)
curl -H "X-Tenant-Code: thu" http://127.0.0.1:8080/product/home/recommend
```

## 回滚方案

```bash
# 1. 关开关
tenant:
  enabled: false
# 2. 还原代码
git revert <merge-commit>
# 3. （可选）删列，见 tenant-isolation-2-init.sql 尾部注释
```

---

**下次先读 `docs/tenant-isolation-2.md`（实现细节）和本文件；sys_user 租户绑定是最高 ROI。**
