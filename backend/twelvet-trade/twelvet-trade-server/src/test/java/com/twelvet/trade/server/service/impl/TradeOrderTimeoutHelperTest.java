package com.twelvet.trade.server.service.impl;

import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.product.api.domain.FunnelEvent;
import com.twelvet.product.api.service.IFunnelEventService;
import com.twelvet.product.server.mapper.ProductMapper;
import com.twelvet.trade.api.domain.TradeOrder;
import com.twelvet.trade.server.mapper.TradeOrderMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 超时任务单笔处理器测试（{@link TradeOrderTimeoutHelper}）
 * <p>
 * 锁定的不变量：
 * <ol>
 * <li>关单+回补库存必须同事务原子完成：helper 是独立 Spring bean（@Transactional 经代理生效）， 回补失败时关单一并回滚，下轮 5
 * 分钟重扫自动重试——修复前两步在循环里裸调，回补失败被吞后 商品永久 locked（不可见不可售，仅手工 SQL 可修复）</li>
 * <li>rows=0（并发被买家取消/抢先）：不回补库存，防双倍回补（沿用 Review Focus #4）</li>
 * <li>rows=0 时 helper 抛 TWTException 交由调用方区分「正常跳过」与「真失败」：关单回滚与
 * 状态流转在事务语义下无法区分，统一以异常表达，扫描循环捕获后继续下一单</li>
 * <li>自动确认同构：确认+markSold 同事务；markSold rows=0 属多库存正常态，不算失败</li>
 * <li>quartz 无租户上下文：mapper 调用一律传 null（全租户语义）</li>
 * </ol>
 * </p>
 */
class TradeOrderTimeoutHelperTest {

	private final TradeOrderMapper orderMapper = mock(TradeOrderMapper.class);

	private final ProductMapper productMapper = mock(ProductMapper.class);

	private final IFunnelEventService funnelEventService = mock(IFunnelEventService.class);

	private final TradeOrderTimeoutHelper helper = new TradeOrderTimeoutHelper(orderMapper, productMapper,
			funnelEventService);

	@BeforeEach
	void clearTenantContext() {
		TenantContext.clear();
	}

	@AfterEach
	void cleanUp() {
		TenantContext.clear();
	}

	@Test
	@DisplayName("关单成功：回补库存（同事务语义，rows=1 才回补）")
	void closeSingle_success_restoresStock() {
		when(orderMapper.closeOrder(1L)).thenReturn(1);

		boolean closed = helper.closeSingleExpiredOrder(1L, 100L);

		assertEquals(true, closed);
		verify(productMapper).restoreStock(100L, null);
	}

	@Test
	@DisplayName("关单 rows=0（买家抢先取消）：不回补，返回 false（正常跳过，非失败）")
	void closeSingle_rowsZero_returnsFalseWithoutRestore() {
		when(orderMapper.closeOrder(1L)).thenReturn(0);

		boolean closed = helper.closeSingleExpiredOrder(1L, 100L);

		assertEquals(false, closed);
		verify(productMapper, never()).restoreStock(anyLong(), any());
	}

	@Test
	@DisplayName("回补抛异常：向调用方传播（事务回滚关单，下轮重扫重试）——绝不吞掉")
	void closeSingle_restoreThrows_propagates() {
		when(orderMapper.closeOrder(1L)).thenReturn(1);
		when(productMapper.restoreStock(100L, null)).thenThrow(new RuntimeException("lock wait timeout"));

		assertThrows(RuntimeException.class, () -> helper.closeSingleExpiredOrder(1L, 100L));
	}

	@Test
	@DisplayName("自动确认成功：标记商品 sold（rows=1 才标记）")
	void confirmSingle_success_marksSold() {
		when(orderMapper.autoConfirmOrder(3L)).thenReturn(1);
		TradeOrder order = new TradeOrder();
		order.setOrderId(3L);
		order.setTenantId(42L);
		order.setBuyerId(7L);
		order.setProductId(102L);
		when(orderMapper.selectOrderDetail(3L, null)).thenReturn(order);

		boolean confirmed = helper.confirmSingleOrder(3L, 102L);

		assertEquals(true, confirmed);
		verify(productMapper).markSold(102L, null);
		// quartz 无租户上下文：事件租户必须来自订单
		ArgumentCaptor<FunnelEvent> captor = ArgumentCaptor.forClass(FunnelEvent.class);
		verify(funnelEventService).record(captor.capture());
		assertEquals(FunnelEvent.TYPE_CONFIRM_RECEIPT, captor.getValue().getEventType());
		assertEquals(42L, captor.getValue().getTenantId());
		assertEquals("confirm_receipt:3", captor.getValue().getEventKey());
		assertEquals(7L, captor.getValue().getUserId());
	}

	@Test
	@DisplayName("自动确认 rows=0：不标记，返回 false")
	void confirmSingle_rowsZero_returnsFalse() {
		when(orderMapper.autoConfirmOrder(3L)).thenReturn(0);

		boolean confirmed = helper.confirmSingleOrder(3L, 102L);

		assertEquals(false, confirmed);
		verify(productMapper, never()).markSold(anyLong(), any());
		verify(funnelEventService, never()).record(any());
	}

	@Test
	@DisplayName("漏斗事件读取/写入失败不影响自动确认（埋点绝不阻断业务）")
	void confirmSingle_funnelFailure_doesNotBreak() {
		when(orderMapper.autoConfirmOrder(3L)).thenReturn(1);
		when(orderMapper.selectOrderDetail(3L, null)).thenThrow(new RuntimeException("db down"));

		assertDoesNotThrow(() -> helper.confirmSingleOrder(3L, 102L));
		verify(productMapper).markSold(102L, null);
	}

	@Test
	@DisplayName("markSold 抛异常：向调用方传播（事务回滚确认，下轮重扫重试）")
	void confirmSingle_markSoldThrows_propagates() {
		when(orderMapper.autoConfirmOrder(3L)).thenReturn(1);
		when(productMapper.markSold(102L, null)).thenThrow(new RuntimeException("connection reset"));

		assertThrows(RuntimeException.class, () -> helper.confirmSingleOrder(3L, 102L));
	}

}
