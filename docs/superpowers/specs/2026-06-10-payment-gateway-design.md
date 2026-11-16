# Design: 交易系统对接支付宝/微信支付

## 为什么做

当前 `TradeOrderController.create()` 使用 Mock 支付（直接标记成功），无法对接真实资金流转。需要接入支付宝和微信支付，支持担保交易模式。

## 做什么

- [ ] 支付网关抽象层（`PaymentGateway` 接口 + 支付宝/微信实现）
- [ ] 支付单数据模型（`trade_pay_order` 表）
- [ ] 订单状态机扩展（`unpaid` → `paid`）
- [ ] 创建支付接口 + 二维码/支付链接接口
- [ ] 支付宝/微信异步回调处理 + 验签
- [ ] 改造订单创建接口（创建后进入 unpaid 状态）
- [ ] 前端下单页 + 支付页（渲染二维码/等待回调）
- [ ] 配置管理（支付宝/微信参数通过环境变量）

## 不做什么（边界）

- 不做 App 原生支付（后续版本）
- 不做小程序内 JSAPI 支付（后续版本）
- 不做退款功能（仅退款能力后续扩展）
- 不做对账功能（后续版本）
- 不改动现有 `twelvet-framework` 核心代码

## 影响分析

- **影响的模块**：
  - `twelvet-trade` 交易模块（新增支付网关、支付单、回调处理）
  - `twelvet-admin` 配置文件（新增支付参数）
  - `twelvet-ui-master` 前端（新增支付页面）
- **不影响**：商品模块、IM 模块、Feed 模块、系统模块、DFS 模块

## 架构设计

```
前端(PC) ──────> TradePayController ──────> PaymentGateway(接口)
  - 渲染二维码                              ├── AlipayGateway
  - 轮询状态                                └── WechatGateway
  - 支付结果页
                                         <───── 异步回调 ─────
                                         /payment/callback/*
```

## 数据模型变更

### 新增：`trade_pay_order` 表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| pay_no | VARCHAR(64) | 支付单号 |
| order_id | BIGINT | 关联订单ID |
| order_no | VARCHAR(64) | 关联订单号 |
| channel | VARCHAR(32) | 支付通道: alipay / wechat |
| amount | DECIMAL(10,2) | 支付金额 |
| subject | VARCHAR(256) | 支付描述 |
| status | VARCHAR(32) | pending/paying/success/failed/refunded |
| pay_url | VARCHAR(1024) | 支付链接/二维码URL |
| external_trade_no | VARCHAR(64) | 第三方支付平台交易号 |
| notify_data | TEXT | 回调原始数据(JSON) |
| paid_at | DATETIME | 支付完成时间 |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |

### 订单状态机扩展

```
修改前: unshipped → shipped → done

修改后: unpaid → paid → unshipped → shipped → done
              ↑              ↑
         创建订单时      支付回调确认
```

## API 设计

### 新增接口

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| POST | `/trade/pay/create` | 创建支付单 | 需登录 |
| GET | `/trade/pay/status/{payNo}` | 查询支付状态 | 需登录 |
| GET | `/trade/pay/qrcode/{payNo}` | 获取二维码数据 | 需登录 |
| POST | `/payment/callback/alipay` | 支付宝回调 | 验签 |
| POST | `/payment/callback/wechat` | 微信回调 | 验签+证书 |

### 改造接口

| 方法 | 路径 | 变更 |
|------|------|------|
| POST | `/trade/order/create` | 创建订单后状态改为 `unpaid`，生成支付单，返回 `payNo` |

## 文件改动清单

### 后端（CREATE）
- `twelvet-trade-server/.../domain/PayOrder.java` — 支付单实体
- `twelvet-trade-server/.../service/PaymentGateway.java` — 支付网关接口
- `twelvet-trade-server/.../service/AlipayGateway.java` — 支付宝实现
- `twelvet-trade-server/.../service/WechatGateway.java` — 微信支付实现
- `twelvet-trade-server/.../service/PayOrderService.java` — 支付单业务逻辑
- `twelvet-trade-server/.../controller/TradePayController.java` — 支付控制器
- `SQL 迁移文件` — `docs/trade_pay_order.sql`

### 后端（MODIFY）
- `twelvet-trade-server/.../controller/TradeOrderController.java` — create() 改造
- `twelvet-admin/.../application-dev.yml` — 支付参数配置

### 前端（CREATE/MODIFY）
- `src/views/trade/payment/index.vue` — 支付页面
- `src/views/trade/order/index.vue` — 下单页改造
- `src/api/trade/order.js` — 新增支付 API

## 技术选型

- **支付宝 SDK**：`com.alipay.sdk:alipay-sdk-java`（沙箱版 `alipay-sdk-java-sandbox`）
- **微信支付**：不引入第三方 SDK，使用 `HttpClient` + `XML` 手动签名（微信官方不提供官方 Java SDK，业内普遍手动实现）
- **二维码渲染**：前端用 `qrcode` npm 包渲染付款码
- **回调验签**：支付宝用 SDK 内置验签，微信用 APIKey  HMAC-SHA256

## 向后兼容性

- 保留现有 `trade_order` 表结构不变，新增 `trade_pay_order` 表
- 现有订单不受影响，新订单走新流程
- 订单状态 `done` 不变，新增 `unpaid`/`paid` 状态
- 回调接口加在 `/payment/callback/*`，不影响现有路由
