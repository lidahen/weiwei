package com.twelvet.trade.server.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.stp.StpUtil;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.trade.server.service.ITradeReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 交易评价控制器 职责: HTTP参数绑定、权限校验、调用Service、包装响应 业务逻辑全部委托给TradeReviewService
 *
 * @author twelvet
 */
@Tag(description = "交易评价", name = "TradeReviewController")
@RestController
@RequestMapping("/trade/review")
public class TradeReviewController extends TWTController {

	private final ITradeReviewService tradeReviewService;

	public TradeReviewController(ITradeReviewService tradeReviewService) {
		this.tradeReviewService = tradeReviewService;
	}

	/**
	 * 提交评价（买卖双方互评，role 由服务端按订单归属判定）
	 */
	@Operation(summary = "提交评价")
	@PostMapping
	public JsonResult<Map<String, Object>> submit(@RequestBody Map<String, Object> body) {
		Long userId = StpUtil.getLoginIdAsLong();
		Long orderId = Long.valueOf(String.valueOf(body.get("orderId")));
		Integer rating = Integer.valueOf(String.valueOf(body.get("rating")));
		String content = body.get("content") == null ? null : String.valueOf(body.get("content"));
		String images = body.get("images") == null ? null : String.valueOf(body.get("images"));
		String tags = body.get("tags") == null ? null : String.valueOf(body.get("tags"));
		Boolean anonymous = Boolean.TRUE.equals(body.get("anonymous"))
				|| "true".equalsIgnoreCase(String.valueOf(body.get("anonymous")));
		return JsonResult.success(tradeReviewService.submit(orderId, userId, rating, content, images, tags, anonymous));
	}

	/**
	 * 订单评价视图(本人角色/是否已评/双向评价)
	 */
	@Operation(summary = "订单评价视图")
	@GetMapping("/order/{orderId}")
	public JsonResult<Map<String, Object>> orderReview(@PathVariable Long orderId) {
		Long userId = StpUtil.getLoginIdAsLong();
		return JsonResult.success(tradeReviewService.orderReviewView(orderId, userId));
	}

	/**
	 * 商品页评价列表(买家评卖家，未隐藏)
	 */
	@SaIgnore
	@Operation(summary = "商品页评价列表")
	@GetMapping("/product/{productId}")
	public JsonResult<Map<String, Object>> productReviews(@PathVariable Long productId,
			@RequestParam(defaultValue = "1") int pageNum, @RequestParam(defaultValue = "10") int pageSize) {
		return JsonResult.success(tradeReviewService.listByProduct(productId, pageNum, pageSize));
	}

	/**
	 * 某用户作为卖家收到的评价列表
	 */
	@SaIgnore
	@Operation(summary = "卖家收到的评价列表")
	@GetMapping("/user/{userId}")
	public JsonResult<Map<String, Object>> userReviews(@PathVariable Long userId,
			@RequestParam(defaultValue = "1") int pageNum, @RequestParam(defaultValue = "10") int pageSize) {
		return JsonResult.success(tradeReviewService.listByUser(userId, pageNum, pageSize));
	}

}
