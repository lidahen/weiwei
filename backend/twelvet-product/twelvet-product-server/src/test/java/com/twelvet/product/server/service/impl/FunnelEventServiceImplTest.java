package com.twelvet.product.server.service.impl;

import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.product.api.domain.FunnelEvent;
import com.twelvet.product.api.domain.Product;
import com.twelvet.product.server.mapper.FunnelEventMapper;
import com.twelvet.product.server.mapper.ProductMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 漏斗事件写入服务。锁定的不变量：
 * <ol>
 * <li>事件租户优先取业务对象，其次取上下文——回调/Quartz 线程没有上下文</li>
 * <li>类目在写入前补齐，避免每个调用方重复查商品</li>
 * <li>写入异常绝不外抛——埋点失败不得回滚下单/支付/退款业务事务</li>
 * <li>曝光批量写只发一次 mapper 调用，防止列表页写放大</li>
 * </ol>
 */
class FunnelEventServiceImplTest {

	private final FunnelEventMapper eventMapper = mock(FunnelEventMapper.class);

	private final ProductMapper productMapper = mock(ProductMapper.class);

	private final FunnelEventServiceImpl service = new FunnelEventServiceImpl(eventMapper, productMapper);

	@AfterEach
	void clear() {
		TenantContext.clear();
	}

	private FunnelEvent event(String type) {
		FunnelEvent e = new FunnelEvent();
		e.setEventType(type);
		e.setUserId(7L);
		e.setRole("buyer");
		e.setProductId(100L);
		e.setEventKey(type + ":1");
		return e;
	}

	@Test
	@DisplayName("租户取上下文，类目由商品补齐，aiAssisted 空值落 0")
	void record_enrichesTenantAndCategory() {
		TenantContext.set(42L);
		Product p = mock(Product.class);
		when(p.getCatL1()).thenReturn("数码");
		when(productMapper.selectById(100L, 42L)).thenReturn(p);

		service.record(event(FunnelEvent.TYPE_ORDER_CREATE));

		ArgumentCaptor<FunnelEvent> captor = ArgumentCaptor.forClass(FunnelEvent.class);
		verify(eventMapper).insertIgnore(captor.capture());
		assertEquals(42L, captor.getValue().getTenantId());
		assertEquals("数码", captor.getValue().getCategory());
		assertEquals(Boolean.FALSE, captor.getValue().getAiAssisted());
	}

	@Test
	@DisplayName("事件自带租户优先于上下文（回调/Quartz 场景）")
	void record_explicitTenantWins() {
		TenantContext.set(42L);
		FunnelEvent e = event(FunnelEvent.TYPE_PAY_SUCCESS);
		e.setTenantId(7L);

		service.record(e);

		ArgumentCaptor<FunnelEvent> captor = ArgumentCaptor.forClass(FunnelEvent.class);
		verify(eventMapper).insertIgnore(captor.capture());
		assertEquals(7L, captor.getValue().getTenantId());
		verify(productMapper).selectById(100L, 7L);
	}

	@Test
	@DisplayName("写入抛异常不向外传播（埋点绝不回滚业务）")
	void record_failureSwallowed() {
		TenantContext.set(42L);
		when(eventMapper.insertIgnore(any())).thenThrow(new RuntimeException("db down"));

		assertDoesNotThrow(() -> service.record(event(FunnelEvent.TYPE_REVIEW_CREATE)));
	}

	@Test
	@DisplayName("批次写入：一次 mapper 调用，按上下文补租户")
	void recordBatch_singleInsert() {
		TenantContext.set(42L);
		List<FunnelEvent> events = List.of(event(FunnelEvent.TYPE_PRODUCT_EXPOSURE),
				event(FunnelEvent.TYPE_PRODUCT_EXPOSURE));

		service.recordBatch(events);

		ArgumentCaptor<List<FunnelEvent>> captor = ArgumentCaptor.forClass(List.class);
		verify(eventMapper).insertBatch(captor.capture());
		assertEquals(2, captor.getValue().size());
		assertEquals(42L, captor.getValue().get(0).getTenantId());
		verify(eventMapper, never()).insertIgnore(any());
	}

}