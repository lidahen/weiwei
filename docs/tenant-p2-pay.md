# 商业化 P2（租户页 AI 用量列 + 扫码续费支付闭环）

> **2026-10-27 注**：本文引用的 2026-10-21 之前 SQL 初始化脚本已于 2026-10-27 清理；
> 相关表结构已并入 [docs/db 全量基线](db/twelvet-fast_2026-10-21.sql)，新环境按 README 基线导入。

> 设计：`docs/superpowers/specs/2026-08-28-tenant-p2-pay-design.md`
> 摘要：`docs/tenant-p2-pay-summary.md`

## 启用步骤（已有库执行一次）

```bash
mysql -uroot -p twelvet-fast < backend/data/tenant-p2-pay-init.sql
```

内容：新表 `sys_tenant_renew_order`（续费履约凭据）+ `trade_pay.order_id` 改可空（NULL=非商品订单）。

## API

```
POST /payment/tenant-renew/{tenantCode}/order   {planCode, months, channel} → {payNo, qrCode, payUrl, mock, amount}
     权限 system:tenant:edit；金额 = PlanCatalog.monthlyPrice × months（basic ¥99/月、pro ¥299/月）
GET  /payment/tenant-renew/status/{payNo}       → {status, applied, planCode, months, amount, mock}
     权限 system:tenant:query；unpaid 且非 mock 时主动问网关（回调丢失自愈），已付未履约则补履约
POST /payment/tenant-renew/mock-pay/{payNo}     → 模拟支付（billing.pay.mock=true 才可用；生产 400）
POST|GET /payment/callback/{channel}            → 网关回调（白名单）：验签 → 按 out_trade_no 分发
     存在续费单 → 续费履约；否则 → PayOrderService.handlePaySuccess（商品订单，历史上首次接线）
```

## 履约语义（与直接续费一致）

- `markPaid` 抢占式幂等：`UPDATE ... WHERE status='unpaid'`，抢到的线程才执行套餐落库（回调/轮询/模拟并发安全）。
- `expire = max(expireAt, now) + months`（提前续费不浪费）；`plan_code/quota_product_max/quota_ai_call_day` 按 PlanCatalog 覆盖。
- trade_pay 同步标 paid；续费支付行 `order_id=NULL`、`tenant_id=目标租户`（显式传，与超管请求上下文无关）。

## 定价（Plan.monthlyPrice，硬编码）

| 套餐 | 商品 | AI/日 | 月价 |
|---|---|---|---|
| free | 100 | 50 | ¥0（支付通道拒绝，走直接续费） |
| basic | 1000 | 500 | ¥99 |
| pro | 不限 | 不限 | ¥299 |

## mock 模式（dev 演示闭环）

`billing.pay.mock=true`（application-dev.yml；base 默认 false，生产必须 false）：
下单跳过网关，二维码内容为 `https://mock.pay/weiwei/{payNo}`，前端显示「模拟支付成功（dev）」按钮 → `POST /mock-pay/{payNo}` 完成履约 → 轮询自动确认。
真实支付需生产密钥 + 公网 notify-url（`payment.alipay.notify-url`），回调验签失败返回 `fail`（已实测该拒绝路径）。

## 前端（System/Tenant 页）

- 列表新增「AI 配额(今日)」列（`aiCallToday / quotaAiCallDay`，0=不限）；详情弹窗同款。
- 续费弹窗两段式：表单（套餐/月数/支付方式 直接续费|扫码/渠道）→ 二维码阶段（antd QRCode + 应付金额 + 2s 轮询 + mock 按钮），支付成功自动关弹窗刷新列表。
- 套餐下拉带价格标签。

## 验证记录（2026-08-29 实测 7 步全过）

| # | 用例 | 结果 |
|---|---|---|
| 1 | thu 下单 basic×12 alipay（mock） | payNo + mock 二维码；trade_pay(order_id=NULL, tenant_id=2, ¥1188) + renew_order unpaid ✅ |
| 2 | status（未付） | unpaid / applied=false ✅ |
| 3 | mock-pay → status → 套餐 | paid / applied=true；thu=basic、expire 2026-09-27→2027-09-27、配额 1000/500 ✅ |
| 4 | 再 mock-pay | 幂等：expire 不变 ✅ |
| 5 | 伪造回调 | 输出 fail（验签拒绝路径通）✅ |
| 6 | 直接续费旧路径 | OK ✅ |
| 7 | 浏览器实测（AI 列 + 扫码闭环 + 截图） | 见 `docs/tenant-page-p2-pay.png` ✅（实测中发现并修复：弹窗 open 状态在 Bigfish dev（regenerator 转译）下偶发不随 setState 关闭——加 `forceModalDom` DOM 兜底，两端（开/关）都做，状态正常时无副作用） |

测试支付数据已清理，租户种子状态已还原（default=pro、thu/pku=free）。

## 已知边界

- 真实网关支付链路需生产密钥/公网，本环境最多验证到「验签拒绝」一级。
- wechat v3 回调为 JSON+证书验签，MVP 沿用 params 风格（`verifyNotify` 由各网关自行适配，接入生产时补）。
- 回调无租户上下文（设计如此）；`/payment/callback` 已在 `security.ignore.urls` 白名单。

## 回滚

`billing.pay.mock=false`（关模拟支付）；`DROP TABLE sys_tenant_renew_order`（可选）；`git revert`。
