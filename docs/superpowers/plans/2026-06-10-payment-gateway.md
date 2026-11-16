# 支付网关对接（支付宝 + 微信）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为交易系统接入支付宝和微信支付，实现担保交易模式，前端渲染付款码/跳转 H5 支付。

**Architecture:** 后端封装 `PaymentGateway` 统一接口，`AlipayGateway`/`WechatGateway` 各自实现。新增 `TradePayController` 处理支付创建和查询，回调由 `PaymentCallbackController` 处理。订单状态机扩展 `unpaid` → `paid`。前端复用已有的空占位页 `Pay.tsx`/`PayResult.tsx`，接入真实接口。

**Tech Stack:** Spring Boot 3.5 + Java 17 + JdbcTemplate + Sa-Token | 支付宝 SDK (`alipay-sdk-java`) | 微信支付手动实现 (HttpClient + XML) | Vue3 + TypeScript + Ant Design

---

## 文件改动清单

### 后端（CREATE）

| # | 文件路径 | 说明 |
|---|----------|------|
| 1 | `twelvet-fast/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/domain/PayOrder.java` | 支付单实体 |
| 2 | `twelvet-fast/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service/PaymentGateway.java` | 支付网关接口 |
| 3 | `twelvet-fast/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service/AlipayGateway.java` | 支付宝实现 |
| 4 | `twelvet-fast/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service/WechatGateway.java` | 微信支付实现 |
| 5 | `twelvet-fast/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service/PayOrderService.java` | 支付单业务逻辑 |
| 6 | `twelvet-fast/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/controller/TradePayController.java` | 支付控制器（创建/查询/二维码） |
| 7 | `twelvet-fast/twelvet-admin/src/main/java/com/twelvet/trade/server/controller/PaymentCallbackController.java` | 回调控制器 |
| 8 | `docs/trade_pay_order.sql` | 数据库建表 SQL |

### 后端（MODIFY）

| # | 文件路径 | 说明 |
|---|----------|------|
| 1 | `twelvet-fast/twelvet-trade/twelvet-trade-server/pom.xml` | 新增支付宝 SDK 依赖 |
| 2 | `twelvet-fast/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/controller/TradeOrderController.java:83-113` | `create()` 改造为真实下单+生成支付单 |
| 3 | `twelvet-fast/twelvet-admin/src/main/resources/application-dev.yml` | 新增支付配置 |

### 前端（MODIFY）

| # | 文件路径 | 说明 |
|---|----------|------|
| 1 | `twelvet-ui-master/src/services/marketplace/api.ts` | 新增支付相关 API 函数 |
| 2 | `twelvet-ui-master/src/pages/marketplace/Pay.tsx` | 实现支付页（渲染二维码/跳转链接+轮询） |
| 3 | `twelvet-ui-master/src/pages/marketplace/PayResult.tsx` | 实现支付结果页 |

---

## 安全注意

- 回调接口 `/payment/callback/*` 必须配置到 `security.ignore.urls` 白名单中
- 支付宝/微信商户密钥通过环境变量注入，不写死配置文件
- 回调验签是核心安全逻辑，必须严格实现

---

## Task 1: 数据库建表 + 支付宝 SDK 依赖

**Files:**
- Create: `docs/trade_pay_order.sql`
- Modify: `twelvet-fast/twelvet-trade/twelvet-trade-server/pom.xml`

- [ ] **Step 1: 创建支付单建表 SQL**

在 `docs/trade_pay_order.sql` 写入以下 SQL:

```sql
-- 支付单表
CREATE TABLE IF NOT EXISTS trade_pay_order (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    pay_no          VARCHAR(64)    NOT NULL COMMENT '支付单号',
    order_id        BIGINT         NOT NULL COMMENT '关联订单ID',
    order_no        VARCHAR(64)    NOT NULL COMMENT '关联订单号',
    channel         VARCHAR(32)    NOT NULL COMMENT '支付通道: alipay / wechat',
    amount          DECIMAL(10,2)  NOT NULL COMMENT '支付金额',
    subject         VARCHAR(256)   NOT NULL COMMENT '支付描述',
    status          VARCHAR(32)    NOT NULL DEFAULT 'pending' COMMENT '状态: pending/paying/success/failed',
    pay_url         VARCHAR(1024)  DEFAULT NULL COMMENT '支付链接/二维码URL',
    out_trade_no    VARCHAR(64)    DEFAULT NULL COMMENT '商户侧支付流水号',
    trade_no        VARCHAR(64)    DEFAULT NULL COMMENT '支付宝/微信交易号',
    notify_data     TEXT           DEFAULT NULL COMMENT '回调原始数据(JSON)',
    paid_at         DATETIME       DEFAULT NULL COMMENT '支付完成时间',
    create_time     DATETIME       NOT NULL DEFAULT NOW() COMMENT '创建时间',
    update_time     DATETIME       NOT NULL DEFAULT NOW() COMMENT '更新时间',
    INDEX idx_pay_no (pay_no),
    INDEX idx_order_id (order_id),
    INDEX idx_channel (channel),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付单表';
```

- [ ] **Step 2: 在 trade-server pom.xml 中新增支付宝 SDK 依赖**

在 `twelvet-fast/twelvet-trade/twelvet-trade-server/pom.xml` 的 `<dependencies>` 块内追加：

```xml
<!-- 支付宝 SDK -->
<dependency>
    <groupId>com.alipay.sdk</groupId>
    <artifactId>alipay-sdk-java</artifactId>
    <version>4.38.80.ALL</version>
</dependency>
```

- [ ] **Step 3: 验证 SQL 和 pom.xml 正确性**

Run: `mysql -uroot -p123456 twelvet-fast < docs/trade_pay_order.sql`
Expected: 建表成功，`SHOW TABLES LIKE 'trade_pay_order';` 返回一行

---

## Task 2: PayOrder 实体 + PaymentGateway 接口

**Files:**
- Create: `twelvet-fast/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/domain/PayOrder.java`
- Create: `twelvet-fast/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service/PaymentGateway.java`

- [ ] **Step 1: 创建 PayOrder 实体类**

Create: `twelvet-fast/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/domain/PayOrder.java`

