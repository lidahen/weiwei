package com.twelvet.product.server.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.product.server.service.IUserPrivacyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户隐私设置控制器（个人设置页的三个开关）
 * <p>
 * 身份一律取自登录态；请求体只用来承载开关值（userId 一律忽略）。 值归一与合法性校验在 Service。
 * </p>
 *
 * @author twelvet
 */
@Tag(description = "隐私设置", name = "UserPrivacyController")
@RestController
@RequestMapping("/user/privacy")
public class UserPrivacyController extends TWTController {

	private final IUserPrivacyService privacyService;

	public UserPrivacyController(IUserPrivacyService privacyService) {
		this.privacyService = privacyService;
	}

	/**
	 * 读取隐私三开关（无记录时全开）
	 */
	@Operation(summary = "读取隐私设置")
	@GetMapping
	public JsonResult<Map<String, Object>> get() {
		Long userId = StpUtil.getLoginIdAsLong();
		return JsonResult.success(privacyService.getPrivacy(userId));
	}

	/**
	 * 保存隐私设置 body: {allowMobileSearch, allowFeedByStranger,
	 * enableNearbyRecommend}，缺省字段保持原值
	 */
	@Operation(summary = "保存隐私设置")
	@PostMapping
	public JsonResult<String> save(@RequestBody Map<String, Object> body) {
		Long userId = StpUtil.getLoginIdAsLong();
		Integer allowMobileSearch = toFlag(body.get("allowMobileSearch"));
		Integer allowFeedByStranger = toFlag(body.get("allowFeedByStranger"));
		Integer enableNearbyRecommend = toFlag(body.get("enableNearbyRecommend"));
		privacyService.savePrivacy(userId, allowMobileSearch, allowFeedByStranger, enableNearbyRecommend);
		return JsonResult.success("OK");
	}

	/**
	 * 开关值解析：null 保持 null（原值）；Boolean 归一 1/0；数字与 "0"/"1" 字符串原样取整。
	 * <p>
	 * 解析不了的给 -1 交给 Service 统一抛「开关值只能是 0 或 1」，避免同一个文案散落两处。
	 * </p>
	 */
	private static Integer toFlag(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof Boolean b) {
			return b ? 1 : 0;
		}
		if (value instanceof Number n) {
			return n.intValue();
		}
		String s = String.valueOf(value).trim();
		if (s.isEmpty() || "null".equals(s)) {
			return null;
		}
		try {
			return Integer.valueOf(s);
		}
		catch (NumberFormatException e) {
			return -1;
		}
	}

}