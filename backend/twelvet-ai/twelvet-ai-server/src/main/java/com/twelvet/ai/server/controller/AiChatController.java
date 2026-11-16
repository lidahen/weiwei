package com.twelvet.ai.server.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.exception.NotLoginException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.twelvet.ai.server.client.AiServiceClient;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.framework.core.constants.Constants;
import com.twelvet.framework.core.tenant.TenantContext;
import cn.dev33.satoken.stp.StpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Map;

/**
 * AI对话控制器（知识库 RAG 聊天 / 聊天历史 / TTS）
 * <p>
 * 权限校验保留在 Java 层，业务逻辑代理到 Python FastAPI 服务。 知识库聊天为 SSE 流式响应，Java 层负责把 Python 的 SSE
 * 事件透传给前端。
 */
@Tag(description = "AI对话接口", name = "AI对话")
@RestController
@RequestMapping("/ai/chat")
public class AiChatController extends TWTController {

	private static final Logger log = LoggerFactory.getLogger(AiChatController.class);

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private static final ParameterizedTypeReference<ServerSentEvent<String>> SSE_TYPE = new ParameterizedTypeReference<>() {
	};

	@Value("${ai.service.url}")
	private String aiServiceUrl;

	private final WebClient webClient;

	private final AiServiceClient aiServiceClient;

	public AiChatController(@Qualifier("aiWebClient") WebClient webClient, AiServiceClient aiServiceClient) {
		this.webClient = webClient;
		this.aiServiceClient = aiServiceClient;
	}

	/**
	 * 聊天统一入口
	 * <p>
	 * - 请求体含 knowledgeId：知识库 RAG 聊天，SSE 流式透传； - 请求体含 messages：商品导购聊天，非流式 JSON 转发。
	 * <p>
	 * 配额统一（2026-09）：每日配额检查与计数不再在本层做（原 {@code AiQuotaChecker.checkAndIncr} 的 Redis 按日 INCR
	 * 已停用），统一由 Python ai-service 侧 quota_service 在 ai_usage_daily 上事前检查；超限/过期返回
	 * {@code code=429} 的 ApiResponse，本控制器原样透传给前端 （非流式路径经 AiServiceClient.unwrapBody 转
	 * QuotaExceededException → HTTP 429）。
	 * @param body 请求体（camelCase，透传 Python）
	 * @return SseEmitter（知识库模式）或 JsonResult（导购模式）
	 */
	@Operation(summary = "知识库RAG聊天(SSE) / 导购聊天")
	@PostMapping
	public Object chat(HttpServletRequest request, @RequestBody(required = false) Map<String, Object> body) {
		// 权限校验：本端点为 SSE 流式，运行在异步线程上；SaTokenContext 基于 ThreadLocal，
		// 跨线程后上下文已丢失，故将 /ai/chat 移出 SaToken 拦截器，改在此处（startAsync 之前、
		// 仍是原请求线程）从请求头解析 token 直接校验登录。
		checkLogin(request);

		// 知识库 RAG 聊天：SSE 流式转发
		if (body != null && body.containsKey("knowledgeId")) {
			SseEmitter emitter = new SseEmitter(300_000L);
			webClient.post()
				.uri(aiServiceUrl + "/ai/chat")
				.contentType(MediaType.APPLICATION_JSON)
				.headers(h -> addTenantHeader(h))
				.exchangeToFlux(upstream -> {
					MediaType contentType = upstream.headers().contentType().orElse(null);
					if (contentType != null && contentType.isCompatibleWith(MediaType.TEXT_EVENT_STREAM)) {
						return upstream.bodyToFlux(SSE_TYPE);
					}
					// Python 未返回 SSE（如配额超限的 code=429 ApiResponse JSON）：解析出友好文案
					// 转为前端已定义的 {type:"error"} 事件透传（emitter 返回时 HTTP 200 已提交，无法改状态码）
					return upstream.bodyToMono(String.class)
						.defaultIfEmpty("")
						.flatMapMany(raw -> Flux.just(errorEventFromJson(raw)));
				})
				.subscribe(event -> {
					try {
						if (event != null && event.data() != null) {
							emitter.send(SseEmitter.event().data(event.data()));
						}
					}
					catch (Exception e) {
						emitter.completeWithError(e);
					}
				}, emitter::completeWithError, emitter::complete);
			return emitter;
		}
		// 商品导购聊天：非流式 JSON 转发。Python 返回 code=429（配额超限）时由
		// unwrapBody 抛 QuotaExceededException → HTTP 429 + 友好文案，不吞成 500
		Object result = webClient.post()
			.uri(aiServiceUrl + "/ai/chat")
			.contentType(MediaType.APPLICATION_JSON)
			.headers(h -> addTenantHeader(h))
			.bodyValue(body == null ? Map.of() : body)
			.retrieve()
			.bodyToMono(Object.class)
			.block();
		// 显式以 Object 接收：unwrapBody 的泛型若被推断为 success(String) 重载，
		// python 聊天返回的 data 为 Map 时会在调用点插入 checkcast String 抛 ClassCastException
		Object data = AiServiceClient.unwrapBody(result);
		return JsonResult.success(data);
	}

