package com.twelvet.trade.server.controller;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.trade.server.domain.TradeWithdraw;
import com.twelvet.trade.server.service.ISellerAccountService;
import com.twelvet.trade.server.service.IWithdrawService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 卖家结算接口测试
 * <p>
 * 锁定的不变量：卖家身份取登录态、租户取上下文（缺失即失败，不落默认 1）；四个端点分别委托账户/提现服务；未登录异常向上传播。
 * </p>
 */
class SellerSettlementControllerTest {

	private static final Long SELLER_ID = 7L;

	private static final Long TENANT_ID = 42L;

	private final ISellerAccountService accountService = mock(ISellerAccountService.class);

	private final IWithdrawService withdrawService = mock(IWithdrawService.class);

	private final SellerSettlementController controller = new SellerSettlementController(accountService,
			withdrawService);

	@BeforeEach
	void setTenant() {
		TenantContext.set(TENANT_ID);
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private static void stubLogin(MockedStatic<StpUtil> stp) {
		stp.when(StpUtil::getLoginIdAsLong).thenReturn(SELLER_ID);
	}

	private static void captureSuccess(MockedStatic<JsonResult> json) {
		json.when(() -> JsonResult.success(any(Object.class))).thenAnswer(invocation -> {
			JsonResult<Object> result = new JsonResult<>();
			result.setData(invocation.getArgument(0));
			return result;
		});
	}

	@Test
	@DisplayName("账户查询：登录卖家 + 上下文租户")
	void account_delegates() {
		try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class);
				MockedStatic<JsonResult> json = mockStatic(JsonResult.class)) {
			stubLogin(stp);
			captureSuccess(json);
			Map<String, Object> data = Map.of("sellerId", SELLER_ID);
			when(accountService.myAccount(SELLER_ID, TENANT_ID)).thenReturn(data);

			JsonResult<Map<String, Object>> result = controller.account();

			assertEquals(data, result.getData());
		}
	}

	@Test
	@DisplayName("提交实名：登录卖家身份与租户透传，资料逐字段透传")
	void applyKyc_delegates() {
		try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class);
				MockedStatic<JsonResult> json = mockStatic(JsonResult.class)) {
			stubLogin(stp);
			captureSuccess(json);
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("accountName", "张三");
			body.put("idCardNo", "110101199001011234");
			body.put("bankCardNo", "6222020200112233445");
			body.put("bankName", "工商银行");

			controller.applyKyc(body);

			verify(accountService).submitKyc(SELLER_ID, TENANT_ID, "张三", "110101199001011234", "6222020200112233445",
					"工商银行");
		}
	}

	@Test
	@DisplayName("提现申请：返回扣后余额")
	void applyWithdraw_returnsBalance() {
		try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class);
				MockedStatic<JsonResult> json = mockStatic(JsonResult.class)) {
			stubLogin(stp);
			captureSuccess(json);
			when(withdrawService.apply(SELLER_ID, TENANT_ID, new BigDecimal("40"))).thenReturn(new BigDecimal("60.00"));

			JsonResult<BigDecimal> result = controller.applyWithdraw(Map.of("amount", "40"));

			assertEquals(new BigDecimal("60.00"), result.getData());
			verify(withdrawService).apply(SELLER_ID, TENANT_ID, new BigDecimal("40"));
		}
	}

	@Test
	@DisplayName("提现记录：登录卖家 + 上下文租户")
	void withdrawPage_delegates() {
		try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class);
				MockedStatic<JsonResult> json = mockStatic(JsonResult.class)) {
			stubLogin(stp);
			captureSuccess(json);
			TableDataInfo<TradeWithdraw> page = TableDataInfo.page(List.of(new TradeWithdraw()), 1);
			when(withdrawService.myPage(SELLER_ID, TENANT_ID)).thenReturn(page);

			JsonResult<TableDataInfo<TradeWithdraw>> result = controller.withdrawPage();

			assertEquals(page, result.getData());
		}
	}

	@Test
	@DisplayName("未登录：NotLoginException 向上传播")
	void notLoggedIn_propagates() {
		try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
			stp.when(StpUtil::getLoginIdAsLong).thenThrow(mock(NotLoginException.class));

			assertThrows(NotLoginException.class, () -> controller.account());
		}
	}

}