# 商业化 P2（租户页 AI 用量列 + 扫码续费支付闭环）设计文档

- 日期：2026-08-28
- 主题：变现闭环——租户套餐在线扫码续费（支付宝/微信）+ 租户管理页 AI 配额/用量展示
- 状态：已批准（用户预授权）
- 前置：round3 已合 master（AI 配额字段/renew 联动/getPlanInfo 用量已就绪）

## 1. 租户管理页 AI 用量列（纯前端）

`System/Tenant` 页：商品配额列后加「AI 配额」列（`aiCallToday / quotaAiCallDay`，0=不限），详情弹窗加 AI 行；typings 补 `quotaAiCallDay`/`aiCallToday`。套餐下拉加月价标签。

## 2. 扫码续费支付闭环

### 现状
- `PaymentGateway`(alipay/wechat) 已有 `createPayRequest/queryPayStatus/verifyNotify`，但 dev 沙箱密钥是占位符（RSA 签名报错），且**回调从未接线**（`handlePaySuccess` 无调用方）。
- 白名单已放行 `/payment/callback/alipay|wechat`（application-dev.yml）。
- `trade_pay.order_id NOT NULL` —— 续费支付没有 trade_order。

### 决策

| # | 决策点 | 选择 | 理由 |
|---|---|---|---|
| 1 | 端点归属 | trade-server 新增 `TenantRenewPayController`（`/payment/tenant-renew/*`）+ `PaymentNotifyController`（`/payment/callback/{channel}`） | 网关/PayOrderService 都在 trade；system-server 不反向依赖 trade |
| 2 | 套餐元数据共享 | `Plan`/`PlanCatalog` 从 system-server **移到 system-api**（`com.twelvet.system.api.plan`），`Plan` 加 `monthlyPrice`（free=0/basic=99/pro=299 元/月） | trade-server 续费履约要用配额元数据；避免 trade→system-server 依赖；纯元数据 record 放 API 层合理 |
| 3 | 续费订单 | 新表 `sys_tenant_renew_order`（tenant_code/plan_code/months/amount/out_trade_no 唯一/channel/status unpaid→paid/paid_at）；trade_pay.order_id 改可空（NULL=非商品订单） | 履约凭据（计划+月数）必须落库，回调按 out_trade_no 反查 |
| 4 | 支付流程 | `POST /{tenantCode}/order` → 建单+网关下单（返回 payNo/qrCode/mock 标记）→ 前端出二维码+轮询 `GET /status/{payNo}` → 已付则自动续费（幂等） | 标准扫码支付形态 |
| 5 | 履约语义 | 与直接续费一致：`base = max(expireAt, now) + months`，套餐/双配额按 PlanCatalog 覆盖；`UPDATE ... WHERE status='unpaid'` 抢占式幂等 | 提前续费不浪费；重复回调/轮询不重复履约 |
| 6 | 回调分发 | `verifyNotify` 通过后按 out_trade_no 查 `sys_tenant_renew_order`：存在→续费履约；不存在→`PayOrderService.handlePaySuccess`（商品订单，顺手接上从未接线的回调路径） | 一个入口管两类支付 |
| 7 | dev 无密钥闭环 | `billing.pay.mock=true`（dev 开、base 默认 false）：下单跳过网关返回 mock 二维码 + `POST /mock-pay/{payNo}` 直接标记支付并履约 | 沙箱密钥缺失也能端到端验证/演示；生产关闭走真网关 |
| 8 | 轮询自愈 | status 轮询时若 unpaid 且非 mock，主动 `gateway.queryPayStatus`，SUCCESS 则履约（回调丢失兜底）；mock 模式跳过网关查询 | 回调不可达（本机无公网）时的补偿 |
| 9 | trade_pay.tenant_id | 续费支付行归属**目标租户**（显式传，不取请求上下文——超管 Header 是 default） | 数据语义正确 |
| 10 | 权限 | 下单/模拟支付 `system:tenant:edit`，查单 `system:tenant:query`；回调在白名单 | 与租户管理页对齐 |

### API 一览

```
POST /payment/tenant-renew/{tenantCode}/order   {planCode, months, channel} → {payNo, qrCode, payUrl, mock, amount}
GET  /payment/tenant-renew/status/{payNo}       → {payNo, status, planCode, months, applied, mock}
POST /payment/tenant-renew/mock-pay/{payNo}     → 模拟支付（billing.pay.mock=true 时可用）
POST /payment/callback/{channel}                → 支付网关回调（白名单，验签后分发续费/商品订单）
```

### 前端交互（Tenant 页续费弹窗）

表单加「支付方式」：直接续费（原有，超管手动）/ 扫码支付（选支付宝/微信）。
扫码提交 → 弹窗切二维码 + 轮询（2s）；`mock=true` 时显示「模拟支付成功」按钮（dev 演示）；
已付 → 提示成功 + 刷新列表。antd 5.24 自带 `QRCode` 组件。

## 3. 验证矩阵

| # | 用例 | 期望 |
|---|---|---|
| 1 | thu 下单 basic×12 alipay（mock） | payNo+mock 二维码；trade_pay(order_id NULL, tenant_id=2, 1188) + renew_order 两行 |
| 2 | status（未付） | unpaid |
| 3 | mock-pay → status | paid + plan=basic/expire+12月/quota 1000+500 落库 |
| 4 | 再 mock-pay + 再查 status | 幂等：不重复续费（expire 不变） |
| 5 | 回调无验签 | 输出 fail（验签拒绝路径通） |
| 6 | 直接续费（旧路径）回归 | OK |
| 7 | 浏览器：AI 列展示 + 扫码续费 mock 闭环 | 页面/截图 |

## 4. 已知边界

- 真实网关支付需生产密钥 + 公网 notify-url（本环境只能验到「验签拒绝」一级）。
- wechat v3 回调是 JSON+证书验签，MVP 沿用 params 风格，生产接入时按渠道适配。
- 回调无租户上下文（设计如此，按 out_trade_no 定位）。
