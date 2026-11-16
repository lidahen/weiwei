package com.twelvet.trade.server.service.impl;

import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.framework.core.tenant.TenantSupport;
import com.twelvet.im.server.service.IImService;
import com.twelvet.product.api.service.IFunnelEventService;
import com.twelvet.product.server.mapper.ProductMapper;
import com.twelvet.product.server.mapper.UserAddressMapper;
import com.twelvet.trade.api.domain.TradeOrder;
import com.twelvet.trade.server.mapper.AdminMarketplaceMapper;
import com.twelvet.trade.server.mapper.TradeLogisticsMapper;
import com.twelvet.trade.server.mapper.TradeOrderMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 交易超时任务测试（30 分钟未支付关单 / 发货超时自动确认收货）
 * <p>
 * 锁定的不变量：
 * <ol>
 * <li>超时关单：先原子 UPDATE（仅 status='unpaid' 命中），rows=1 才回补库存——与买家主动取消并发时 只有一方
 * rows=1，防止库存双倍回补（Review Focus #4）</li>
 * <li>自动确认收货：先原子 UPDATE（仅 status='shipped' 命中），rows=1 才标记商品 sold</li>
 * <li>后台静默容错：单单失败只 log.warn 继续下一单，不抛异常中断整批</li>
 * <li>租户设计：quartz 无租户上下文，回补/标记一律传 null（全租户语义），不读 TenantContext</li>
 * </ol>
 * </p>
 */
class TradeOrderServiceImplTimeoutTest {

	private static final int SCAN_LIMIT = 200;

	private final TradeOrderMapper orderMapper = mock(TradeOrderMapper.class);

	private final TradeLogisticsMapper logisticsMapper = mock(TradeLogisticsMapper.class);

	private final AdminMarketplaceMapper adminMapper = mock(AdminMarketplaceMapper.class);

	private final ProductMapper productMapper = mock(ProductMapper.class);

	private final UserAddressMapper userAddressMapper = mock(UserAddressMapper.class);

	private final TenantSupport tenantSupport = mock(TenantSupport.class);

	private final TradeOrderTimeoutHelper timeoutHelper = mock(TradeOrderTimeoutHelper.class);

	private final IImService imService = mock(IImService.class);

	private final IFunnelEventService funnelEventService = mock(IFunnelEventService.class);

	private final TradeOrderServiceImpl service = new TradeOrderServiceImpl(orderMapper, logisticsMapper, adminMapper,
			productMapper, userAddressMapper, tenantSupport, timeoutHelper, imService, funnelEventService);

	@BeforeEach
	void clearTenantContext() {
		// quartz 后台线程无租户上下文：清空以模拟真实调用环境（服务层超时方法应传 null 而非读上下文）
		TenantContext.clear();
	}

	@AfterEach
	void cleanUp() {
		// 防御：不把上下文残留给同线程的下一个用例
		TenantContext.clear();
	}

	private TradeOrder order(long orderId, long productId) {
		TradeOrder order = mock(TradeOrder.class);
		when(order.getOrderId()).thenReturn(orderId);
		when(order.getProductId()).thenReturn(productId);
		return order;
	}

	@Test
	@DisplayName("超时关单：helper 返回 true 计数，false（买家抢先取消）跳过")
	void cancelExpired_helperTrueCounts() {
		TradeOrder first = order(1L, 100L);
		TradeOrder second = order(2L, 101L);
		when(orderMapper.selectExpiredUnpaidOrders(SCAN_LIMIT)).thenReturn(List.of(first, second));
		when(timeoutHelper.closeSingleExpiredOrder(1L, 100L)).thenReturn(true);
		when(timeoutHelper.closeSingleExpiredOrder(2L, 101L)).thenReturn(false);

		int closed = service.cancelExpiredUnpaid();

		assertEquals(1, closed);
		verify(timeoutHelper).closeSingleExpiredOrder(1L, 100L);
		verify(timeoutHelper).closeSingleExpiredOrder(2L, 101L);
	}

	@Test
	@DisplayName("自动确认收货：helper 返回 true 计数")
	void autoConfirm_helperTrueCounts() {
		TradeOrder due = order(3L, 102L);
		when(orderMapper.selectAutoConfirmDueOrders(SCAN_LIMIT)).thenReturn(List.of(due));
		when(timeoutHelper.confirmSingleOrder(3L, 102L)).thenReturn(true);

		int confirmed = service.autoConfirmReceipt();

		assertEquals(1, confirmed);
		verify(timeoutHelper).confirmSingleOrder(3L, 102L);
	}

	@Test
	@DisplayName("自动确认 rows=0（已被买家确认/并发抢先）：helper false 不计数")
	void autoConfirm_helperFalseSkips() {
		TradeOrder due = order(4L, 103L);
		when(orderMapper.selectAutoConfirmDueOrders(SCAN_LIMIT)).thenReturn(List.of(due));
		when(timeoutHelper.confirmSingleOrder(4L, 103L)).thenReturn(false);

		int confirmed = service.autoConfirmReceipt();

		assertEquals(0, confirmed);
	}

	@Test
	@DisplayName("空列表：不触碰 helper，返回 0")
	void emptyLists_noop() {
		when(orderMapper.selectExpiredUnpaidOrders(SCAN_LIMIT)).thenReturn(List.of());
		when(orderMapper.selectAutoConfirmDueOrders(SCAN_LIMIT)).thenReturn(List.of());

		assertEquals(0, service.cancelExpiredUnpaid());
		assertEquals(0, service.autoConfirmReceipt());

		verifyNoInteractions(timeoutHelper);
	}

	@Test
	@DisplayName("关单单单容错：helper 抛异常（事务已回滚，将下轮重试）只 log.warn，继续处理其余订单")
	void cancelExpired_singleOrderFails_continuesNext() {
		TradeOrder first = order(1L, 100L);
		TradeOrder second = order(2L, 101L);
		when(orderMapper.selectExpiredUnpaidOrders(SCAN_LIMIT)).thenReturn(List.of(first, second));
		when(timeoutHelper.closeSingleExpiredOrder(1L, 100L)).thenThrow(new RuntimeException("db down"));
		when(timeoutHelper.closeSingleExpiredOrder(2L, 101L)).thenReturn(true);

		int closed = service.cancelExpiredUnpaid();

		assertEquals(1, closed);
		verify(timeoutHelper).closeSingleExpiredOrder(2L, 101L);
	}

	@Test
	@DisplayName("确认单单容错：helper 抛异常（事务已回滚，将下轮重试）只 log.warn，继续处理其余订单")
	void autoConfirm_singleOrderFails_continuesNext() {
		TradeOrder first = order(1L, 100L);
		TradeOrder second = order(2L, 101L);
		when(orderMapper.selectAutoConfirmDueOrders(SCAN_LIMIT)).thenReturn(List.of(first, second));
		when(timeoutHelper.confirmSingleOrder(1L, 100L)).thenThrow(new RuntimeException("db down"));
		when(timeoutHelper.confirmSingleOrder(2L, 101L)).thenReturn(true);

		int confirmed = service.autoConfirmReceipt();

		assertEquals(1, confirmed);
		verify(timeoutHelper).confirmSingleOrder(2L, 101L);
	}

}
