package com.twelvet.product.server.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.product.server.service.IRecommendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 推荐效果管理端接口：CTR 统计（曝光流水 × 详情页浏览 关联计算）
 * <p>
 * 权限复用二手市场管理端 marketplace:admin:recommend（不新增菜单与权限项）。
 * </p>
 */
@Tag(description = "推荐效果管理端接口", name = "AdminRecommendController")
@RestController
@RequestMapping("/admin/recommend")
public class AdminRecommendController extends TWTController {

	private final IRecommendService recommendService;

	public AdminRecommendController(IRecommendService recommendService) {
		this.recommendService = recommendService;
	}

	@SaCheckPermission("marketplace:admin:recommend")
	@Operation(summary = "推荐效果统计(每日曝光/点击/UV 按 A/B 组拆分 + 点击Top商品)")
	@GetMapping("/stats")
	public JsonResult<Map<String, Object>> stats(@RequestParam(required = false, defaultValue = "7") Integer days,
			@RequestParam(required = false) Integer bucket) {
		return JsonResult.success(recommendService.recommendStats(days, bucket));
	}

}