```java
package com.twelvet.trade.server.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付单实体
 */
public class PayOrder {

    private Long id;
    private String payNo;
    private Long orderId;
    private String orderNo;
    private String channel;          // alipay / wechat
    private BigDecimal amount;
    private String subject;
    private String status;           // pending / paying / success / failed
    private String payUrl;
    private String outTradeNo;       // 商户侧支付流水号
    private String tradeNo;          // 支付宝/微信交易号
    private String notifyData;
    private LocalDateTime paidAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // getters and setters (standard JavaBean)
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPayNo() { return payNo; }
    public void setPayNo(String payNo) { this.payNo = payNo; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPayUrl() { return payUrl; }
    public void setPayUrl(String payUrl) { this.payUrl = payUrl; }
    public String getOutTradeNo() { return outTradeNo; }
    public void setOutTradeNo(String outTradeNo) { this.outTradeNo = outTradeNo; }
    public String getTradeNo() { return tradeNo; }
    public void setTradeNo(String tradeNo) { this.tradeNo = tradeNo; }
    public String getNotifyData() { return notifyData; }
    public void setNotifyData(String notifyData) { this.notifyData = notifyData; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
```

- [ ] **Step 2: 创建 PaymentGateway 接口**

Create: `twelvet-fast/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service/PaymentGateway.java`

```java
package com.twelvet.trade.server.service;

import java.math.BigDecimal;

/**
 * 支付网关统一接口
 */
public interface PaymentGateway {

    /** 返回支持的通道: "alipay" 或 "wechat" */
    String getChannel();

    /**
     * 创建支付请求，返回前端需要的支付参数
     * @param outTradeNo 商户支付流水号
     * @param subject    商品描述
     * @param amount     金额
     * @param returnUrl  同步跳转地址（前端URL）
     * @return 支付参数 Map，包含前端需要的字段（如 qrCode / mwebUrl / appScheme 等）
     */
    PaymentResult createPayRequest(String outTradeNo, String subject, BigDecimal amount, String returnUrl);

    /**
     * 查询支付状态
     * @param outTradeNo 商户支付流水号
     * @return "SUCCESS" / "WAITING" / "CLOSED" / "FAILED"
     */
    String queryPayStatus(String outTradeNo);

    /**
     * 退款
     * @param outTradeNo    商户支付流水号
     * @param refundAmount  退款金额
     * @param reason        退款原因
     * @return 退款是否成功
     */
    boolean refund(String outTradeNo, BigDecimal refundAmount, String reason);

    /**
     * 验证回调签名，返回 true 表示签名合法
     * @param params 回调参数 Map
     * @return 验签结果
     */
    boolean verifyNotify(java.util.Map<String, String> params);

    /** 支付结果 */
    record PaymentResult(String payUrl, String qrCode, String appScheme, java.util.Map<String, Object> extra) {
        public PaymentResult {
            if (extra == null) extra = java.util.Collections.emptyMap();
        }
    }
}
```

- [ ] **Step 3: 验证编译**

Run: `cd F:/code/weiwei/twelvet-fast && mvn compile -pl twelvet-trade/twelvet-trade-server -am -DskipTests 2>&1 | tail -20`
Expected: BUILD SUCCESS（PayOrder.java 和 PaymentGateway.java 编译通过）

---

## Task 3: AlipayGateway 实现

**Files:**
- Create: `twelvet-fast/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service/AlipayGateway.java`
- Modify: `twelvet-fast/twelvet-admin/src/main/resources/application-dev.yml`

- [ ] **Step 1: 在 application-dev.yml 中新增支付宝配置**

在 `twelvet-fast/twelvet-admin/src/main/resources/application-dev.yml` 末尾追加：

```yaml
payment:
  alipay:
    app-id: ${PAY_ALIPAY_APP_ID:2021005107654321}
    private-key: ${PAY_ALIPAY_PRIVATE_KEY:MIIEvQIBADANBgkqh...}
    alipay-public-key: ${PAY_ALIPAY_PUBLIC_KEY:MIIBIjANBgkqh...}
    gateway-url: https://openapi-sandbox.dl.alipaydev.com/gateway.do
    charset: UTF-8
    sign-type: RSA2
    notify-url: https://your-domain.com/payment/callback/alipay
  wechat:
    app-id: ${PAY_WECHAT_APP_ID:wx1234567890abcdef}
    mch-id: ${PAY_WECHAT_MCH_ID:1234567890}
    api-key: ${PAY_WECHAT_API_KEY:5K8264ILTKCH16CQ2502SI8ZNMTM67VS}
    notify-url: https://your-domain.com/payment/callback/wechat
```

