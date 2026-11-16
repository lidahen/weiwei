package com.twelvet.im.server.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.stp.StpUtil;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.im.api.domain.ImConversation;
import com.twelvet.im.api.domain.ImMessage;
import com.twelvet.im.api.domain.ImNotice;
import com.twelvet.im.server.service.IImService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * IM即时通讯控制器 职责: HTTP参数绑定、权限校验、调用Service、包装响应 业务逻辑全部委托给ImService
 *
 * @author twelvet
 */
@Tag(description = "即时通讯", name = "ImController")
@RestController
@RequestMapping("/im")
public class ImController extends TWTController {

	private final IImService imService;

	public ImController(IImService imService) {
		this.imService = imService;
	}

	/**
	 * 我的会话列表
	 */
	@Operation(summary = "我的会话列表")
	@GetMapping("/conversation/list")
	public JsonResult<List<ImConversation>> conversations() {
		Long userId = StpUtil.getLoginIdAsLong();
		List<ImConversation> list = imService.selectConversationList(userId);
		return JsonResult.success(list);
	}

	/**
	 * 对方资料
	 */
	@Operation(summary = "对方资料")
	@GetMapping("/peer/info")
	public JsonResult<Map<String, Object>> peerInfo(@RequestParam Long peerId) {
		Map<String, Object> userInfo = imService.selectUserInfo(peerId);
		if (userInfo == null) {
			return JsonResult.error("用户不存在");
		}
		return JsonResult.success(userInfo);
	}

	/**
	 * 消息列表
	 */
	@Operation(summary = "消息列表")
	@GetMapping("/message/list")
	public JsonResult<List<Map<String, Object>>> messages(@RequestParam Long peerId,
			@RequestParam(required = false) Long productId) {
		Long userId = StpUtil.getLoginIdAsLong();
		Long convId = imService.ensureConversation(userId, peerId, productId);
		List<ImMessage> messages = imService.selectMessageList(convId);

		Map<String, Object> wrapper = new java.util.HashMap<>();
		wrapper.put("convId", convId);
		wrapper.put("messages", messages);
		return JsonResult.success(java.util.Collections.singletonList(wrapper));
	}

	/**
	 * 发送消息
	 */
	@Operation(summary = "发送消息")
	@PostMapping("/message/send")
	public JsonResult<Map<String, Object>> send(@RequestBody Map<String, Object> body) {
		Long userId = StpUtil.getLoginIdAsLong();
		Long peerId = Long.valueOf(String.valueOf(body.get("peerId")));
		Long productId = body.get("productId") != null ? Long.valueOf(String.valueOf(body.get("productId"))) : null;
		String type = String.valueOf(body.getOrDefault("type", "text"));
		String content = String.valueOf(body.get("content"));
		Map<String, Object> result = imService.sendMessage(userId, peerId, productId, type, content);
		return JsonResult.success(result);
	}

	/**
	 * 通知中心
	 */
	@Operation(summary = "通知中心")
	@GetMapping("/notice/list")
	public JsonResult<List<ImNotice>> notices() {
		Long userId = StpUtil.getLoginIdAsLong();
		List<ImNotice> list = imService.selectNoticeList(userId);
		return JsonResult.success(list);
	}

	/**
	 * 未读汇总（当前用户全部会话未读总数 + 按会话分布） 口径：会话内 msg_id 大于用户已读位点（im_unread.last_read_msg_id）的他人消息
	 */
	@Operation(summary = "未读汇总")
	@GetMapping("/unread/count")
	public JsonResult<Map<String, Object>> unreadCount() {
		Long userId = StpUtil.getLoginIdAsLong();
		return JsonResult.success(imService.selectUnreadSummary(userId));
	}

	/**
	 * 标记已读（conversationId 缺省=全部会话已读；messageId 可选，表示读到哪条，缺省推进到会话最后一条）
	 */
	@Operation(summary = "标记已读")
	@PutMapping("/unread/read")
	public JsonResult<Map<String, Object>> markRead(@RequestParam(required = false) Long conversationId,
			@RequestParam(required = false) Long messageId) {
		Long userId = StpUtil.getLoginIdAsLong();
		Map<String, Object> result = conversationId == null ? imService.markAllConversationRead(userId)
				: imService.markConversationRead(userId, conversationId, messageId);
		return JsonResult.success(result);
	}

	/**
	 * Ping健康检查
	 */
	@SaIgnore
	@Operation(summary = "Ping")
	@GetMapping("/ping")
	public JsonResult<String> ping() {
		return JsonResult.success("pong from im");
	}

}
