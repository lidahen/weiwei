package com.twelvet.trade.server.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.framework.core.internal.InternalApiToken;
import com.twelvet.trade.server.service.ITradeOrderService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 内部订单接口（服务间调用：Python ai-service → Java）
 * <p>
 * 存在的理由：AI 导购 Agent 需要为用户下单，但 Python 侧没有用户登录态，无法调用 {@code POST /trade/order/create}（该接口要求
 * {@code StpUtil.getLoginIdAsLong()}）。 在此接口出现之前，Agent 直接 INSERT trade_order，导致 tenant_id
 * 落到 DEFAULT 1（跨租户混入）、 freight 恒为 0、且没有商品快照。现在改为调用本接口，订单写入完全交给 Java 侧。
 * </p>
 * <p>
 * 安全设计（三道）：
 * <ol>
 * <li>接口置于 {@code /internal/**} 前缀下，便于在反向代理（Caddy）统一 deny，不暴露到公网</li>
 * <li>必须携带 {@code X-Internal-Token}，由 {@link InternalApiToken}
 * 做常量时间比对；<b>未配置令牌一律拒绝</b></li>
 * <li>租户不取调用方传参，由服务端从 {@code sys_user.tenant_id} 解析</li>
 * </ol>
 * 本接口 {@code @SaIgnore}（绕过 Sa-Token 登录校验），因此上面第 2 条是它唯一的鉴权手段，不可省略。
 * </p>
 *
 * @author twelvet
 */
@Hidden
@Tag(description = "内部订单接口(服务间调用)", name = "InternalOrderController")
@RestController
@RequestMapping("/internal/trade/order")
public class InternalOrderController {

	private static final Logger log = LoggerFactory.getLogger(InternalOrderController.class);

	private final ITradeOrderService tradeOrderService;

	private final InternalApiToken internalApiToken;

	public InternalOrderController(ITradeOrderService tradeOrderService, InternalApiToken internalApiToken) {
		this.tradeOrderService = tradeOrderService;
		this.internalApiToken = internalApiToken;
	}

	/**
	 * 内部下单（代指定用户，租户由服务端解析）
	 * @param token 请求头 {@code X-Internal-Token}
	 * @param body 需包含 userId 与 productId
	 * @return 订单信息；鉴权失败 403、参数缺失或业务拒绝 400
	 */
	@Operation(summary = "内部下单（服务间调用）")
	@SaIgnore
	@PostMapping("/create")
	public ResponseEntity<JsonResult<Map<String, Object>>> create(
			@RequestHeader(value = InternalApiToken.HEADER, required = false) String token,
			@RequestBody Map<String, Object> body) {
		if (!internalApiToken.matches(token)) {
			// 失败关闭且不区分"未配置"与"令牌错误"，避免向外泄露内部接口的配置状态
			log.warn("内部下单接口鉴权失败，已拒绝");
			return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(JsonResult.<Map<String, Object>>error(HttpStatus.FORBIDDEN.value(), "内部接口鉴权失败"));
		}
		Long userId = toLong(body == null ? null : body.get("userId"));
		Long productId = toLong(body == null ? null : body.get("productId"));
		Long addressId = toLong(body == null ? null : body.get("addressId"));
		if (userId == null || productId == null) {
			return ResponseEntity.badRequest()
				.body(JsonResult.<Map<String, Object>>error(HttpStatus.BAD_REQUEST.value(),
						"参数缺失：userId 与 productId 均为必填且必须为数字"));
		}
		try {
			// addressId 可选：express 商品必须由调用方解析用户地址后传入（缺地址会被校验链拒绝）
			Map<String, Object> result = tradeOrderService.createOrderForUser(userId, productId, addressId);
			log.info("内部下单成功: buyerId={}, productId={}, addressId={}, orderNo={}", userId, productId, addressId,
					result.get("orderNo"));
			return ResponseEntity.ok(JsonResult.success(result));
		}
		catch (TWTException e) {
			// 业务性拒绝（商品不存在/跨租户商品/用户无租户）属预期结果，返回 400 而不是 500，
			// 避免污染错误监控（真实故障才应该是 5xx）
			log.warn("内部下单被拒绝: buyerId={}, productId={}, reason={}", userId, productId, e.getMessage());
			return ResponseEntity.badRequest()
				.body(JsonResult.<Map<String, Object>>error(HttpStatus.BAD_REQUEST.value(), e.getMessage()));
		}
	}

	private Long toLong(Object value) {
		if (value == null) {
			return null;
		}
		try {
			return Long.valueOf(String.valueOf(value).trim());
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

}
