package com.twelvet.payment.server.service.impl;

import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.framework.core.tenant.TenantSupport;
import com.twelvet.payment.api.dto.PayCallbackDTO;
import com.twelvet.payment.api.dto.PayRequestDTO;
import com.twelvet.payment.api.event.PaySuccessEvent;
import com.twelvet.payment.api.service.IPaymentGateway;
import com.twelvet.payment.server.mapper.PayOrderMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 支付单服务安全加固测试（2026-10-20 P0 修复）
 * <p>
 * 锁定的不变量：
 * <ol>
 * <li>支付成功事件必须可靠发布：回调路径 rows=0（幂等重放/监听器曾失败）也重发事件（监听器幂等）， 轮询路径标记 paid 后同样发布——否则订单永久卡
 * unpaid，随后被超时关单转售（资损）</li>
 * <li>createPayOrder 必须记录发起人 payUserId（mock-pay/归属校验的权威来源）</li>
 * <li>mockPay 仅支付单发起人可调用（防任意用户强付他人订单）</li>
 * <li>mock 模式不触网关</li>
 * </ol>
 * </p>
 */
class PayOrderServiceImplTest {

	private static final String PAY_NO = "PAY20261020000001";

	private static final Long ORDER_ID = 21L;

	private static final Long PAY_USER_ID = 7L;

	private final PayOrderMapper payOrderMapper = mock(PayOrderMapper.class);

	private final TenantSupport tenantSupport = mock(TenantSupport.class);

	private final List<PaySuccessEvent> events = new ArrayList<>();

	private final ApplicationEventPublisher eventPublisher = event -> events.add((PaySuccessEvent) event);

