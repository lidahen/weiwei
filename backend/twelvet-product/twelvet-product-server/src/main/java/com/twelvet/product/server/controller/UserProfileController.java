package com.twelvet.product.server.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.stp.StpUtil;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.product.server.service.IUserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户资料/关注/地址控制器 职责: HTTP参数绑定、权限校验、调用Service、包装响应 业务逻辑全部委托给UserProfileService
 *
 * @author twelvet
 */
@Tag(description = "用户资料", name = "UserProfileController")
@RestController
@RequestMapping("/user")
public class UserProfileController extends TWTController {

	private final IUserProfileService userProfileService;

	public UserProfileController(IUserProfileService userProfileService) {
		this.userProfileService = userProfileService;
	}

	/**
	 * 我自己的资料
	 */
	@Operation(summary = "我自己的资料")
	@GetMapping("/profile/me")
	public JsonResult<Map<String, Object>> me() {
		Long userId = StpUtil.getLoginIdAsLong();
		Map<String, Object> profile = userProfileService.selectUserProfile(userId, userId);
		return JsonResult.success(profile);
	}

	/**
	 * 他人主页
	 */
	@SaIgnore
	@Operation(summary = "他人主页")
	@GetMapping("/profile/{userId}")
	public JsonResult<Map<String, Object>> profile(@PathVariable Long userId) {
		Long viewerId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
		Map<String, Object> profile = userProfileService.selectUserProfile(userId, viewerId);
		return JsonResult.success(profile);
	}

	/**
	 * 卖家信用画像（需登录：含失信扣分明细）
	 */
	@Operation(summary = "卖家信用画像")
	@GetMapping("/credit/{userId}")
	public JsonResult<Map<String, Object>> credit(@PathVariable Long userId) {
		return JsonResult.success(userProfileService.creditProfile(userId));
	}

	/**
	 * 关注用户
	 */
	@Operation(summary = "关注用户")
	@PostMapping("/follow")
	public JsonResult<String> follow(@RequestParam Long targetUserId) {
		Long userId = StpUtil.getLoginIdAsLong();
		return JsonResult.success(userProfileService.followUser(userId, targetUserId));
	}

	/**
	 * 取消关注
	 */
	@Operation(summary = "取消关注")
	@PostMapping("/unfollow")
	public JsonResult<String> unfollow(@RequestParam Long targetUserId) {
		Long userId = StpUtil.getLoginIdAsLong();
		return JsonResult.success(userProfileService.unfollowUser(userId, targetUserId));
	}

	/**
	 * 我的关注/粉丝列表
	 */
	@Operation(summary = "我的关注/粉丝列表")
	@GetMapping("/follows/list")
	public JsonResult<List<Map<String, Object>>> follows(@RequestParam(defaultValue = "follows") String tab) {
		Long userId = StpUtil.getLoginIdAsLong();
		List<Map<String, Object>> list = userProfileService.selectFollowList(userId, tab);
		return JsonResult.success(list);
	}

	/**
	 * 我的收货地址列表
	 */
	@Operation(summary = "我的收货地址")
	@GetMapping("/address/list")
	public JsonResult<List<Map<String, Object>>> addressList() {
		Long userId = StpUtil.getLoginIdAsLong();
		List<Map<String, Object>> list = userProfileService.selectAddressList(userId);
		return JsonResult.success(list);
	}

	/**
	 * 新增地址
	 */
	@Operation(summary = "新增地址")
	@PostMapping("/address/save")
	public JsonResult<String> saveAddress(@RequestBody Map<String, Object> body) {
		Long userId = StpUtil.getLoginIdAsLong();
		return JsonResult.success(userProfileService.saveAddress(userId, body));
	}

	/**
	 * 删除地址
	 */
	@Operation(summary = "删除地址")
	@DeleteMapping("/address/{addressId}")
	public JsonResult<String> removeAddress(@PathVariable Long addressId) {
		Long userId = StpUtil.getLoginIdAsLong();
		return JsonResult.success(userProfileService.removeAddress(addressId, userId));
	}

}
