package com.twelvet.exchange.server.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.twelvet.exchange.api.domain.ExchangeItem;
import com.twelvet.exchange.server.service.IExchangeItemService;
import com.twelvet.exchange.server.service.IExchangeMatchService;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 换物物品 - 物品主人视角
 *
 * @author twelvet
 */
@Tag(description = "换物物品（主人视角）", name = "ExchangeItemController")
@RestController
@RequestMapping("/exchange/item")
public class ExchangeItemController extends TWTController {

	private final IExchangeItemService itemService;

	private final IExchangeMatchService matchService;

	public ExchangeItemController(IExchangeItemService itemService, IExchangeMatchService matchService) {
		this.itemService = itemService;
		this.matchService = matchService;
	}

	@SaCheckLogin
	@Operation(summary = "发布物品（进 AI 初审 auditing）")
	@PostMapping("/publish")
	public JsonResult<Map<String, Object>> publish(@RequestBody ExchangeItem item) {
		Long userId = StpUtil.getLoginIdAsLong();
		Long itemId = itemService.publish(item, userId);

		Map<String, Object> resp = new HashMap<>();
		resp.put("itemId", itemId);
		resp.put("itemNo", item.getItemNo());
		resp.put("status", item.getStatus());
		return JsonResult.success(resp);
	}

	@SaCheckLogin
	@Operation(summary = "我的换物物品（分状态 tab）")
	@GetMapping("/my-items")
	public JsonResult<List<ExchangeItem>> myItems(@RequestParam(required = false) String status) {
		Long userId = StpUtil.getLoginIdAsLong();
		return JsonResult.success(itemService.myItems(userId, status));
	}

	@SaCheckLogin
	@Operation(summary = "下架 / 重新上架（onswap↔offshelf）")
	@PostMapping("/{id}/offshelf")
	public JsonResult<Map<String, Object>> offshelf(@PathVariable Long id) {
		Long userId = StpUtil.getLoginIdAsLong();
		String status = itemService.toggleOffshelf(id, userId);
		Map<String, Object> resp = new HashMap<>();
		resp.put("itemId", id);
		resp.put("status", status);
		return JsonResult.success(resp);
	}

	@SaCheckLogin
	@Operation(summary = "可能想换 TA 的人（物品供给向量反向召回需求侧物品，向量通道不可用时降级空数组）")
	@GetMapping("/{id}/wanters")
	public JsonResult<List<ExchangeItem>> wanters(@PathVariable Long id, @RequestParam(defaultValue = "6") int limit) {
		return JsonResult.success(matchService.wanters(id, limit));
	}

}
