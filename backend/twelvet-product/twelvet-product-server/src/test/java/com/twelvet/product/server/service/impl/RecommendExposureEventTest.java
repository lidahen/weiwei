package com.twelvet.product.server.service.impl;

import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.im.server.service.IImService;
import com.twelvet.product.api.domain.FunnelEvent;
import com.twelvet.product.api.domain.Product;
import com.twelvet.product.api.service.IFunnelEventService;
import com.twelvet.product.server.client.RecommendVectorClient;
import com.twelvet.product.server.mapper.FavoriteMapper;
import com.twelvet.product.server.mapper.ProductMapper;
import com.twelvet.product.server.mapper.RecommendImpressionMapper;
import com.twelvet.product.server.mapper.SearchKeywordLogMapper;
import com.twelvet.product.server.mapper.UserInterestStatMapper;
import com.twelvet.product.server.mapper.UserMapper;
import com.twelvet.product.server.mapper.UserRelationMapper;
import com.twelvet.product.server.service.ICategoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 推荐流曝光事件：每张卡片一条 product_exposure，租户/类目/场景/角色必须齐全， 否则看板无法按社区切片（spec D7/D8）。
 */
class RecommendExposureEventTest {

	private final RecommendImpressionMapper impressionMapper = mock(RecommendImpressionMapper.class);

	private final IFunnelEventService funnelEventService = mock(IFunnelEventService.class);

	private final RecommendServiceImpl service = new RecommendServiceImpl(mock(ProductMapper.class),
			mock(UserInterestStatMapper.class), mock(UserRelationMapper.class), mock(ICategoryService.class),
			mock(RecommendVectorClient.class), impressionMapper, mock(SearchKeywordLogMapper.class),
			mock(FavoriteMapper.class), mock(IImService.class), mock(UserMapper.class), funnelEventService);

	@AfterEach
	void clear() {
		TenantContext.clear();
	}

	private Product product(Long productId, String catL1) {
		Product p = new Product();
		p.setProductId(productId);
		p.setCatL1(catL1);
		return p;
	}

	@Test
	@DisplayName("推荐流每张卡片写一条 product_exposure，带租户/类目/场景")
	@SuppressWarnings("unchecked")
	void logImpressions_writesExposureEvents() {
		TenantContext.set(42L);
		Product p1 = product(1L, "数码");
		Product p2 = product(2L, "家具");

		service.logImpressions(42L, 7L, List.of(p1, p2), 0, "home_recommend", 0);

		ArgumentCaptor<List<FunnelEvent>> captor = ArgumentCaptor.forClass(List.class);
		verify(funnelEventService).recordBatch(captor.capture());
		List<FunnelEvent> events = captor.getValue();
		assertEquals(2, events.size());
		assertEquals(FunnelEvent.TYPE_PRODUCT_EXPOSURE, events.get(0).getEventType());
		assertEquals(42L, events.get(0).getTenantId());
		assertEquals("数码", events.get(0).getCategory());
		assertEquals("home_recommend", events.get(0).getScene());
		assertEquals("buyer", events.get(0).getRole());
		assertEquals(2L, events.get(1).getProductId());
		assertEquals("家具", events.get(1).getCategory());
	}

	@Test
	@DisplayName("匿名访客曝光角色为 visitor")
	@SuppressWarnings("unchecked")
	void logImpressions_anonymousIsVisitor() {
		TenantContext.set(42L);

		service.logImpressions(42L, null, List.of(product(1L, "数码")), 0, "home_recommend", 0);

		ArgumentCaptor<List<FunnelEvent>> captor = ArgumentCaptor.forClass(List.class);
		verify(funnelEventService).recordBatch(captor.capture());
		assertEquals("visitor", captor.getValue().get(0).getRole());
	}

}