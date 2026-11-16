package com.twelvet.ai.server.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.twelvet.ai.server.client.AiServiceClient;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI 歌词助手 Controller
 *
 * <p>
 * 代理到 Python ai-service 的 /ai/music/lyrics/{draft,revise}, 商汤上游无配额(但 BFF 层仍校验每日调用次数, 见
 * usage_service)。
 * </p>
 */
@Tag(description = "AI 歌词助手", name = "AI 歌词助手")
@RestController
@RequestMapping("/ai/music/lyrics")
public class AiMusicLyricsController extends TWTController {

	private final AiServiceClient aiServiceClient;

	public AiMusicLyricsController(AiServiceClient aiServiceClient) {
		this.aiServiceClient = aiServiceClient;
	}

	@Operation(summary = "AI 写词")
	@PostMapping("/draft")
	@SaCheckPermission("ai:music:lyrics:draft")
	public JsonResult<Object> draft(@RequestBody Map<String, Object> req) {
		Map<String, String> headers = Map.of("X-User-Id", String.valueOf(StpUtil.getLoginIdAsLong()));
		return JsonResult.success(aiServiceClient.post("/ai/music/lyrics/draft", req, Object.class, headers));
	}

	@Operation(summary = "AI 改写歌词")
	@PostMapping("/revise")
	@SaCheckPermission("ai:music:lyrics:revise")
	public JsonResult<Object> revise(@RequestBody Map<String, Object> req) {
		Map<String, String> headers = Map.of("X-User-Id", String.valueOf(StpUtil.getLoginIdAsLong()));
		return JsonResult.success(aiServiceClient.post("/ai/music/lyrics/revise", req, Object.class, headers));
	}

}
