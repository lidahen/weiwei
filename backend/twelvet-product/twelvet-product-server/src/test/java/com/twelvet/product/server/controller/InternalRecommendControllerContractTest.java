package com.twelvet.product.server.controller;

import com.twelvet.framework.core.config.JacksonConfiguration;
import com.twelvet.framework.core.internal.InternalApiToken;
import com.twelvet.framework.core.tenant.TenantSupport;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.framework.utils.SpringContextHolder;
import com.twelvet.product.api.domain.Product;
import com.twelvet.product.server.mapper.UserMapper;
import com.twelvet.product.server.service.IRecommendService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 内部推荐端点 JSON 契约测试（跨侧断裂回归：Python 曾按 data.rows 解析， 而 TableDataInfo 真实序列化是 data.records ——
 * 本测试用真实 Jackson 链路钉死形状）。
 * <p>
 * 为什么不用纯 Mockito：上轮跨侧缺陷的根因正是两侧测试各自 mock、没有任何一处断言 真实 JSON 形状。本测试走 Spring MockMvc
 * standalone + 真实消息转换器，mock 只替身 IRecommendService 的返回对象，序列化完全真实。
 * </p>
 * <p>
 * Jackson 全局定制的对齐方式：standalone MockMvc 不加载 AutoConfiguration，因此
 * {@link JacksonConfiguration}（Long→ToStringSerializer、JavaTimeModule 等）不会生效。 这里直接实例化真实的
 * {@code JacksonConfiguration#customizer()} 并应用到 {@link Jackson2ObjectMapperBuilder} 再构造
 * ObjectMapper——复用生产定制器本身而非手工 重写其内容，生产配置变更时测试自动保持对齐，而不是巧合一致。
 * </p>
 */
class InternalRecommendControllerContractTest {

	@BeforeAll
	static void stubSpringContextForI18n() {
		// 与 InternalRecommendControllerTest 同款脚手架：JsonResult.success() 走 I18nUtils
		// （静态初始化时经 SpringContextHolder 取 MessageSource），纯单测没有 Spring 上下文，
		// 必须给 holder 塞一个最小 MessageSource，否则静态初始化抛 NPE。
		// StaticMessageSource 未注册条目时返回 defaultMessage，与真实容器"取不到即回退默认
		// 文案"的行为一致。
		DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
		beanFactory.registerSingleton("messageSource", new StaticMessageSource());
		new SpringContextHolder().postProcessBeanFactory(beanFactory);
	}

	private final IRecommendService recommendService = mock(IRecommendService.class);

	private final InternalApiToken token = mock(InternalApiToken.class);

	private final TenantSupport tenantSupport = mock(TenantSupport.class);

	private final UserMapper userMapper = mock(UserMapper.class);

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		InternalRecommendController controller = new InternalRecommendController(recommendService, token, tenantSupport,
				userMapper);
		// 与生产同源的 ObjectMapper：复用 JacksonConfiguration 的真实全局定制
		// （Long.class → ToStringSerializer，见 JacksonConfiguration.java:36），否则
		// productId 会序列化成数字 1 而非字符串 "1"，与生产 JSON 形状不符。
		Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json();
		new JacksonConfiguration().customizer().customize(builder);
		ObjectMapper objectMapper = builder.build();
		mockMvc = MockMvcBuilders.standaloneSetup(controller)
			.setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
			.build();
	}

	@Test
	@DisplayName("真实序列化：data.records 数组 + productId 为字符串（Long→ToStringSerializer）")
	void jsonContract() throws Exception {
		when(token.matches(Mockito.any())).thenReturn(true);
		when(tenantSupport.enabled()).thenReturn(false);
		Product p = new Product();
		p.setProductId(1L);
		p.setTitle("九成新沙发");
		p.setPrice(new java.math.BigDecimal("320.00"));
		p.setReason("看过同款");
		when(recommendService.homeRecommend(Mockito.eq(7L), Mockito.isNull(), Mockito.eq(1), Mockito.eq(8)))
			.thenReturn(TableDataInfo.page(List.of(p), 1));

		mockMvc.perform(get("/internal/recommend/for-user").header("X-Internal-Token", "t")
			.param("userId", "7")
			.accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(200))
			.andExpect(jsonPath("$.data.records").isArray())
			.andExpect(jsonPath("$.data.records[0].productId").value("1"))
			.andExpect(jsonPath("$.data.records[0].reason").value("看过同款"));
	}

}
