package com.twelvet.exchange.server.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.stp.StpUtil;
import com.twelvet.exchange.api.domain.ExchangeItem;
import com.twelvet.exchange.server.service.IExchangeItemService;
import com.twelvet.exchange.server.service.IExchangeMatchService;
import com.twelvet.exchange.server.service.IExchangeReviewService;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 交换广场 - 浏览视角
 *
 * @author twelvet
 */
@Tag(description = "交换广场（浏览）", name = "ExchangeSquareController")
@RestController
@RequestMapping("/exchange")
public class ExchangeSquareController extends TWTController {

	private final IExchangeItemService itemService;

	private final IExchangeMatchService matchService;

	private final IExchangeReviewService reviewService;

	public ExchangeSquareController(IExchangeItemService itemService, IExchangeMatchService matchService,
			IExchangeReviewService reviewService) {
		this.itemService = itemService;
		this.matchService = matchService;
		this.reviewService = reviewService;
	}

	@SaIgnore
	@Operation(summary = "广场列表（类目/成色/关键词全文/交付方式筛选，hot_score 排序）")
	@GetMapping("/square")
	public TableDataInfo square(@RequestParam(defaultValue = "1") Long pageNum,
			@RequestParam(defaultValue = "20") Long pageSize, @RequestParam(required = false) String catL1,
			@RequestParam(required = false) String conditionLevel, @RequestParam(required = false) String deliveryMode,
			@RequestParam(required = false) String keyword) {
		return itemService.square(pageNum, pageSize, catL1, conditionLevel, deliveryMode, keyword);
	}

	@SaIgnore
	@Operation(summary = "物品详情（浏览数+1，含主人换物主页摘要）")
	@GetMapping("/item/{id}")
	public JsonResult<Map<String, Object>> detail(@PathVariable Long id) {
		Long userId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
		Map<String, Object> detail = itemService.detail(id, userId);
		if (detail == null) {
			return JsonResult.error("物品不存在");
		}
		return JsonResult.success(detail);
	}

	@SaIgnore
	@Operation(summary = "类目树（只读复用商品类目字典 product_category_dict）")
	@GetMapping("/category/tree")
	public JsonResult<List<Map<String, Object>>> categoryTree() {
		return JsonResult.success(itemService.categoryTree());
	}

	@SaCheckLogin
	@Operation(summary = "为你推荐（用户 want_desc 向量召回供给物品，向量通道不可用时降级空数组）")
	@GetMapping("/square/recommend")
	public JsonResult<List<ExchangeItem>> recommend(@RequestParam(defaultValue = "8") int limit) {
		Long userId = StpUtil.getLoginIdAsLong();
		return JsonResult.success(matchService.recommendForMe(userId, limit));
	}

	@SaCheckLogin
	@Operation(summary = "用户换物信用分（实时计算：评价均分 + 30天履约率 - 违约扣减，无数据默认 70）")
	@GetMapping("/credit/{userId}")
	public JsonResult<Map<String, Object>> credit(@PathVariable Long userId) {
		return JsonResult.success(reviewService.creditProfile(userId));
	}

}
