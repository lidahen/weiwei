package com.twelvet.trade.server.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.trade.server.service.IReconService;
import com.twelvet.trade.server.service.ISellerAccountService;
import com.twelvet.trade.server.service.ISettlementService;
import com.twelvet.trade.server.service.IWithdrawService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 运营结算相关接口测试
 * <p>
 * 锁定的不变量：每个运营端点的权限串逐字固定；操作人取自登录态；服务异常（如凭证缺失）向上传播不吞。
 * </p>
 */
class AdminSettlementControllersTest {

	private static final Long OPERATOR_ID = 99L;

	private static final Long TENANT_ID = 42L;

	private final ISellerAccountService accountService = mock(ISellerAccountService.class);

	private final ISettlementService settlementService = mock(ISettlementService.class);

	private final IWithdrawService withdrawService = mock(IWithdrawService.class);

	private final IReconService reconService = mock(IReconService.class);

	private final AdminKycController kycController = new AdminKycController(accountService);

	private final AdminSettlementController settlementController = new AdminSettlementController(settlementService);

	private final AdminWithdrawController withdrawController = new AdminWithdrawController(withdrawService);

	private final AdminReconController reconController = new AdminReconController(reconService, accountService);

	@BeforeEach
	void setTenant() {
		TenantContext.set(TENANT_ID);
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private static void assertPermission(Class<?> type, String expected, String method, Class<?>... params)
			throws NoSuchMethodException {
		Method target = type.getMethod(method, params);
		SaCheckPermission permission = target.getAnnotation(SaCheckPermission.class);
		assertNotNull(permission, method + " 缺少 @SaCheckPermission");
		assertEquals(List.of(expected), List.of(permission.value()), method + " 权限串不匹配");
	}

	@Test
	@DisplayName("十个运营端点权限串逐字固定")
	void allEndpointsAnnotated() throws NoSuchMethodException {
		assertPermission(AdminKycController.class, "marketplace:admin:kyc", "page", String.class, Long.class);
		assertPermission(AdminKycController.class, "marketplace:admin:kyc", "review", Map.class);
		assertPermission(AdminSettlementController.class, "marketplace:admin:settlement", "page", String.class,
				Long.class);
		assertPermission(AdminSettlementController.class, "marketplace:admin:settlement", "detail", Long.class,
				Long.class);
		assertPermission(AdminWithdrawController.class, "marketplace:admin:withdraw", "page", String.class, Long.class);
		assertPermission(AdminWithdrawController.class, "marketplace:admin:withdraw", "review", Map.class);
		assertPermission(AdminWithdrawController.class, "marketplace:admin:withdraw", "pay", Map.class);
		assertPermission(AdminReconController.class, "marketplace:admin:recon", "page", String.class, String.class,
				Long.class);
		assertPermission(AdminReconController.class, "marketplace:admin:recon", "resolve", Map.class);
		assertPermission(AdminReconController.class, "marketplace:admin:recon", "unfreeze", Map.class);
	}

	@Test
	@DisplayName("实名审核：操作人取登录态，租户取上下文")
	void kycReview_usesLoginId() {
		try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class);
				MockedStatic<JsonResult> ignored = mockStatic(JsonResult.class)) {
			stp.when(StpUtil::getLoginIdAsLong).thenReturn(OPERATOR_ID);
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("accountId", 11L);
			body.put("result", "verified");
			body.put("note", "资料清晰");

			kycController.review(body);

			verify(accountService).reviewKyc(11L, "verified", "资料清晰", OPERATOR_ID, TENANT_ID);
		}
	}

	@Test
	@DisplayName("打款凭证缺失：服务异常向上传播，controller 不吞")
	void withdrawPay_blankVoucherPropagates() {
		try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
			stp.when(StpUtil::getLoginIdAsLong).thenReturn(OPERATOR_ID);
			doThrow(new TWTException("请上传打款凭证")).when(withdrawService).markPaid(33L, null, OPERATOR_ID, TENANT_ID);
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("withdrawId", 33L);
			body.put("voucherUrl", " ");

			TWTException error = assertThrows(TWTException.class, () -> withdrawController.pay(body));

			assertEquals("请上传打款凭证", error.getMessage());
			verify(withdrawService).markPaid(33L, null, OPERATOR_ID, TENANT_ID);
		}
	}

	@Test
	@DisplayName("解冻：按卖家 + 上下文租户 + 操作人 + 原因调用账户服务")
	void unfreeze_usesSellerIdAndTenantAndReason() {
		try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class);
				MockedStatic<JsonResult> json = mockStatic(JsonResult.class)) {
			stp.when(StpUtil::getLoginIdAsLong).thenReturn(OPERATOR_ID);
			json.when(() -> JsonResult.success(any(Object.class))).thenAnswer(invocation -> {
				JsonResult<Object> result = new JsonResult<>();
				result.setData(invocation.getArgument(0));
				return result;
			});
			when(accountService.unfreezeBySeller(7L, TENANT_ID, OPERATOR_ID, "差异已处理")).thenReturn(true);
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("sellerId", 7L);
			body.put("reason", "差异已处理");

			JsonResult<Boolean> result = reconController.unfreeze(body);

			assertTrue(result.getData());
			verify(accountService).unfreezeBySeller(7L, TENANT_ID, OPERATOR_ID, "差异已处理");
		}
	}

	@Test
	@DisplayName("运营写操作缺少租户上下文：直接拒绝，不落全租户语义")
	void writeWithoutTenant_rejected() {
		TenantContext.clear();
		try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
			stp.when(StpUtil::getLoginIdAsLong).thenReturn(OPERATOR_ID);
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("accountId", 11L);
			body.put("result", "verified");

			TWTException error = assertThrows(TWTException.class, () -> kycController.review(body));

			assertEquals("缺少租户上下文", error.getMessage());
			verify(accountService, org.mockito.Mockito.never()).reviewKyc(any(), any(), any(), any(), any());
		}
	}

}