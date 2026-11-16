# 租户隔离修复设计（v2，含已确认决策）

> 状态：**待评审，未实施**
> 版本：2026-10-27 v2（v1 为问题分析；v2 补入决策与实施级方案）
> 相关代码：`TenantFilter` / `TenantBindInterceptor` / `TenantFieldInterceptor` / `TenantContext` /
> `SaTokenConfigure` / `LoginVO` / `ProductServiceImpl` / `FeedServiceImpl` / `AiServiceClient`
> 相关数据：`sys_tenant`（default/thu）、`sys_user.tenant_id`、8 张业务表 `tenant_id`

## 1. 已确认决策（2026-10-27）

| # | 决策 | 取值 |
|---|---|---|
| 1 | 游客租户策略 | **default**：未登录只看默认社区（`tenant_code=default`） |
| 2 | 跨租户详情 | **允许只读**（商品 / 动态 / 换物物品）；互助任务、订单详情仍按租户/参与方限定 |
| 3 | 平台超管视角 | **保持跨租户全局**（无上下文即可见全部），显式带头时收敛到指定租户 |
| 4 | 本轮范围 | **Phase 1**（后端：登录态回填 + 游客策略 + 写入保护 + 租户信息返回），前端不改 |

## 2. 问题与实测证据

当前不是「隔离偏弱」，而是**隔离静默失效 + 写入落错租户**：

| 场景 | 实测结果 |
|---|---|
| `alice`（tenant=2）不带租户头发布商品 | 200，但 `product.tenant_id = 1`（错租户） |
| `alice` 带 `X-Tenant-Code: thu` | 200，`tenant_id = 2`（正确） |
| `user_demo`（tenant=1）不带头发发布 | 200，`tenant_id = 1`（碰巧正确） |
| `alice` 登录后拉首页推荐 | 13 条 **default 租户**商品（thu 商品数为 0） |
| `alice` + `X-Tenant-Code: default` | 400「当前登录租户与访问租户不一致」 |

根因链：
```
前端不发 X-Tenant-Code
  → TenantContext 恒空
     → 464 处 <if test="tenantId != null"> 全部跳过        （读泄漏）
     → INSERT 的 tenant_id 是条件列，整列省略 → DDL 默认 1  （写污染）
     → enforceProductQuota() 在上下文为空时 return         （配额/计费失效）
     → AiServiceClient 的 X-Tenant-Id 为空 → Python 按 0    （AI 计量失效）
```

## 3. 目标行为矩阵

| 调用方 | 列表/搜索 | 商品·动态·换物详情（跨租户） | 写操作 | 互助/订单详情 |
|---|---|---|---|---|
| 游客 | 仅 `default`（除非子域名/显式头指定） | ✅ 允许只读 | 需登录 | 需登录且同租户 |
| 普通用户（buyer/seller） | 仅本租户 | ✅ 允许只读 | 强制本租户 | 同租户 / 参与方 |
| 租户管理员（thuadmin） | 仅本租户 | ✅ 允许只读 | 强制本租户 | 同租户 / 参与方 |
| 平台超管（userId=1） | 默认全部；带头则收敛 | ✅ | 对业务表 INSERT 需显式租户 | 参与方或超管 |

> 「允许只读」= 详情查询不带租户过滤，但只返回公开字段；不返回手机号、详细地址、买家信息。

## 4. 实施设计（5 个改动单元）

### 4.1 登录态回填租户上下文（核心）

新增 `TenantResolveInterceptor`（`twelvet-framework-security`，与 `TenantBindInterceptor` 同包）：

```java
public boolean preHandle(...) {
    if (TenantContext.get() != null) return true;          // 子域/头部已指定
    Long loginId = StpUtil.getLoginIdDefaultNull();
    if (loginId == null) return true;                       // 游客由 TenantFilter 处理
    LoginUser u = SecurityUtils.getLoginUser();
    if (u == null || u.getTenantId() == null) return true;  // 老 token 兼容
    if (SysUser.isAdmin(u.getUserId())) return true;        // 决策 3：超管保持全局
    TenantContext.set(u.getTenantId());
    return true;
}
```

注册顺序（`SaTokenConfigure.addInterceptors`）：

```
SaInterceptor(checkLogin)
  → TenantResolveInterceptor   ← 新增
  → TenantBindInterceptor      ← 已有（上下文与登录租户不一致 → 400，admin 豁免）
```

- 上下文清理沿用 `TenantFilter` 的 `finally { TenantContext.clear(); }`（Filter 包住整个链）。
- 老 token（无 `tenantId`）跳过，与既有 `TenantBindInterceptor` 语义一致，提示重新登录。

### 4.2 游客默认租户策略

`TenantFilter` 在「子域名 → `X-Tenant-Code`」都为空时，按配置决定：