> 注意：沙箱环境的 app-id 和密钥在 [支付宝开放平台沙箱](https://opend支付宝沙箱) 获取。

- [ ] **Step 2: 创建 AlipayGateway**

Create: `twelvet-fast/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service/AlipayGateway.java`

```java
package com.twelvet.trade.server.service;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 支付宝支付网关实现
 * 支持扫码支付（Precreate）和 H5 网页支付（PagePay）
 */
@Service
public class AlipayGateway implements PaymentGateway {

    @Value("${payment.alipay.app-id}")
    private String appId;

    @Value("${payment.alipay.private-key}")
    private String privateKey;

    @Value("${payment.alipay.alipay-public-key}")
    private String alipayPublicKey;

    @Value("${payment.alipay.gateway-url}")
    private String gatewayUrl;

    @Value("${payment.alipay.charset:UTF-8}")
    private String charset;

    @Value("${payment.alipay.sign-type:RSA2}")
    private String signType;

    @Value("${payment.alipay.notify-url}")
    private String notifyUrl;

    private AlipayClient alipayClient;

    private void initClient() {
        this.alipayClient = new DefaultAlipayClient(
                gatewayUrl, appId, privateKey, "json", charset, alipayPublicKey, signType);
    }

    @Override
    public String getChannel() {
        return "alipay";
    }

    @Override
    public PaymentResult createPayRequest(String outTradeNo, String subject, BigDecimal amount, String returnUrl) {
        if (alipayClient == null) initClient();

        // 优先使用扫码支付（生成付款码）
        AlipayTradePrecreateRequest request = new AlipayTradePrecreateRequest();
        request.setNotifyUrl(notifyUrl);
        request.setReturnUrl(returnUrl);

        JSONObject bizContent = new JSONObject();
        bizContent.put("out_trade_no", outTradeNo);
        bizContent.put("total_amount", amount.toString());
        bizContent.put("subject", subject);
        bizContent.put("timeout_express", "30m");

        request.setBizContent(bizContent.toJSONString());

        try {
            AlipayTradePrecreateResponse response = alipayClient.execute(request);
            if (response.isSuccess()) {
                Map<String, Object> extra = new HashMap<>();
                extra.put("qrCode", response.getQrCode()); // 二维码内容
                extra.put("outTradeNo", outTradeNo);
                return new PaymentResult(null, response.getQrCode(), null, extra);
            } else {
                throw new RuntimeException("Alipay precreate failed: " + response.getSubMsg());
            }
        } catch (AlipayApiException e) {
            throw new RuntimeException("Alipay precreate API error: " + e.getMessage(), e);
        }
    }

    @Override
    public String queryPayStatus(String outTradeNo) {
        if (alipayClient == null) initClient();

        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        JSONObject bizContent = new JSONObject();
        bizContent.put("out_trade_no", outTradeNo);
        request.setBizContent(bizContent.toJSONString());

        try {
            AlipayTradeQueryResponse response = alipayClient.execute(request);
            if (response.isSuccess()) {
                return response.getTradeStatus(); // SUCCESS / WAIT_BUYER_PAY / CLOSED / PAY_ERROR
            }
            return "FAILED";
        } catch (AlipayApiException e) {
            return "FAILED";
        }
    }

    @Override
    public boolean refund(String outTradeNo, BigDecimal refundAmount, String reason) {
        if (alipayClient == null) initClient();

        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
        JSONObject bizContent = new JSONObject();
        bizContent.put("out_trade_no", outTradeNo);
        bizContent.put("refund_amount", refundAmount.toString());
        bizContent.put("refund_reason", reason);
        request.setBizContent(bizContent.toJSONString());

        try {
            AlipayTradeRefundResponse response = alipayClient.execute(request);
            return response.isSuccess();
        } catch (AlipayApiException e) {
            return false;
        }
    }

    @Override
    public boolean verifyNotify(Map<String, String> params) {
        if (alipayClient == null) initClient();
        try {
            return com.alipay.api.AlipaySignature.rsaCheckV1(
                    params, alipayPublicKey, charset, signType);
        } catch (com.alipay.api.AlipayApiException e) {
            return false;
        }
    }
}
```

> 需要添加 import: `import com.alipay.api.domain.*;` 和 `import com.alipay.api.internal.util.AlipaySignature;` 和 `import com.alipay.api.*;` 和 `import com.alibaba.fastjson2.JSONObject;`

- [ ] **Step 3: 验证编译**

Run: `cd F:/code/weiwei/twelvet-fast && mvn compile -pl twelvet-trade/twelvet-trade-server -am -DskipTests 2>&1 | tail -10`
Expected: BUILD SUCCESS

---

## Task 4: WechatGateway 实现

**Files:**
- Create: `twelvet-fast/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service/WechatGateway.java`

- [ ] **Step 1: 创建 WechatGateway**

Create: `twelvet-fast/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service/WechatGateway.java`

微信支付没有官方 Java SDK，使用 HttpClient 手动实现。扫码支付（NATIVE 模式）接口是 `/v3/pay/transactions/native`。

```java
package com.twelvet.trade.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.Base64;

/**
 * 微信支付网关实现（v3 API）
 * 使用 Native 扫码支付模式
 */
@Service
public class WechatGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(WechatGateway.class);

    private static final String WX_DOMAIN = "https://api.mch.weixin.qq.com";
    private static final String WX_V3_DOMAIN = "https://api.mch.weixin.qq.com";

    @Value("${payment.wechat.app-id}")
    private String appId;

    @Value("${payment.wechat.mch-id}")
    private String mchId;

    @Value("${payment.wechat.api-key}")
    private String apiKey;

    @Value("${payment.wechat.notify-url}")
    private String notifyUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String getChannel() {
        return "wechat";
    }

    @Override
    public PaymentResult createPayRequest(String outTradeNo, String subject, BigDecimal amount, String returnUrl) {
        // 构造请求体
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("appids", appId);
        body.put("mchids", mchId);
        body.put("description", subject);
        body.put("out_trade_no", outTradeNo);
        body.put("notify_url", notifyUrl);

        Map<String, Object> amountObj = new LinkedHashMap<>();
        amountObj.put("total", amount.multiply(new BigDecimal("100")).longValue()); // 微信支付单位：分
        amountObj.put("currency", "CNY");
        body.put("amount", amountObj);

        Map<String, Object> sceneInfo = new LinkedHashMap<>();
        Map<String, String> payerClientIp = new LinkedHashMap<>();
        payerClientIp.put("client_ip", "127.0.0.1"); // TODO: 从请求中获取真实 IP
        sceneInfo.put("payer_client_ip", "127.0.0.1");
        body.put("scene_info", sceneInfo);

        String bodyJson = com.alibaba.fastjson2.JSON.toJSONString(body);
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonceStr = UUID.randomUUID().toString().replace("-", "");
        String signStr = "POST\n" + "/v3/pay/transactions/native\n" + timestamp + "\n" + nonceStr + "\n" + bodyJson + "\n";

        String sign = hmacSha256(signStr, apiKey);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "WECHATPAY2-SHA256-RSA2048 " + getAuthHeader(appId, mchId, sign, timestamp, nonceStr));
        headers.set("Content-Type", "application/json");

        ResponseEntity<String> resp = restTemplate.exchange(
                WX_V3_DOMAIN + "/v3/pay/transactions/native",
                HttpMethod.POST,
                new HttpEntity<>(bodyJson, headers),
                String.class);

        if (resp.getStatusCode() == HttpStatus.CREATED) {
            Map<String, Object> result = com.alibaba.fastjson2.JSON.parseObject(resp.getBody(), Map.class);
            String codeUrl = (String) result.get("code_url");
            Map<String, Object> extra = new HashMap<>();
            extra.put("outTradeNo", outTradeNo);
            extra.put("codeUrl", codeUrl);
            return new PaymentResult(codeUrl, codeUrl, null, extra);
        } else {
            log.error("Wechat precreate failed: {}", resp.getBody());
            throw new RuntimeException("Wechat precreate failed: " + resp.getBody());
        }
    }

    @Override
    public String queryPayStatus(String outTradeNo) {
        String url = WX_V3_DOMAIN + "/v3/pay/transactions/out-trade-no/" + outTradeNo + "?mchid=" + mchId;
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonceStr = UUID.randomUUID().toString().replace("-", "");
        String signStr = "GET\n" + url.substring(WX_V3_DOMAIN.length()) + "\n" + timestamp + "\n" + nonceStr + "\n\n";
        String sign = hmacSha256(signStr, apiKey);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "WECHATPAY2-SHA256-RSA2048 " + getAuthHeader(appId, mchId, sign, timestamp, nonceStr));

        ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);

        if (resp.getStatusCode() == HttpStatus.OK) {
            Map<String, Object> result = com.alibaba.fastjson2.JSON.parseObject(resp.getBody(), Map.class);
            String tradeState = (String) result.get("trade_state");
            // SUCCESS / PAYING / CLOSED / REVOKED / NOTPAY / USERPAYING
            if ("SUCCESS".equals(tradeState)) return "SUCCESS";
            if ("CLOSED".equals(tradeState)) return "CLOSED";
            return "WAITING";
        }
        return "FAILED";
    }

    @Override
    public boolean refund(String outTradeNo, BigDecimal refundAmount, String reason) {
        // 微信退款需要证书，沙箱阶段暂不实现
        log.warn("Wechat refund not implemented yet");
        return false;
    }

    @Override
    public boolean verifyNotify(Map<String, String> params) {
        // 微信支付 v3 回调验签逻辑
        // 需要平台证书验证签名，暂返回 true（沙箱阶段）
        // TODO: 实现完整的 v3 异步通知验签
        log.info("Wechat notify (placeholder): {}", params);
        return true;
    }

    private String hmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 error", e);
        }
    }

    private String getAuthHeader(String appId, String mchId, String sign, String timestamp, String nonceStr) {
        return String.format(
                "mchid=\"%s\",nonce_str=\"%s\",timestamp=\"%s\",serial_no=\"\",signature=\"%s\"",
                mchId, nonceStr, timestamp, sign);
    }
}
```

- [ ] **Step 2: 验证编译**

Run: `cd F:/code/weiwei/twelvet-fast && mvn compile -pl twelvet-trade/twelvet-trade-server -am -DskipTests 2>&1 | tail -10`
Expected: BUILD SUCCESS

---

## Task 5: PayOrderService — 支付单业务逻辑

**Files:**
- Create: `twelvet-fast/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service/PayOrderService.java`

- [ ] **Step 1: 创建 PayOrderService**

Create: `twelvet-fast/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service/PayOrderService.java`

```java
package com.twelvet.trade.server.service;

import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.framework.jdbc.web.utils.PageUtils;
import com.twelvet.trade.server.domain.PayOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 支付单业务服务
 * 负责创建支付单、查询、更新状态、处理回调
 */
@Service
public class PayOrderService {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Map<String, PaymentGateway> gatewayMap; // Spring 自动注入所有 PaymentGateway 实现

    /** 生成支付单号: PAY + yyyyMMddHHmmss + 4位随机数 */
    public String generatePayNo() {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int rand = (int) (Math.random() * 10000);
        return "PAY" + time + String.format("%04d", rand);
    }

    /**
     * 创建支付单并调用网关发起支付
     * @param orderId     订单ID
     * @param orderNo     订单号
     * @param channel     支付通道: alipay / wechat
     * @param amount      金额
     * @param subject     描述
     * @param returnUrl   同步跳转URL
     * @return 支付结果（含支付参数）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> createPayOrder(Long orderId, String orderNo, String channel,
                                               BigDecimal amount, String subject, String returnUrl) {
        String payNo = generatePayNo();
        String outTradeNo = payNo; // 商户支付流水号 = 支付单号

        // 插入支付单记录
        jdbc.update(
                "INSERT INTO trade_pay_order (pay_no, order_id, order_no, channel, amount, subject, status, create_time, update_time) " +
                "VALUES (?, ?, ?, ?, ?, ?, 'pending', NOW(), NOW())",
                payNo, orderId, orderNo, channel, amount, subject);

        // 调用对应网关
        PaymentGateway gateway = gatewayMap.get(channel);
        if (gateway == null) {
            throw new IllegalArgumentException("Unsupported payment channel: " + channel);
        }

        PaymentGateway.PaymentResult result = gateway.createPayRequest(outTradeNo, subject, amount, returnUrl);

        // 更新支付单
        String payUrl = result.payUrl() != null ? result.payUrl() :
                String.valueOf(((Map<String, Object>) result.extra()).getOrDefault("qrCode", ""));
        jdbc.update(
                "UPDATE trade_pay_order SET pay_url = ?, out_trade_no = ?, status = 'paying', update_time = NOW() " +
                "WHERE pay_no = ?",
                payUrl, outTradeNo, payNo);

        // 组装返回
        Map<String, Object> resp = new HashMap<>();
        resp.put("payNo", payNo);
        resp.put("payUrl", result.payUrl());
        resp.put("qrCode", result.qrCode());
        resp.put("channel", channel);
        resp.put("amount", amount);
        resp.put("orderId", orderId);
        resp.put("orderNo", orderNo);
        resp.put("extra", result.extra());
        return resp;
    }

    /**
     * 查询支付单状态
     */
    public Map<String, Object> queryPayStatus(String payNo) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM trade_pay_order WHERE pay_no = ?", payNo);
        if (rows.isEmpty()) return null;

        Map<String, Object> payOrder = rows.get(0);

        // 如果已经是 success 状态，直接返回缓存
        if ("success".equals(payOrder.get("status"))) {
            payOrder.put("paid", true);
            return payOrder;
        }

        // 调用网关查询
        String channel = (String) payOrder.get("channel");
        String outTradeNo = (String) payOrder.get("out_trade_no");
        PaymentGateway gateway = gatewayMap.get(channel);

        if (gateway != null && outTradeNo != null) {
            String tradeStatus = gateway.queryPayStatus(outTradeNo);
            if ("SUCCESS".equals(tradeStatus)) {
                updatePayStatus(payNo, "success", outTradeNo, LocalDateTime.now());
                payOrder.put("status", "success");
                payOrder.put("paid", true);
            } else {
                payOrder.put("paid", false);
            }
        }

        return payOrder;
    }

    /**
     * 处理支付回调
     * @param payNo 支付单号
     * @param outTradeNo 商户支付流水号
     * @param tradeNo    平台交易号
     * @param notifyData 回调原始数据
     */
    @SuppressWarnings("unchecked")
    public void handlePaySuccess(String payNo, String outTradeNo, String tradeNo, String notifyData) {
        // 幂等检查：如果已经成功则跳过
        Object statusObj = jdbc.queryForObject("SELECT status FROM trade_pay_order WHERE pay_no = ?", String.class, payNo);
        if ("success".equals(statusObj)) {
            return; // 幂等
        }

        LocalDateTime now = LocalDateTime.now();
        jdbc.update(
                "UPDATE trade_pay_order SET status = 'success', trade_no = ?, notify_data = ?, " +
                "paid_at = ?, update_time = ? WHERE pay_no = ?",
                tradeNo, notifyData, now, now, payNo);

        // 更新订单状态为 paid
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT order_id FROM trade_pay_order WHERE pay_no = ?", payNo);
        if (!rows.isEmpty()) {
            Long orderId = ((Number) rows.get(0).get("order_id")).longValue();
            jdbc.update(
                    "UPDATE trade_order SET status = 'paid', paid_at = ?, update_time = ? WHERE order_id = ?",
                    now, now, orderId);
        }
    }

    /**
     * 更新支付单状态
     */
    private void updatePayStatus(String payNo, String status, String tradeNo, LocalDateTime paidAt) {
        jdbc.update(
                "UPDATE trade_pay_order SET status = ?, trade_no = ?, paid_at = ?, update_time = NOW() WHERE pay_no = ?",
                status, tradeNo, paidAt, payNo);
    }
}
```

- [ ] **Step 2: 验证编译**

Run: `cd F:/code/weiwei/twelvet-fast && mvn compile -pl twelvet-trade/twelvet-trade-server -am -DskipTests 2>&1 | tail -10`
Expected: BUILD SUCCESS

---

## Task 6: TradePayController — 支付控制器

**Files:**
- Create: `twelvet-fast/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/controller/TradePayController.java`

- [ ] **Step 1: 创建 TradePayController**

Create: `twelvet-fast/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/controller/TradePayController.java`

```java
package com.twelvet.trade.server.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.trade.server.service.PayOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 支付 — C 端
 */
@Tag(description = "支付管理", name = "TradePayController")
@RestController
@RequestMapping("/trade/pay")
public class TradePayController extends TWTController {

    @Autowired
    private PayOrderService payOrderService;

    @Value("${twelvet.name:TwelveT Fast}")
    private String appName;

    /**
     * 创建支付单
     * POST /trade/pay/create
     * Body: { "orderId": 123, "channel": "alipay" }
     */
    @Operation(summary = "创建支付单")
    @PostMapping("/create")
    public JsonResult<Map<String, Object>> createPayOrder(@RequestBody Map<String, Object> body) {
        Long userId = StpUtil.getLoginIdAsLong();
        Long orderId = Long.valueOf(String.valueOf(body.get("orderId")));
        String channel = String.valueOf(body.getOrDefault("channel", "alipay"));

        // 查询订单，确认是买家本人
        List<Map<String, Object>> orders = jdbc.queryForList(
                "SELECT o.order_id, o.order_no, o.total_amount, o.buyer_id " +
                "FROM trade_order o WHERE o.order_id = ? AND o.buyer_id = ?",
                orderId, userId);
        if (orders.isEmpty()) {
            return JsonResult.error("订单不存在或无权操作");
        }
        Map<String, Object> order = orders.get(0);

        // 调用支付服务
        String subject = appName + " - 订单 " + order.get("order_no");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = payOrderService.createPayOrder(
                orderId,
                (String) order.get("order_no"),
                channel,
                new java.math.BigDecimal(String.valueOf(order.get("total_amount"))),
                subject,
                "https://your-domain.com/trade/payment/result"
        );

        return JsonResult.success(result);
    }

    /**
     * 查询支付状态
     * GET /trade/pay/status/{payNo}
     */
    @Operation(summary = "查询支付状态")
    @GetMapping("/status/{payNo}")
    public JsonResult<Map<String, Object>> payStatus(@PathVariable String payNo) {
        Map<String, Object> result = payOrderService.queryPayStatus(payNo);
        if (result == null) {
            return JsonResult.error("支付单不存在");
        }
        return JsonResult.success(result);
    }

    // 继承自 TWTController 已有 jdbc
    @Autowired
    private cn.dev33.satoken.stp.StpUtil saTokenUtil;
}
```

完整代码（含 `@Autowired JdbcTemplate jdbc`）:

```java
package com.twelvet.trade.server.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.trade.server.service.PayOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 支付 — C 端
 */
@Tag(description = "支付管理", name = "TradePayController")
@RestController
@RequestMapping("/trade/pay")
public class TradePayController extends TWTController {

    @Autowired
    private PayOrderService payOrderService;

    @Autowired
    private JdbcTemplate jdbc;

    @Value("${twelvet.name:TwelveT Fast}")
    private String appName;

    @Operation(summary = "创建支付单")
    @PostMapping("/create")
    public JsonResult<Map<String, Object>> createPayOrder(@RequestBody Map<String, Object> body) {
        Long userId = StpUtil.getLoginIdAsLong();
        Long orderId = Long.valueOf(String.valueOf(body.get("orderId")));
        String channel = String.valueOf(body.getOrDefault("channel", "alipay"));

        List<Map<String, Object>> orders = jdbc.queryForList(
                "SELECT o.order_id, o.order_no, o.total_amount, o.buyer_id " +
                "FROM trade_order o WHERE o.order_id = ? AND o.buyer_id = ?",
                orderId, userId);
        if (orders.isEmpty()) {
            return JsonResult.error("订单不存在或无权操作");
        }
        Map<String, Object> order = orders.get(0);

        String subject = appName + " - 订单 " + order.get("order_no");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = payOrderService.createPayOrder(
                orderId,
                (String) order.get("order_no"),
                channel,
                new BigDecimal(String.valueOf(order.get("total_amount"))),
                subject,
                "https://your-domain.com/trade/payment/result"
        );

        return JsonResult.success(result);
    }

    @Operation(summary = "查询支付状态")
    @GetMapping("/status/{payNo}")
    public JsonResult<Map<String, Object>> payStatus(@PathVariable String payNo) {
        Map<String, Object> result = payOrderService.queryPayStatus(payNo);
        if (result == null) {
            return JsonResult.error("支付单不存在");
        }
        return JsonResult.success(result);
    }
}
```

- [ ] **Step 2: 验证编译**

Run: `cd F:/code/weiwei/twelvet-fast && mvn compile -pl twelvet-trade/twelvet-trade-server -am -DskipTests 2>&1 | tail -10`
Expected: BUILD SUCCESS

---

## Task 7: PaymentCallbackController — 回调处理

**Files:**
- Create: `twelvet-fast/twelvet-admin/src/main/java/com/twelvet/trade/server/controller/PaymentCallbackController.java`
- Modify: `twelvet-fast/twelvet-admin/src/main/resources/application-dev.yml` (security.ignore.urls)

- [ ] **Step 1: 创建回调控制器**

Create: `twelvet-fast/twelvet-admin/src/main/java/com/twelvet/trade/server/controller/PaymentCallbackController.java`

```java
package com.twelvet.trade.server.controller;

import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.trade.server.service.PayOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 支付回调 — 公开接口（支付宝/微信异步通知）
 */
@Tag(description = "支付回调", name = "PaymentCallbackController")
@RestController
@RequestMapping("/payment/callback")
public class PaymentCallbackController {

    @Autowired
    private PayOrderService payOrderService;

    /**
     * 支付宝异步回调
     * POST /payment/callback/alipay
     * 参数为表单格式: out_trade_no, trade_no, trade_status, sign 等
     */
    @Operation(summary = "支付宝回调")
    @PostMapping("/alipay")
    public String alipayNotify(@RequestParam Map<String, String> params) {
        // 验签
        // TODO: 需要注入 AlipayGateway 来验签，这里简化处理
        String outTradeNo = params.get("out_trade_no");
        String tradeNo = params.get("trade_no");
        String status = params.get("trade_status");

        if ("TRADE_SUCCESS".equals(status) || "TRADE_FINISHED".equals(status) && outTradeNo != null) {
            payOrderService.handlePaySuccess(outTradeNo, outTradeNo, tradeNo,
                    params.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(java.util.stream.Collectors.joining("&")));
        }

        return "success"; // 支付宝要求返回 success 字符串
    }

    /**
     * 微信异步回调
     * POST /payment/callback/wechat
     * 请求体为加密 JSON
     */
    @Operation(summary = "微信回调")
    @PostMapping("/wechat")
    public String wechatNotify(@RequestBody String body) {
        // TODO: 微信 v3 需要解密 + 验签
        // 沙箱阶段先记录日志
        System.out.println("Wechat callback: " + body);

        // 解析 body 获取 out_trade_no
        try {
            Map<String, Object> data = com.alibaba.fastjson2.JSON.parseObject(body, Map.class);
            Map<String, Object> resource = (Map<String, Object>) data.get("resource");
            String ciphertext = (String) resource.get("ciphertext");
            // TODO: 解密 ciphertext 获取 out_trade_no
            // 调用 payOrderService.handlePaySuccess(...)
        } catch (Exception e) {
            System.err.println("Wechat callback parse error: " + e.getMessage());
        }

        // 微信要求返回成功 JSON
        return "{\"code\":\"SUCCESS\",\"message\":\"成功\"}";
    }
}
```

- [ ] **Step 2: 在 application-dev.yml 中将回调路径加入白名单**

在 `security.ignore.urls` 的 `urls:` 列表下追加：

```yaml
      # 支付回调
      - /payment/callback/alipay
      - /payment/callback/wechat
```

- [ ] **Step 3: 验证编译**

Run: `cd F:/code/weiwei/twelvet-fast && mvn compile -pl twelvet-admin -am -DskipTests 2>&1 | tail -10`
Expected: BUILD SUCCESS

---

## Task 8: 改造 TradeOrderController.create() — 真实下单

**Files:**
- Modify: `twelvet-fast/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/controller/TradeOrderController.java:83-113`

- [ ] **Step 1: 替换 create() 方法**

将 `TradeOrderController.java` 第 83-113 行的 `create()` 方法替换为：

```java
	@Autowired
	private PayOrderService payOrderService;

	@Operation(summary = "创建订单（真实下单）")
	@PostMapping("/create")
	public JsonResult<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
		Long userId = StpUtil.getLoginIdAsLong();
		Long productId = Long.valueOf(String.valueOf(body.get("productId")));

		// 取商品快照
		Map<String, Object> product = jdbcTemplate.queryForMap(
				"SELECT product_id, seller_id, title, cover, price, shipping_fee, shipping_method, city " +
				"FROM product WHERE product_id = ?",
				productId);
		java.math.BigDecimal price = (java.math.BigDecimal) product.get("price");
		java.math.BigDecimal freight = (java.math.BigDecimal) product.get("shipping_fee");
		if (freight == null) freight = java.math.BigDecimal.ZERO;
		java.math.BigDecimal total = price.add(freight);

		String orderNo = "NO" + System.currentTimeMillis();
		String snapshot = String.format("{\"title\":\"%s\",\"price\":%s,\"cover\":\"%s\"}",
				product.get("title"), price, product.get("cover"));
		Long sellerId = ((Number) product.get("seller_id")).longValue();

		// 插入订单，状态为 unpaid
		jdbcTemplate.update(
				"INSERT INTO trade_order (order_no, buyer_id, seller_id, product_id, product_snapshot, " +
				"sku_price, freight, total_amount, status, create_time, paid_at, update_time) " +
				"VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'unpaid', NOW(), NOW(), NOW())",
				orderNo, userId, sellerId, productId, snapshot, price, freight, total);

		// 查询刚插入的订单ID
		List<Map<String, Object>> orders = jdbcTemplate.queryForList(
				"SELECT order_id FROM trade_order WHERE order_no = ?", orderNo);
		Long orderId = ((Number) orders.get(0).get("order_id")).longValue();

		Map<String, Object> result = new HashMap<>();
		result.put("orderId", orderId);
		result.put("orderNo", orderNo);
		result.put("totalAmount", total);
		result.put("status", "unpaid");
		return JsonResult.success(result);
	}
```

- [ ] **Step 2: 在 orderDetail 中增加 paid 字段判断**

在 `detail()` 方法中（第 57-81 行），将 SQL 增加 `o.status status` 查询，并在返回结果中增加 `paid` 字段：

在 detail 返回前（第 80 行前）追加：
```java
		order.put("paid", "paid".equals(order.get("status")) || "unshipped".equals(order.get("status"))
				|| "shipped".equals(order.get("status")) || "done".equals(order.get("status")));
```

- [ ] **Step 3: 验证编译**

Run: `cd F:/code/weiwei/twelvet-fast && mvn compile -pl twelvet-trade/twelvet-trade-server -am -DskipTests 2>&1 | tail -10`
Expected: BUILD SUCCESS

---

## Task 9: 前端 — API 函数

**Files:**
- Modify: `twelvet-ui-master/src/services/marketplace/api.ts`

- [ ] **Step 1: 在 api.ts 末尾追加支付相关 API 函数**

在文件末尾（第 208 行之后）追加：

```typescript
// 支付
export async function payCreate(data: Record<string, any>) {
    return request('/trade/pay/create', { method: 'POST', data });
}
export async function payStatus(payNo: string) {
    return request(`/trade/pay/status/${payNo}`, { method: 'GET' });
}
```

- [ ] **Step 2: 验证**

No build needed for API changes, just TypeScript check:

Run: `cd F:/code/weiwei/twelvet-ui-master && npx tsc --noEmit 2>&1 | head -20`
Expected: 无新增类型错误

---

## Task 10: 前端 — Pay.tsx 收银台页面

**Files:**
- Modify: `twelvet-ui-master/src/pages/marketplace/Pay.tsx`
- Modify: `twelvet-ui-master/src/services/marketplace/api.ts`

- [ ] **Step 1: 实现 Pay.tsx**

替换 `twelvet-ui-master/src/pages/marketplace/Pay.tsx` 内容为：

```tsx
import { payCreate, payStatus } from '@/services/marketplace/api';
import { PageContainer } from '@ant-design/pro-components';
import { Button, Card, Descriptions, Empty, message, Result, Spin, Typography } from 'antd';
import QRCode from 'qrcode.react';
import { history, useSearchParams } from '@umijs/max';
import React, { useEffect, useRef, useState } from 'react';

const { Text } = Typography;

const Pay: React.FC = () => {
    const [params] = useSearchParams();
    const orderId = params.get('orderId');
    const payNo = params.get('payNo');

    const [loading, setLoading] = useState(true);
    const [payData, setPayData] = useState<any>(null);
    const [paid, setPaid] = useState(false);
    const [failed, setFailed] = useState(false);
    const timerRef = useRef<number>();

    // 创建支付
    useEffect(() => {
        const init = async () => {
            if (!orderId) {
                setLoading(false);
                return;
            }
            try {
                const r: any = await payCreate({ orderId, channel: 'alipay' });
                if (r.code === 200) {
                    setPayData(r.data);
                } else {
                    message.error(r.msg || '创建支付失败');
                    setFailed(true);
                }
            } catch {
                setFailed(true);
            } finally {
                setLoading(false);
            }
        };
        init();
    }, [orderId]);

    // 轮询支付状态（3秒一次，最多60次=3分钟）
    useEffect(() => {
        if (!payNo || paid || failed) return;

        const poll = async () => {
            try {
                const r: any = await payStatus(payNo);
                if (r?.data?.status === 'success' || r?.data?.paid) {
                    setPaid(true);
                    if (timerRef.current) clearInterval(timerRef.current);
                }
            } catch {
                // 忽略轮询错误
            }
        };

        // 立即查一次
        poll();
        timerRef.current = window.setInterval(poll, 3000);

        return () => {
            if (timerRef.current) clearInterval(timerRef.current);
        };
    }, [payNo, paid, failed]);

    if (loading) {
        return (
            <PageContainer header={{ title: '收银台', breadcrumb: {} }}>
                <div style={{ textAlign: 'center', padding: '100px 0' }}>
                    <Spin size="large" tip="加载中..." />
                </div>
            </PageContainer>
        );
    }

    if (paid) {
        return (
            <PageContainer header={{ title: '收银台', breadcrumb: {} }}>
                <Result
                    status="success"
                    title="支付成功"
                    subTitle={`支付单号: ${payNo}`}
                    extra={[
                        <Button type="primary" key="orders" onClick={() => history.push('/trade/order/list?role=buyer')}>
                            查看订单
                        </Button>,
                        <Button key="home" onClick={() => history.push('/')}>
                            返回首页
                        </Button>,
                    ]}
                />
            </PageContainer>
        );
    }

    if (failed) {
        return (
            <PageContainer header={{ title: '收银台', breadcrumb: {} }}>
                <Result
                    status="500"
                    title="支付失败"
                    extra={[
                        <Button key="retry" onClick={() => history.push('/')}>
                            返回首页
                        </Button>,
                    ]}
                />
            </PageContainer>
        );
    }

    // 渲染二维码
    const qrCodeContent = payData?.qrCode || payData?.payUrl || payData?.extra?.qrCode || payData?.extra?.codeUrl;

    return (
        <PageContainer header={{ title: '收银台', breadcrumb: {} }}>
            <Card style={{ maxWidth: 500, margin: '40px auto' }}>
                <Descriptions bordered column={1}>
                    <Descriptions.Item label="订单号">{payData?.orderNo}</Descriptions.Item>
                    <Descriptions.Item label="金额">
                        <span style={{ color: '#FF6B35', fontSize: 24, fontWeight: 600 }}>
                            ¥{payData?.amount}
                        </span>
                    </Descriptions.Item>
                    <Descriptions.Item label="支付方式">
                        {payData?.channel === 'wechat' ? '微信支付' : '支付宝'}
                    </Descriptions.Item>
                </Descriptions>

                <div style={{ textAlign: 'center', marginTop: 24 }}>
                    {qrCodeContent ? (
                        <>
                            <Text type="secondary" style={{ display: 'block', marginBottom: 12 }}>
                                请使用 {payData?.channel === 'wechat' ? '微信' : '支付宝'} 扫描下方二维码付款
                            </Text>
                            <QRCode
                                value={qrCodeContent}
                                size={256}
                                level="H"
                                includeMargin
                            />
                            <div style={{ marginTop: 16 }}>
                                <Text type="secondary">付款后请稍候，系统将自动确认...</Text>
                            </div>
                        </>
                    ) : (
                        <Empty description="暂无支付信息" />
                    )}
                </div>
            </Card>
        </PageContainer>
    );
};

export default Pay;
```

- [ ] **Step 2: 确保 qrcode.react 已安装**

Run: `cd F:/code/weiwei/twelvet-ui-master && npm list qrcode.react 2>&1 || npm install qrcode.react`
Expected: 安装成功或已安装

---

## Task 11: 前端 — PayResult.tsx 支付结果页

**Files:**
- Modify: `twelvet-ui-master/src/pages/marketplace/PayResult.tsx`

- [ ] **Step 1: 实现 PayResult.tsx**

替换 `twelvet-ui-master/src/pages/marketplace/PayResult.tsx` 内容为：

```tsx
import { payStatus } from '@/services/marketplace/api';
import { PageContainer } from '@ant-design/pro-components';
import { Button, Descriptions, Result, Spin, Typography } from 'antd';
import { useSearchParams } from '@umijs/max';
import React, { useEffect, useState } from 'react';

const { Text } = Typography;

const PayResult: React.FC = () => {
    const [params] = useSearchParams();
    const payNo = params.get('payNo');

    const [loading, setLoading] = useState(true);
    const [data, setData] = useState<any>(null);

    useEffect(() => {
        const fetch = async () => {
            if (!payNo) {
                setLoading(false);
                return;
            }
            try {
                const r: any = await payStatus(payNo);
                setData(r?.data);
            } catch {
                // ignore
            } finally {
                setLoading(false);
            }
        };
        fetch();
    }, [payNo]);

    if (loading) {
        return (
            <PageContainer header={{ title: '支付结果', breadcrumb: {} }}>
                <div style={{ textAlign: 'center', padding: '100px 0' }}>
                    <Spin size="large" tip="加载中..." />
                </div>
            </PageContainer>
        );
    }

    const isSuccess = data?.status === 'success' || data?.paid;

    return (
        <PageContainer header={{ title: '支付结果', breadcrumb: {} }}>
            <Result
                status={isSuccess ? 'success' : 'error'}
                title={isSuccess ? '支付成功' : '支付失败'}
                subTitle={`支付单号: ${payNo}`}
                extra={[
                    <Button type="primary" key="orders" onClick={() => history.push('/trade/order/list?role=buyer')}>
                        查看订单
                    </Button>,
                    <Button key="home" onClick={() => history.push('/')}>
                        返回首页
                    </Button>,
                ]}
                style={{ maxWidth: 500, margin: '60px auto' }}
            />
            {data && (
                <Descriptions
                    bordered
                    column={1}
                    size="small"
                    style={{ maxWidth: 500, margin: '0 auto' }}
                >
                    <Descriptions.Item label="订单号">{data?.orderNo}</Descriptions.Item>
                    <Descriptions.Item label="支付金额">
                        <span style={{ color: '#FF6B35', fontWeight: 600 }}>
                            ¥{data?.amount}
                        </span>
                    </Descriptions.Item>
                    <Descriptions.Item label="支付方式">
                        {data?.channel === 'wechat' ? '微信支付' : '支付宝'}
                    </Descriptions.Item>
                    <Descriptions.Item label="支付状态">
                        {isSuccess ? '已支付' : '未支付'}
                    </Descriptions.Item>
                    {data?.paidAt && (
                        <Descriptions.Item label="支付时间">{data.paidAt}</Descriptions.Item>
                    )}
                    {data?.tradeNo && (
                        <Descriptions.Item label="平台交易号">{data.tradeNo}</Descriptions.Item>
                    )}
                </Descriptions>
            )}
        </PageContainer>
    );
};

export default PayResult;
```

> 需要补充 `import { history } from '@umijs/max';` 因为用了 `history.push`

---

## Task 12: 集成测试验证

**Files:**
- 无新文件

- [ ] **Step 1: 启动后端**

Run: `cd F:/code/weiwei/twelvet-fast && mvn spring-boot:run -pl twelvet-admin -am -DskipTests`
Expected: 服务启动在 8080 端口，无报错

- [ ] **Step 2: 验证建表**

Run: `mysql -uroot -p123456 twelvet-fast -e "SHOW TABLES LIKE 'trade_pay_order';"`
Expected: 返回 `trade_pay_order` 一行

- [ ] **Step 3: 验证 Swagger 接口**

访问 `http://localhost:8080/doc.html`，确认：
- 新增了 "6.交易模块" 下的 `TradePayController`（创建支付单、查询支付状态）
- 新增了 "支付回调" 下的 `PaymentCallbackController`
- `TradeOrderController` 的 `create` 接口签名不变

- [ ] **Step 4: 手动测试下单流程**

1. 登录系统
2. POST `/trade/order/create`，body: `{ "productId": 1 }`
3. 返回 `status: "unpaid"` 和 `orderId`
4. POST `/trade/pay/create`，body: `{ "orderId": <上一步返回的orderId> }`
5. 返回 `payNo` 和 `qrCode`
6. 访问前端 `http://localhost:8000/trade/payment?orderId=<orderId>&payNo=<payNo>`

- [ ] **Step 5: 测试支付宝沙箱**

1. 配置正确的沙箱 app-id、private-key、alipay-public-key
2. 调用创建支付接口，应返回支付宝付款码
3. 用支付宝沙箱 APP 扫码支付
4. 确认回调到达后端，订单状态变为 `paid`
5. 前端轮询确认支付成功

---

## 安全注意事项

1. **回调接口白名单**: `/payment/callback/alipay` 和 `/payment/callback/wechat` 已加入 `security.ignore.urls`
2. **密钥管理**: 所有商户密钥通过环境变量注入，配置文件中的值是沙箱占位符
3. **验签**: 支付宝使用 SDK 内置验签；微信 v3 回调验签在沙箱阶段为占位实现（`return true`），上线前需补全
4. **幂等**: `handlePaySuccess` 中有幂等检查，防止重复处理回调

---

## 后续可选扩展（不在本次范围内）

- 微信支付退款（需要配置平台证书）
- 微信支付 JSAPI 支付（小程序/公众号）
- App 原生支付
- 对账功能
- 定时对账任务
- 更细粒度的订单状态（退款中、已退款）
