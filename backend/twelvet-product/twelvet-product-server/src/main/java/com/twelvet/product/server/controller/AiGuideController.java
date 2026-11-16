package com.twelvet.product.server.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.framework.core.exception.QuotaExceededException;
import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.product.api.domain.ai.AiGuideChatReq;
import com.twelvet.product.api.domain.ai.ChatMsg;
import com.twelvet.product.server.service.IRecommendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * AI 导购对话接口(需登录)
 * <p>
 * 权限校验保留在 Java 层，业务逻辑代理到 Python FastAPI 服务
 * <p>
 * 配额统一（2026-09）：每日配额检查与计数统一在 Python ai-service 侧 quota_service （ai_usage_daily
 * 单一账本）事前执行；超限/过期时 Python 返回 {@code code=429} 的 ApiResponse， 非流式路径抛
 * {@link QuotaExceededException} → HTTP 429 + 友好文案透传前端， 流式路径（HTTP 200 已提交）转为
 * {@code {type:"error"}} SSE 事件透传。
 */
@Tag(description = "AI 导购接口", name = "AiGuideController")
@RestController
@RequestMapping("/product/ai-guide")
public class AiGuideController extends TWTController {

	private static final Logger log = LoggerFactory.getLogger(AiGuideController.class);

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	@Value("${ai.service.url}")
	private String aiServiceUrl;

	private final WebClient webClient;

	private final IRecommendService recommendService;

	public AiGuideController(@Qualifier("aiGuideWebClient") WebClient webClient, IRecommendService recommendService) {
		this.webClient = webClient;
		this.recommendService = recommendService;
	}

	/**
	 * 从 Sa-Token 登录上下文注入当前用户 ID（线程级，前端无需传，Agent 也绝不向用户索要 ID）； 同时注入用户兴趣画像上下文（AI
	 * 导购个性化，无画像时不注入）
	 */
	private void injectLoginUser(AiGuideChatReq req) {
		try {
			Object loginId = StpUtil.getLoginIdDefaultNull();
			if (loginId != null && req.getUserId() == null) {
				req.setUserId(Long.valueOf(loginId.toString()));
			}
			if (req.getUserId() != null && req.getInterestHint() == null) {
				req.setInterestHint(recommendService.guideInterestContext(req.getUserId()));
			}
		}
		catch (Exception e) {
			log.warn("注入登录用户 ID 失败: {}", e.getMessage());
		}
	}

	@Operation(summary = "AI 导购对话:根据需求筛选商品并返回卡片")
	@PostMapping("/chat")
	public JsonResult<Object> chat(@RequestBody AiGuideChatReq req) {
		List<ChatMsg> messages = (req == null) ? null : req.getMessages();
		if (messages == null || messages.isEmpty()) {
			return JsonResult.error("消息不能为空");
		}
		injectLoginUser(req);
		Object result;
		try {
			result = webClient.post()
				.uri(aiServiceUrl + "/ai/chat")
				.contentType(MediaType.APPLICATION_JSON)
				.headers(this::addTenantHeader)
				.bodyValue(req)
				.retrieve()
				.bodyToMono(Object.class)
				.block();
		}
		catch (Exception e) {
			log.error("AI 导购对话失败", e);
			return JsonResult.error("AI 导购暂时不可用,请稍后再试");
		}
		// 解开 Python FastAPI 服务的 {code, msg, data} 统一响应包装（放在 try 外：
		// code=429 配额超限需抛 QuotaExceededException → HTTP 429，不能被网络异常兜底吞掉）
		if (result instanceof Map<?, ?> map) {
			Object code = map.get("code");
			if (code instanceof Number number) {
				if (number.intValue() == 200) {
					return JsonResult.success(map.get("data"));
				}
				Object msg = map.get("msg");
				String message = msg == null ? "AI 服务调用失败" : msg.toString();
				if (number.intValue() == 429) {
					throw new QuotaExceededException(message);
				}
				return JsonResult.error(message);
			}
		}
		return JsonResult.success(result);
	}