	private PayOrderServiceImpl newService(boolean mockEnabled) {
		PayOrderServiceImpl service = new PayOrderServiceImpl(payOrderMapper, Map.of(), tenantSupport, eventPublisher);
		try {
			Field f = PayOrderServiceImpl.class.getDeclaredField("mockEnabled");
			f.setAccessible(true);
			f.set(service, mockEnabled);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return service;
	}

	private PayRequestDTO payRequest() {
		PayRequestDTO request = new PayRequestDTO();
		request.setOrderId(ORDER_ID);
		request.setOrderNo("NO123");
		request.setChannel("alipay");
		request.setAmount(new BigDecimal("13.00"));
		request.setSubject("测试订单");
		request.setPayUserId(PAY_USER_ID);
		return request;
	}

	private Map<String, Object> payRow(String status) {
		Map<String, Object> row = new HashMap<>();
		row.put("order_id", ORDER_ID);
		row.put("pay_user_id", PAY_USER_ID);
		row.put("pay_channel", "alipay");
		row.put("pay_amount", new BigDecimal("13.00"));
		row.put("status", status);
		row.put("biz_type", "trade");
		return row;
	}

	@Test
	@DisplayName("回调首处理：标记 paid 并发布事件（金额转分）")
	void handlePaySuccess_firstTime_publishesEvent() {
		when(payOrderMapper.markPaidByOutTradeNo(eq(PAY_NO), any(LocalDateTime.class))).thenReturn(1);
		when(payOrderMapper.selectOrderInfoByOutTradeNo(PAY_NO)).thenReturn(payRow("paid"));

		newService(false).handlePaySuccess(callback());

		assertEquals(1, events.size(), "支付成功必须发布事件");
		PaySuccessEvent event = events.get(0);
		assertEquals(PAY_NO, event.getOutTradeNo());
		assertEquals(ORDER_ID, event.getOrderId());
		assertEquals("trade", event.getBizType(), "事件必须携带 biz_type（监听器按其门卫）");
		assertEquals(1300L, event.getAmountCents(), "金额必须转分");
	}

	@Test
	@DisplayName("refund：非 paid 状态（unpaid/refunded）→ 拒绝且不翻状态（markRefunded 幂等前置）")
	void refund_notPaid_returnsFalse() {
		when(payOrderMapper.selectOrderInfoByOutTradeNo(PAY_NO)).thenReturn(payRow("unpaid"));
		assertTrue(!newService(true).refund(PAY_NO, new BigDecimal("13.00"), "测试"), "unpaid 单不得退款");
		verify(payOrderMapper, never()).markRefunded(anyString());

		when(payOrderMapper.selectOrderInfoByOutTradeNo(PAY_NO)).thenReturn(payRow("refunded"));
		assertTrue(!newService(true).refund(PAY_NO, new BigDecimal("13.00"), "测试"), "已退款单不得重复退款");
		verify(payOrderMapper, never()).markRefunded(anyString());
	}

	@Test
	@DisplayName("refund：paid 单 mock 模式 → markRefunded 恰好一次")
	void refund_paid_markRefundedOnce() {
		when(payOrderMapper.selectOrderInfoByOutTradeNo(PAY_NO)).thenReturn(payRow("paid"));

		boolean ok = newService(true).refund(PAY_NO, new BigDecimal("13.00"), "测试");

		assertTrue(ok);
		verify(payOrderMapper).markRefunded(PAY_NO);
	}

	@Test
	@DisplayName("回调重放/监听器曾失败（rows=0 但单已 paid）：重发事件自愈（监听器幂等）")
	void handlePaySuccess_alreadyPaid_republishesForSelfHeal() {
		// 历史缺陷：rows=0 直接 return，事件永不重发 → 订单永久卡 unpaid 被超时关单转售
		when(payOrderMapper.markPaidByOutTradeNo(eq(PAY_NO), any(LocalDateTime.class))).thenReturn(0);
		when(payOrderMapper.selectOrderInfoByOutTradeNo(PAY_NO)).thenReturn(payRow("paid"));

		newService(false).handlePaySuccess(callback());

		assertEquals(1, events.size(), "已付单的重复回调必须重发事件（自愈搁浅的订单状态迁移）");
	}

	@Test
	@DisplayName("回调对应单不存在：不发布事件")
	void handlePaySuccess_unknownPayNo_noEvent() {
		when(payOrderMapper.markPaidByOutTradeNo(eq(PAY_NO), any(LocalDateTime.class))).thenReturn(0);
		when(payOrderMapper.selectOrderInfoByOutTradeNo(PAY_NO)).thenReturn(null);

		newService(false).handlePaySuccess(callback());

		assertTrue(events.isEmpty());
	}

	@Test
	@DisplayName("轮询路径查询到网关已支付：标记 paid 并发布事件（历史缺陷：只标记不发布）")
	void queryPayStatus_pollingDiscoversSuccess_publishesEvent() {
		IPaymentGateway gateway = mock(IPaymentGateway.class);
		when(gateway.queryPayStatus(PAY_NO)).thenReturn("SUCCESS");
		PayOrderServiceImpl service = new PayOrderServiceImpl(payOrderMapper, Map.of("alipay", gateway), tenantSupport,
				eventPublisher);
		setMock(service, false);
		when(payOrderMapper.selectByOutTradeNo(PAY_NO, null)).thenReturn(payRow("unpaid"));
		when(payOrderMapper.markPaidByOutTradeNo(eq(PAY_NO), any(LocalDateTime.class))).thenReturn(1);
		when(payOrderMapper.selectOrderInfoByOutTradeNo(PAY_NO)).thenReturn(payRow("paid"));

		Map<String, Object> result = service.queryPayStatus(PAY_NO);

		assertEquals(Boolean.TRUE, result.get("paid"));
		assertEquals(1, events.size(), "轮询补单也必须发布事件，否则订单状态永远不迁移");
	}

	@Test
	@DisplayName("mock 模式：状态查询不触网关、不发布事件")
	void queryPayStatus_mockMode_noGatewayNoEvent() {
		PayOrderServiceImpl service = newService(true);
		when(payOrderMapper.selectByOutTradeNo(PAY_NO, null)).thenReturn(payRow("unpaid"));

		Map<String, Object> result = service.queryPayStatus(PAY_NO);

		assertEquals(Boolean.FALSE, result.get("paid"));
		verify(payOrderMapper, never()).markPaidByOutTradeNo(anyString(), any());
		assertTrue(events.isEmpty());
	}

	@Test
	@DisplayName("createPayOrder：必须记录发起人 payUserId（归属校验权威来源）")
	void createPayOrder_recordsPayUserId() {
		// writeTid 未 stub 时 Mockito 对 Long 默认给 0 而非 null（与 trade 侧测试记录的陷阱相同），
		// 显式对齐真实实现：返回 TenantContext.get()，这里清空上下文即 null
		when(tenantSupport.writeTid()).thenAnswer(invocation -> com.twelvet.framework.core.tenant.TenantContext.get());

		PayOrderServiceImpl service = newService(true);

		service.createPayOrder(payRequest());

		ArgumentCaptor<Long> payUserCaptor = ArgumentCaptor.forClass(Long.class);
		verify(payOrderMapper).insertPayOrder(eq(ORDER_ID), payUserCaptor.capture(), anyString(), eq("alipay"),
				eq(new BigDecimal("13.00")), eq("trade"), isNull());
		assertEquals(PAY_USER_ID, payUserCaptor.getValue(), "支付单必须落发起人ID，mock-pay 归属校验依赖它");
	}

	@Test
	@DisplayName("createPayOrder：显式 bizType=runner → 落库 runner（互助任务路径）")
	void createPayOrder_explicitRunnerBizType() {
		when(tenantSupport.writeTid()).thenAnswer(invocation -> com.twelvet.framework.core.tenant.TenantContext.get());

		PayRequestDTO request = payRequest();
		request.setBizType("runner");
		newService(true).createPayOrder(request);

		verify(payOrderMapper).insertPayOrder(eq(ORDER_ID), eq(PAY_USER_ID), anyString(), eq("alipay"),
				eq(new BigDecimal("13.00")), eq("runner"), isNull());
	}

	@Test
	@DisplayName("mockPay：发起人本人 → 成功履约")
	void mockPay_owner_succeeds() {
		when(payOrderMapper.selectOrderInfoByOutTradeNo(PAY_NO)).thenReturn(payRow("unpaid"));

		newService(true).mockPay(PAY_NO, PAY_USER_ID);

		verify(payOrderMapper).markPaidByOutTradeNo(eq(PAY_NO), any(LocalDateTime.class));
		assertEquals(1, events.size(), "mock 支付同样必须走事件链路");
	}

	@Test
	@DisplayName("mockPay：非发起人 → 拒绝且不产生任何状态变更（防任意用户强付他人订单）")
	void mockPay_notOwner_rejected() {
		when(payOrderMapper.selectOrderInfoByOutTradeNo(PAY_NO)).thenReturn(payRow("unpaid"));

		assertThrows(TWTException.class, () -> newService(true).mockPay(PAY_NO, 999L));

		verify(payOrderMapper, never()).markPaidByOutTradeNo(anyString(), any());
		assertTrue(events.isEmpty(), "越权 mock 支付绝不能触发订单履约");
	}

	@Test
	@DisplayName("mockPay：存量单（pay_user_id NULL）放行——dev 数据兼容，归属校验只约束新单")
	void mockPay_legacyRowWithoutOwner_allowed() {
		Map<String, Object> legacy = payRow("unpaid");
		legacy.put("pay_user_id", null);
		when(payOrderMapper.selectOrderInfoByOutTradeNo(PAY_NO)).thenReturn(legacy);

		newService(true).mockPay(PAY_NO, 999L);

		verify(payOrderMapper).markPaidByOutTradeNo(eq(PAY_NO), any(LocalDateTime.class));
	}

	@Test
	@DisplayName("mockPay：单不存在 → 拒绝")
	void mockPay_unknownPayNo_rejected() {
		when(payOrderMapper.selectOrderInfoByOutTradeNo(PAY_NO)).thenReturn(null);

		assertThrows(TWTException.class, () -> newService(true).mockPay(PAY_NO, PAY_USER_ID));
	}

	private PayCallbackDTO callback() {
		PayCallbackDTO callback = new PayCallbackDTO();
		callback.setOutTradeNo(PAY_NO);
		callback.setTradeNo("TRADE123");
		callback.setNotifyData("{}");
		return callback;
	}

	private void setMock(PayOrderServiceImpl service, boolean value) {
		try {
			Field f = PayOrderServiceImpl.class.getDeclaredField("mockEnabled");
			f.setAccessible(true);
			f.set(service, value);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

}
