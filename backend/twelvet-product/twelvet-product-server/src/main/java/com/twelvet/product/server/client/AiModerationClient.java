package com.twelvet.product.server.client;

import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * AI 初审客户端：代理调用 Python ai-service /ai/moderation/content。
 * <p>
 * 与 AiGuideController 同款 WebClient（aiGuideWebClient，180s 响应上限）， 这里 block 再叠加 150s 上限（文本 +
 * 视觉两次 LLM 调用的最坏耗时）。
 * <p>
 * 任何失败（超时/429 配额/未配置模型/网络异常）都返回 null，由任务侧跳过该条 留待人工或下轮重试——初审是旁路增强，绝不阻塞或误伤主流程。
 */
@Component
public class AiModerationClient {

	private static final Logger log = LoggerFactory.getLogger(AiModerationClient.class);

	private static final Duration CALL_TIMEOUT = Duration.ofSeconds(150);

	private final WebClient webClient;

	@Value("${ai.service.url}")
	private String aiServiceUrl;

	public AiModerationClient(@Qualifier("aiGuideWebClient") WebClient webClient) {
		this.webClient = webClient;
	}

	/**
	 * 审查一条内容。
	 * @param tenantId 业务对象所属租户（配额账本按租户计）
	 * @param payload bizType/title/description/categoryName/price/condition/images
	 * @return data Map（riskLevel/action/violations/summary/modelName）；失败返回 null
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> moderate(Long tenantId, Map<String, Object> payload) {
		try {
			Object resp = webClient.post()
				.uri(aiServiceUrl + "/ai/moderation/content")
				.contentType(MediaType.APPLICATION_JSON)
				.header("X-Tenant-Id", String.valueOf(tenantId))
				.bodyValue(payload)
				.retrieve()
				.bodyToMono(Map.class)
				.block(CALL_TIMEOUT);
			if (resp instanceof Map<?, ?> map) {
				Object code = map.get("code");
				if (code instanceof Number number && number.intValue() == 200) {
					Object data = map.get("data");
					return (data instanceof Map) ? (Map<String, Object>) data : null;
				}
				log.warn("[AI初审] ai-service 返回非 200：code={} msg={}", code, map.get("msg"));
			}
		}
		catch (Exception e) {
			log.warn("[AI初审] 调用失败（跳过留人工）: {}", e.getMessage());
		}
		return null;
	}

}