	/**
	 * 透传租户上下文给 Python（X-Tenant-Id），供 Python 侧 AI 用量落账与配额检查 （ai_usage_daily 单一账本，聊天与
	 * /ai/assist/* 共用）
	 */
	private void addTenantHeader(HttpHeaders headers) {
		Long tenantId = TenantContext.get();
		if (tenantId != null) {
			headers.set("X-Tenant-Id", String.valueOf(tenantId));
		}
	}

	@Operation(summary = "AI 导购对话(流式 SSE):逐字推送文案,结束后推送商品卡片")
	@PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter chatStream(@RequestBody AiGuideChatReq req) {
		List<ChatMsg> messages = (req == null) ? null : req.getMessages();
		SseEmitter emitter = new SseEmitter(120_000L);
		if (messages == null || messages.isEmpty()) {
			try {
				emitter.send(SseEmitter.event().data("{\"type\":\"error\",\"message\":\"消息不能为空\"}"));
			}
			catch (Exception ignored) {
			}
			emitter.complete();
			return emitter;
		}
		injectLoginUser(req);
		webClient.post()
			.uri(aiServiceUrl + "/ai/chat/stream")
			.contentType(MediaType.APPLICATION_JSON)
			.headers(this::addTenantHeader)
			// 请求体必须显式写入（缺了这行上游 FastAPI 会收到空 body 返回 422）
			.bodyValue(req)
			.exchangeToFlux(upstream -> {
				MediaType contentType = upstream.headers().contentType().orElse(null);
				if (contentType != null && contentType.isCompatibleWith(MediaType.TEXT_EVENT_STREAM)) {
					return upstream.bodyToFlux(String.class);
				}
				// Python 未返回 SSE（如配额超限的 code=429 ApiResponse JSON）：解析出友好文案
				// 转为前端已定义的 {type:"error"} 事件透传（emitter 返回时 HTTP 200 已提交，无法改状态码）
				return upstream.bodyToMono(String.class)
					.defaultIfEmpty("")
					.flatMapMany(raw -> Flux.just(errorEventFromJson(raw)));
			})
			.subscribe(data -> {
				try {
					emitter.send(SseEmitter.event().data(data));
				}
				catch (Exception e) {
					emitter.completeWithError(e);
				}
			}, emitter::completeWithError, emitter::complete);
		return emitter;
	}

	/**
	 * 把 Python 返回的非 SSE JSON 响应体（统一 {@code {code, msg}} 包装，如配额超限 429） 转为前端已定义的
	 * {@code {type:"error", code, message}} SSE 事件。
	 * @param raw Python 原始响应体文本
	 * @return 可直接 send 的 data 文本
	 */
	private String errorEventFromJson(String raw) {
		// 非 SSE 响应统一记录原始体（FastAPI 422 校验失败等场景，detail 只在这里能拿到）
		log.warn("AI 上游返回非 SSE 响应: {}", raw);
		int code = 500;
		String message = "AI 服务调用失败";
		try {
			if (raw != null && !raw.isBlank()) {
				JsonNode node = OBJECT_MAPPER.readTree(raw);
				if (node != null && node.hasNonNull("code") && node.get("code").isNumber()) {
					code = node.get("code").asInt();
				}
				if (node != null && node.hasNonNull("msg")) {
					message = node.get("msg").asText();
				}
			}
		}
		catch (Exception ex) {
			log.warn("解析 Python 非 SSE 响应失败，按默认错误透传: {}", raw);
		}
		try {
			return OBJECT_MAPPER.writeValueAsString(Map.of("type", "error", "code", code, "message", message));
		}
		catch (Exception ex) {
			return "{\"type\":\"error\",\"code\":" + code + ",\"message\":\"AI 服务调用失败\"}";
		}
	}

}