```yaml
tenant:
  enabled: true
  base-domain: ''                 # 生产填 ershou.com 启用子域名
  guest-strategy: default         # global | default | require-subdomain
  default-code: default
```

| 策略 | 行为 |
|---|---|
| `global` | 不设上下文（现状，作为回滚档） |
| `default` | 查 `default-code` 对应租户并设上下文（**本轮启用**） |
| `require-subdomain` | 无子域即 404（严格模式，本地开发需 hosts） |

实现要点：`TenantFilter` 已注入 `SysTenantMapper`，直接 `selectByCode(defaultCode)`；不存在或停用则记录 warn 并回退 `global`（避免整个站点 404）。
游客上下文的写入保护见 4.3 —— 游客写接口本来就要登录，不会被误伤。

### 4.3 写入保护（禁止静默落默认租户）

`TenantFieldInterceptor` 现状：INSERT 时若上下文为空，仅判断 SQL 文本是否含 `tenant_id`；
而 mapper 的条件列在 `tenantId == null` 时整列省略，于是**判断不出** → 走 DDL 默认值 1。

改为：

```
if (INSERT && param instanceof BaseEntity && entity.tenantId == null) {
    if (TenantContext.get() == null && isAuthenticatedRequest()) {
        throw new TWTException("缺少租户上下文，无法写入");
    }
    if (TenantContext.get() != null) entity.setTenantId(TenantContext.get());
}
```

- `isAuthenticatedRequest()`：`StpUtil.getLoginIdDefaultNull()`（try/catch 兜住非 Web 线程）——
  **登录用户缺上下文 → 直接拒绝**；定时任务/`@Async`/WebSocket 无登录态 → 保持现状（显式传租户的任务不受影响）。
- 保护范围用配置表白名单，避免误伤平台表：

```yaml
tenant:
  protected-tables:
    - product
    - trade_order
    - trade_pay
    - trade_review
    - feed
    - twelve_runner_task
    - twelve_exchange_item
    - twelve_exchange_offer
    - im_conversation
    - im_message
    - product_favorite
    - user_interest_stat
```

表名从 `BoundSql` 的 `INSERT INTO <table>` 解析（小写比对），解析失败则维持原逻辑。
`sys_*` / `qrtz_*` 等平台表不在名单内，不受影响。

> 影响提示：超管对业务表 INSERT 时若不带 `X-Tenant-Code`，会收到 400/500「缺少租户上下文」。
> 这是有意的（避免管理员在不知情的情况下把数据写进默认租户）；管理端写业务表属低频路径，
> 若后续前端支持租户切换，在请求头带上即可。

### 4.4 登录/用户信息返回租户

- `LoginVO` 增加 `tenantId`、`tenant_code`（沿用现有 `@JsonProperty` 蛇形风格）。
- `LoginServiceImpl.login()` 填充：`sysUser.getTenantId()` + `sysTenantMapper.selectById(...).getTenantCode()`。
- `GET /system/user/getInfo` 返回值增加 `tenantId` / `tenantCode`（顶层，不塞进 `user`，避免污染现有前端类型）。
- 前端本轮**不改**，仅作为后续租户切换/展示的数据基础。

### 4.5 跨租户详情只读（决策 2）

| 接口 | 现状 | 改法 |
|---|---|---|
| `GET /product/detail/{id}`（`ProductController:150`） | `selectProductById(id)` → `mapper.selectById(id, currentTid())`，跨租户 404（注释明确） | 详情查询传 `null` 租户（不过滤）；保留 404 仅用于「不存在」 |
| `GET /feed/detail/{id}`（`FeedController:42`） | `selectById(feedId, currentTid())` | 同上；仍保留 `isPublic` 可见性判断（私密动态仍不可见） |
| 换物物品详情（`ExchangeItemServiceImpl`） | mapper `selectById` 带 `tenantId` 条件 | 同上，只放开 detail，列表/意向保持租户内 |
| `GET /runner/task/{id}` | 需登录 + 租户过滤，返回取送地址 | **不放开**（含地址/联系方式） |
| 订单详情 | 参与方校验 | **不放开** |

字段边界（只读公开字段）：
- 商品：标题、图片、价格、成色、城市、卖家昵称/头像、描述；不含手机号（实体本身无）。
- 动态：正文、图片、点赞评论数、关联商品卡片；私密动态不可见。
- 换物物品：标题、图片、估值、期望品类、城市/小区级位置；不含联系方式。

### 4.6 AI 转发头（随 4.1 自动修复）

`AiServiceClient` 的 `X-Tenant-Id` 取 `TenantContext`；4.1 后登录用户与游客（default）均有值，
Python 侧 `X-Tenant-Id` 惯例不变。Python 内部 354 处 `tenant_id` 的正确性**不在本期**（单列风险）。

