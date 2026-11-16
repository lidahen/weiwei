package com.twelvet.payment.server.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.payment.api.service.IPayOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/**
 * 模拟支付（dev 专用）
 * <p>
 * 沙箱密钥为占位符时（billing.pay.mock=true），支付单不经过网关， 由本接口驱动履约：标记支付单已付 + 发布
 * PaySuccessEvent（互助/商品订单自行监听）。 生产环境 billing.pay.mock=false → 接口抛异常，不可用。
 * </p>
 *
 * @author twelvet
 */
@Tag(description = "模拟支付（dev）", name = "PaymentMockController")
@RestController
@RequestMapping("/payment")
public class PaymentMockController extends TWTController {

	private final IPayOrderService payOrderService;

	public PaymentMockController(IPayOrderService payOrderService) {
		this.payOrderService = payOrderService;
	}

	@Operation(summary = "模拟支付成功（仅 mock 模式可用，仅支付单发起人）")
	@SaCheckLogin
	@PostMapping("/mock-pay/{payNo}")
	public JsonResult<String> mockPay(@PathVariable String payNo) {
		// 归属校验在服务层（pay_user_id 比对）；payNo 可枚举，无校验时任意登录用户可强付他人订单
		payOrderService.mockPay(payNo, StpUtil.getLoginIdAsLong());
		return JsonResult.success("OK");
	}

	@Operation(summary = "查询当前是否开启模拟支付")
	@SaCheckLogin
	@GetMapping("/mock-enabled")
	public JsonResult<Boolean> mockEnabled() {
		return JsonResult.success(payOrderService.isMockEnabled());
	}

}
