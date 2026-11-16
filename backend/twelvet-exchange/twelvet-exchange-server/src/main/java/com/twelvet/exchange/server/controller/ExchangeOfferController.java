package com.twelvet.exchange.server.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.twelvet.exchange.api.domain.ExchangeOffer;
import com.twelvet.exchange.server.service.IExchangeOfferService;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 换物意向 - 发起/收发列表/接受
 *
 * @author twelvet
 */
@Tag(description = "换物意向", name = "ExchangeOfferController")
@RestController
@RequestMapping("/exchange/offer")
public class ExchangeOfferController extends TWTController {

	private final IExchangeOfferService offerService;

	public ExchangeOfferController(IExchangeOfferService offerService) {
		this.offerService = offerService;
	}

	@SaCheckLogin
	@Operation(summary = "发起意向（offer_item_id 或 offer_snapshot 二选一，72h 有效）")
	@PostMapping("")
	public JsonResult<Map<String, Object>> create(@RequestBody ExchangeOffer offer) {
		Long offerorId = StpUtil.getLoginIdAsLong();
		Long offerId = offerService.create(offer, offerorId);

		Map<String, Object> resp = new HashMap<>();
		resp.put("offerId", offerId);
		resp.put("status", offer.getStatus());
		resp.put("expireTime", offer.getExpireTime());
		return JsonResult.success(resp);
	}

	@SaCheckLogin
	@Operation(summary = "我收到的意向（pending 优先）")
	@GetMapping("/received")
	public JsonResult<List<ExchangeOffer>> received() {
		Long userId = StpUtil.getLoginIdAsLong();
		return JsonResult.success(offerService.received(userId));
	}

	@SaCheckLogin
	@Operation(summary = "我发出的意向")
	@GetMapping("/sent")
	public JsonResult<List<ExchangeOffer>> sent() {
		Long userId = StpUtil.getLoginIdAsLong();
		return JsonResult.success(offerService.sent(userId));
	}

	@SaCheckLogin
	@Operation(summary = "接受意向（乐观锁事务，生成订单）")
	@PostMapping("/{id}/accept")
	public JsonResult<Map<String, Object>> accept(@PathVariable Long id) {
		Long userId = StpUtil.getLoginIdAsLong();
		Long orderId = offerService.accept(id, userId);
		Map<String, Object> resp = new HashMap<>();
		resp.put("offerId", id);
		resp.put("orderId", orderId);
		return JsonResult.success(resp);
	}

	@SaCheckLogin
	@Operation(summary = "婉拒意向")
	@PostMapping("/{id}/reject")
	public JsonResult<String> reject(@PathVariable Long id) {
		Long userId = StpUtil.getLoginIdAsLong();
		offerService.reject(id, userId);
		return JsonResult.success("OK");
	}

	@SaCheckLogin
	@Operation(summary = "撤回我发起的意向")
	@PostMapping("/{id}/withdraw")
	public JsonResult<String> withdraw(@PathVariable Long id) {
		Long userId = StpUtil.getLoginIdAsLong();
		offerService.withdraw(id, userId);
		return JsonResult.success("OK");
	}

}