## 5. 数据一致性

当前库已由演示脚本（`demo-seed-data.sql` 第 5 步）做过一次归属校正，实测 13 张业务表
`tenant_id` 与 owner 租户不一致 = 0。设计上补一条可重复执行的校验 SQL（供每次改数据后自查）：

```sql
-- 期望：每行都是 0
SELECT 'product', COUNT(*) FROM product p JOIN sys_user u ON u.user_id = p.seller_id
WHERE p.tenant_id <> u.tenant_id
UNION ALL SELECT 'trade_order', COUNT(*) FROM trade_order o JOIN sys_user u ON u.user_id = o.buyer_id
WHERE o.tenant_id <> u.tenant_id
-- …（feed / runner / exchange / im / favorite / review / pay 同构）
```

## 6. 提交切分（建议顺序，每步可独立验证）

| # | 提交 | 内容 | 验证 |
|---|---|---|---|
| 1 | `feat(tenant): 登录态回填租户上下文` | TenantResolveInterceptor + 注册 | alice 无头读写都落在 thu |
| 2 | `feat(tenant): 游客默认租户策略` | TenantFilter + 配置项 | 游客无头只看到 default 数据 |
| 3 | `fix(tenant): 登录用户写业务表缺上下文直接拒绝` | TenantFieldInterceptor + 白名单 | 构造缺上下文写入 → 4xx；正常写入不受影响 |
| 4 | `feat(tenant): 登录与用户信息返回租户标识` | LoginVO / LoginServiceImpl / getInfo | 登录响应含 tenant_code |
| 5 | `feat(tenant): 商品/动态/换物详情允许跨租户只读` | 3 个 detail 查询传 null 租户 | alice 能打开 default 商品详情并只读 |
| 6 | `docs(tenant): 更新租户说明与验收矩阵` | CLAUDE.md / 本文档状态 | — |

## 7. 验收矩阵（Phase 1 完成即验）

| # | 场景 | 期望 |
|---|---|---|
| 1 | `alice`（thu）不带任何头发布商品 | 成功且 `tenant_id = 2` |
| 2 | `alice` 不带任何头拉商品列表/推荐 | 仅 thu 数据（当前 thu 商品为 0，应返回空而不是 default 的 13 条） |
| 3 | `user_demo`（default）不带头拉列表 | 仅 default 数据 |
| 4 | `alice` + `X-Tenant-Code: default` | 400 租户不一致 |
| 5 | `alice` 打开 default 商品详情 | 200 只读；不含手机号/地址 |
| 6 | `alice` 打开 default 用户的私密动态 | 404/空（可见性判断仍生效） |
| 7 | `alice` 打开 default 的互助任务详情 | 404（不放开） |
| 8 | 游客不带头拉列表 | 仅 default |
| 9 | 游客拉 thu 商品详情（带 ID） | 200 只读（详情放开） |
| 10 | `admin` 不带头 | 跨租户全部可见；带 `thu` 头 → 仅 thu |
| 11 | `admin` 不带头 INSERT 业务表（如发布商品） | 4xx「缺少租户上下文」，不再静默落 tenant 1 |
| 12 | 定时任务（无登录态）写入 | 行为不变（不抛错） |
| 13 | AI 调用 | 请求头 `X-Tenant-Id` 与上下文一致（看 ai-service 日志） |

## 8. 风险与回归重点

| 风险 | 说明 | 缓解 |
|---|---|---|
| 列表回归面大 | 464 处 tenant 条件从「常跳过」变成「常有值」，之前靠兼容模式返回全量的接口会变少 | 按模块回归：商品/动态/互助/换物/IM/订单；重点看联表（seller/买家昵称）是否被过滤成空 |
| 游客策略误伤 | 未识别 default 租户（改名/停用）会导致整站无数据 | 查不到时 warn + 回退 global |
| 超管写业务表 | 4.3 后会 4xx | 文档标注 + 需要时带头；后台写业务表路径少 |
| 定时任务 | 无登录态不拦 | 保持现状；后续单独梳理显式租户 |
| 缓存跨租户 | 推荐/热榜缓存 key 可能不含租户 | 列为已知问题，Phase 1 不修，回归时观察 |
| AI service | 直连 DB，354 处 tenant 逻辑未审计 | 不在本期，单独立项 |

## 9. 不在本期

- 前端社区选择/切换与请求头注入（Phase 3）。
- `@Async`/Quartz/WebSocket 的租户显式传递梳理。
- AI service 租户过滤审计。
- 子域名生产部署（`tenant.base-domain` 配置与证书）。

## 10. 回滚

`tenant.enabled=false` 一键回退到「无租户上下文」的兼容模式（Filter/Interceptor/写保护全部放行），
与现有设计一致；回滚后行为等同当前线上状态（含已知的隔离缺失）。
