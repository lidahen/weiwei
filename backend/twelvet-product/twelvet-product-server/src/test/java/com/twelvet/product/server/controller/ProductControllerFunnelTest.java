package com.twelvet.product.server.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.product.api.domain.FunnelEvent;
import com.twelvet.product.api.domain.Product;
import com.twelvet.product.api.service.IFunnelEventService;
import com.twelvet.product.server.service.IProductService;
import com.twelvet.product.server.service.IRecommendService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商品详情漏斗事件：product_detail_view 必须带登录用户与类目。
 */
class ProductControllerFunnelTest {

	private final IProductService productService = mock(IProductService.class);

	private final IRecommendService recommendService = mock(IRecommendService.class);

	private final IFunnelEventService funnelEventService = mock(IFunnelEventService.class);

	private final ProductController controller = new ProductController(productService, recommendService,
			funnelEventService);

	@Test
	@DisplayName("商品详情写 product_detail_view，类目取商品 catL1")
	void detail_recordsViewEvent() {
		try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class);
				MockedStatic<JsonResult> ignored = mockStatic(JsonResult.class)) {
			stp.when(StpUtil::isLogin).thenReturn(true);
			stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
			Product product = new Product();
			product.setProductId(1L);
			product.setCatL1("数码");
			when(productService.selectProductById(1L)).thenReturn(product);

			controller.detail(1L);

			ArgumentCaptor<FunnelEvent> captor = ArgumentCaptor.forClass(FunnelEvent.class);
			verify(funnelEventService).record(captor.capture());
			assertEquals(FunnelEvent.TYPE_PRODUCT_DETAIL_VIEW, captor.getValue().getEventType());
			assertEquals("数码", captor.getValue().getCategory());
			assertEquals(7L, captor.getValue().getUserId());
			assertEquals(1L, captor.getValue().getProductId());
		}
	}

	@Test
	@DisplayName("匿名浏览详情角色为 visitor")
	void detail_anonymousIsVisitor() {
		try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class);
				MockedStatic<JsonResult> ignored = mockStatic(JsonResult.class)) {
			stp.when(StpUtil::isLogin).thenReturn(false);
			Product product = new Product();
			product.setProductId(1L);
			product.setCatL1("数码");
			when(productService.selectProductById(1L)).thenReturn(product);

			controller.detail(1L);

			ArgumentCaptor<FunnelEvent> captor = ArgumentCaptor.forClass(FunnelEvent.class);
			verify(funnelEventService).record(captor.capture());
			assertEquals("visitor", captor.getValue().getRole());
		}
	}

}