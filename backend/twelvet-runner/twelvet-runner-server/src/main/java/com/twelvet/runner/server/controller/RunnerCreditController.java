package com.twelvet.runner.server.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.runner.server.service.IRunnerReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 接单人信用画像
 * <p>
 * 设计：docs/跑腿信用与评价设计.md 信用分实时聚合评价 + 订单数据，无评价用户给默认分 80
 * </p>
 *
 * @author twelvet
 */
@Tag(description = "接单人信用", name = "RunnerCreditController")
@RestController
@RequestMapping("/runner/credit")
public class RunnerCreditController extends TWTController {

	private final IRunnerReviewService reviewService;

	public RunnerCreditController(IRunnerReviewService reviewService) {
		this.reviewService = reviewService;
	}

	@SaCheckLogin
	@Operation(summary = "信用画像（完成单数/平均分/近30天完成率/信用分）")
	@GetMapping("/{userId}")
	public JsonResult<Map<String, Object>> credit(@PathVariable Long userId) {
		return JsonResult.success(reviewService.creditProfile(userId));
	}

}
