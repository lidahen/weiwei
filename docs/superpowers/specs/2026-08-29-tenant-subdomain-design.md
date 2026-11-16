# 租户子域名支持（P2 剩余）设计 + 计划

- 日期：2026-08-29
- 主题：`thu.ershou.com` / `thu.localhost` → tenant_code，只改 TenantFilter（兑现 MVP 决策「未来换子域名只改 Filter」）
- 状态：已批准（用户预授权）

## 设计

### 解析顺序（TenantFilter，tenant.enabled=true 时）

1. **Host 子域名**：Host 去端口后若等于 `<sub>.<tenant.base-domain>`，且 sub 非保留字 → 租户 code=sub。
   - `tenant.base-domain`：dev 配 `localhost`（浏览器/系统对 `*.localhost` 解析到本机，零 hosts 配置）；生产配 `ershou.com`；空 = 关闭子域名解析。
   - 保留字（不作为租户）：`www,api,admin,static,docs,console`（代码内默认）。
2. **X-Tenant-Code Header**（现有逻辑，向后兼容）。
3. 都没有 → 不设租户（兼容模式）。

非法租户（子域名或 header 指向不存在的租户）→ 404（沿用既有语义）。子域名优先于 header：域名是更强的租户信号（租户子域名站点的管理后台也应被租户域圈定）。

### UI 配套（dev 直连 8080，代理不参与，故在请求层注入）

`requestErrorConfig.ts`：tenantCode 取 `location.hostname` 的子域名（`thu.localhost` → `thu`；`localhost`/`ershou.com` → 无子域名），无子域名时回落 `TWT.tenantCode`（'default'）；保留字跳过。生产部署到租户子域名时行为一致（后端 Filter 也会从 Host 解析，双保险）。

### 改动文件

| 文件 | 改动 |
|---|---|
| `TenantFilter.java` | +Host 子域名解析（base-domain/保留字），顺序：子域名 → header |
| `application-dev.yml` | `tenant.base-domain: localhost` |
| `application.yml` | `tenant.base-domain: ''`（生产填真实域名） |
| `ui/src/requestErrorConfig.ts` | hostname 子域名注入 X-Tenant-Code |

## 验证矩阵

| # | 用例 | 期望 |
|---|---|---|
| 1 | curl `-H "Host: thu.localhost:8080"` 列商品 | 仅 tenant=2（8 条） |
| 2 | curl Host: pku.localhost | 仅 tenant=3（7 条） |
| 3 | curl Host: unknown.localhost | 404 |
| 4 | curl Host: www.localhost | 保留字 → 不过滤（21 条全量） |
| 5 | curl Host: localhost:8080 + X-Tenant-Code: thu | header 回落生效（8 条） |
| 6 | curl Host: localhost:8080 无 header | 兼容模式（21 条） |
| 7 | 浏览器 `http://thu.localhost:8000` 首页 | UI 注入 → 仅 thu 商品 |
| 8 | 回归：正常 localhost 访问（default header） | 不变 |

## 验证记录（2026-08-29 实测 8 步全过）

| # | 用例 | 结果 |
|---|---|---|
| 1 | curl Host: thu.localhost:8080 | 200，8 条全 tenant=2 ✅ |
| 2 | curl Host: pku.localhost:8080 | 200，7 条全 tenant=3 ✅ |
| 3 | curl Host: unknown.localhost:8080 | 404 tenant not found ✅ |
| 4 | curl Host: www.localhost:8080 | 保留字跳过，21 条全量 ✅ |
| 5 | curl Host: localhost:8080 无 header | 兼容 21 条 ✅ |
| 6 | curl Host: localhost + header thu | 8 条 tenant=2（header 回落）✅ |
| 7 | 浏览器 thu.localhost:8000 登录→/home | 全部 thu 商品（罗技/索尼/戴森…），default 商品不出现 ✅ 截图 `docs/tenant-subdomain-thu-localhost.png` |
| 8 | 回归：localhost:8000 正常访问 | default 商品不变 ✅ |
