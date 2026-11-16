package com.twelvet.product.server.controller;

import com.twelvet.framework.core.internal.InternalApiToken;
import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.framework.core.tenant.TenantSupport;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.framework.utils.SpringContextHolder;
import com.twelvet.product.api.domain.Product;
import com.twelvet.product.server.mapper.UserMapper;
import com.twelvet.product.server.service.IRecommendService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 内部推荐接口测试。锁定六条不变量：
 * <ol>
 * <li>令牌不通过 → 403，且不触达推荐服务</li>
 * <li>userId 缺失/非数字 → 400</li>
 * <li>租户开关开启：homeRecommend 执行期间 TenantContext 为买家租户，调用后清理（异常路径同样清）</li>
 * <li>租户开关关闭：不解析租户、不设上下文（零侵入回滚）</li>
 * <li>pageSize 上限 20</li>
 * <li>homeRecommend 以 current=1 调用（内部端点固定第一页）</li>
 * </ol>
 */
class InternalRecommendControllerTest {

	@BeforeAll
	static void stubSpringContextForI18n() {
		// JsonResult.success() 内部走 I18nUtils（静态初始化时经 SpringContextHolder 取
		// MessageSource）。
		// 纯 Mockito 单测没有 Spring 上下文，必须给 holder 塞一个最小 MessageSource，
		// 否则静态初始化抛 NPE 并把该类标记为永久失败（NoClassDefFoundError）。
		// 仅测试脚手架：生产代码与全部断言不变；StaticMessageSource 未注册条目时返回
		// defaultMessage，与真实容器"取不到即回退默认文案"的行为一致。
		DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
		beanFactory.registerSingleton("messageSource", new StaticMessageSource());
		new SpringContextHolder().postProcessBeanFactory(beanFactory);
	}

	private final IRecommendService recommendService = mock(IRecommendService.class);

	private final InternalApiToken token = mock(InternalApiToken.class);

	private final TenantSupport tenantSupport = mock(TenantSupport.class);

	private final UserMapper userMapper = mock(UserMapper.class);

	private final InternalRecommendController controller = new InternalRecommendController(recommendService, token,
			tenantSupport, userMapper);

	@BeforeEach
	void setUp() {
		TenantContext.clear();
	}

	@AfterEach
	void clearContext() {
		// 防御：任何一个用例失败都不应把上下文残留给下一个用例
		TenantContext.clear();
	}

	private TableDataInfo<Product> fakePage() {
		Product p = new Product();
		p.setProductId(1L);
		p.setReason("看过同款");
		return TableDataInfo.page(List.of(p), 1);
	}

	@Test
	@DisplayName("令牌不通过 → 403 且不触达推荐服务")
	void tokenRejected() {
		when(token.matches(any())).thenReturn(false);
		ResponseEntity<?> resp = controller.forUser("bad-token", "7", null, 8);
		assertEquals(403, resp.getStatusCode().value());
		verifyNoInteractions(recommendService);
	}

	@Test
	@DisplayName("userId 缺失 → 400")
	void userIdRequired() {
		when(token.matches(any())).thenReturn(true);
		ResponseEntity<?> resp = controller.forUser("t", null, null, 8);
		assertEquals(400, resp.getStatusCode().value());
	}

	@Test
	@DisplayName("userId 非数字 → 400")
	void userIdMustBeNumeric() {
		when(token.matches(any())).thenReturn(true);
		ResponseEntity<?> resp = controller.forUser("t", "abc", null, 8);
		assertEquals(400, resp.getStatusCode().value());
	}

	@Test
	@DisplayName("租户开启：homeRecommend 执行期间上下文为买家租户，调用后已清理")
	void tenantResolvedAndCleared() {
		when(token.matches(any())).thenReturn(true);
		when(tenantSupport.enabled()).thenReturn(true);
		when(userMapper.selectUserTenantId(7L)).thenReturn(42L);
		when(recommendService.homeRecommend(eq(7L), isNull(), eq(1), eq(8))).thenAnswer(inv -> {
			// 执行期间断言：上下文必须是买家租户（服务端解析，不是调用方传参）
			assertEquals(42L, TenantContext.get(), "homeRecommend 执行期间 TenantContext 应为买家租户");
			return fakePage();
		});
		ResponseEntity<?> resp = controller.forUser("t", "7", null, 8);
		assertEquals(200, resp.getStatusCode().value());
		assertNull(TenantContext.get(), "调用结束后上下文必须已清理");
	}

	@Test
	@DisplayName("租户开启但用户查不到 → 400 且上下文已清理（失败路径 finally）")
	void tenantMissingFailsClosedAndClears() {
		when(token.matches(any())).thenReturn(true);
		when(tenantSupport.enabled()).thenReturn(true);
		when(userMapper.selectUserTenantId(7L)).thenReturn(null);
		ResponseEntity<?> resp = controller.forUser("t", "7", null, 8);
		assertEquals(400, resp.getStatusCode().value());
		assertNull(TenantContext.get(), "失败路径上下文同样必须清理");
		verify(recommendService, never()).homeRecommend(anyLong(), any(), anyInt(), anyInt());
	}

	@Test
	@DisplayName("租户关闭：不解析租户、不设上下文，行为与公开路径一致")
	void tenantDisabledSkipsResolution() {
		when(token.matches(any())).thenReturn(true);
		when(tenantSupport.enabled()).thenReturn(false);
		when(recommendService.homeRecommend(eq(7L), isNull(), eq(1), eq(8))).thenReturn(fakePage());
		ResponseEntity<?> resp = controller.forUser("t", "7", null, 8);
		assertEquals(200, resp.getStatusCode().value());
		assertNull(TenantContext.get());
		verify(userMapper, never()).selectUserTenantId(anyLong());
	}

	@Test
	@DisplayName("pageSize 超上限截为 20")
	void pageSizeClamped() {
		when(token.matches(any())).thenReturn(true);
		when(tenantSupport.enabled()).thenReturn(false);
		when(recommendService.homeRecommend(anyLong(), any(), anyInt(), anyInt())).thenReturn(fakePage());
		controller.forUser("t", "7", null, 99);
		ArgumentCaptor<Integer> size = ArgumentCaptor.forClass(Integer.class);
		verify(recommendService).homeRecommend(eq(7L), isNull(), eq(1), size.capture());
		assertEquals(20, size.getValue());
	}

}