	/**
	 * 把 Python 返回的非 SSE JSON 响应体（统一 {@code {code, msg}} 包装，如配额超限 429） 转为前端已定义的
	 * {@code {type:"error", code, message}} SSE 事件（与导购流式协议一致）。
	 * @param raw Python 原始响应体文本
	 * @return SSE 事件
	 */
	private ServerSentEvent<String> errorEventFromJson(String raw) {
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
		String payload;
		try {
			payload = OBJECT_MAPPER.writeValueAsString(Map.of("type", "error", "code", code, "message", message));
		}
		catch (Exception ex) {
			payload = "{\"type\":\"error\",\"code\":" + code + ",\"message\":\"AI 服务调用失败\"}";
		}
		return ServerSentEvent.builder(payload).build();
	}

	/**
	 * 透传租户上下文给 Python（X-Tenant-Id），供 Python 侧 AI 用量落账与配额检查 （ai_usage_daily 单一账本，聊天与
	 * /ai/assist/* 共用）
	 * @param headers 请求头构建器
	 */
	private void addTenantHeader(HttpHeaders headers) {
		Long tenantId = TenantContext.get();
		if (tenantId != null) {
			headers.set("X-Tenant-Id", String.valueOf(tenantId));
		}
	}

	/**
	 * 从请求头解析 token 并校验登录。
	 * <p>
	 * 与项目的 {@code Authorization: Bearer <token>} 约定一致。
	 * {@link StpUtil#getLoginIdByToken(String)} 用 token 值直接校验登录（查 Redis 存储）， 不依赖当前线程的
	 * SaTokenContext（ThreadLocal 跨异步线程会丢失），因此安全。
	 * @param request 当前请求
	 */
	private void checkLogin(HttpServletRequest request) {
		String auth = request.getHeader(Constants.AUTHORIZATION);
		if (auth == null || auth.isBlank()) {
			throw new NotLoginException("NOT_LOG", "未登录", null);
		}
		String tokenValue = auth.startsWith("Bearer ") ? auth.substring("Bearer ".length()) : auth;
		if (tokenValue.isBlank()) {
			throw new NotLoginException("NOT_LOG", "未登录", null);
		}
		// 用 token 值直接校验；token 不存在/已失效/已退出时会抛 NotLoginException
		StpUtil.getLoginIdByToken(tokenValue);
	}

	/**
	 * 分页查询知识库聊天历史
	 * @param query 查询条件（knowledgeId/pageNum/pageSize/current 等）
	 * @return JsonResult<Map>
	 */
	@Operation(summary = "分页查询知识库聊天历史")
	@GetMapping("/history/page")
	@SaCheckPermission("ai:chat:list")
	public JsonResult<Object> historyPage(@RequestParam MultiValueMap<String, String> query) {
		String url = UriComponentsBuilder.fromPath("/ai/chat/history/page").queryParams(query).toUriString();
		return JsonResult.success(aiServiceClient.get(url, Object.class));
	}

	/**
	 * TTS 语音合成：透传 Python 的 audio/mpeg 二进制（Python 出错时返回 JSON，按 Content-Type 原样透传，
	 * 由前端区分处理）。登录校验由 SaInterceptor 兜底；不挂 @SaCheckPermission， AI 导购页角色无需 ai:chat:list
	 * 权限即可朗读回复。
	 * @param body 请求体 {text, voice?, speed?}
	 * @return audio/mpeg 或 JSON 错误
	 */
	@Operation(summary = "TTS 语音合成")
	@PostMapping("/tts")
	public ResponseEntity<byte[]> tts(@RequestBody(required = false) Map<String, Object> body) {
		ResponseEntity<byte[]> upstream = webClient.post()
			.uri(aiServiceUrl + "/ai/chat/tts")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(body == null ? Map.of() : body)
			.retrieve()
			.toEntity(byte[].class)
			.block(Duration.ofSeconds(60));
		if (upstream == null || upstream.getBody() == null) {
			return ResponseEntity.internalServerError().contentType(MediaType.APPLICATION_JSON).body(new byte[0]);
		}
		MediaType contentType = upstream.getHeaders().getContentType();
		return ResponseEntity.status(upstream.getStatusCode())
			.contentType(contentType != null ? contentType : MediaType.APPLICATION_OCTET_STREAM)
			.body(upstream.getBody());
	}

}
