package com.twelvet.exchange.server.client;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.twelvet.framework.core.tenant.TenantContext;

/**
 * 换物向量匹配客户端（Python ai-service /exchange-match/*，docs/交换广场二期-智能匹配与信用设计.md §4.1）
 * <p>
 * 与 RecommendVectorClient 同款 WebClient（aiGuideWebClient）。向量通道是增强而非依赖： 所有失败（503 向量服务不可用
 * / 超时 / 网络异常）一律返回空列表或 0，由调用方降级（推荐位空态、推送跳过），绝不向用户抛错。
 * </p>
 */
@Component
public class ExchangeMatchClient {

	private static final Logger log = LoggerFactory.getLogger(ExchangeMatchClient.class);

	/** 相似/候选查询上限（广场与详情页都是同步展示链路，快速失败） */
	private static final Duration QUERY_TIMEOUT = Duration.ofSeconds(3);

	/** 全量增量同步（Quartz 每 30 分钟一次，物品量级小，放宽超时） */
	private static final Duration REINDEX_TIMEOUT = Duration.ofSeconds(120);

	@Value("${ai.service.url}")
	private String aiServiceUrl;

	private final WebClient webClient;

	public ExchangeMatchClient(@Qualifier("aiGuideWebClient") WebClient webClient) {
		this.webClient = webClient;
	}

	/** 匹配召回命中：物品ID + 相似度分（cosine） */
	public record MatchHit(long itemId, double score) {
	}

	/**
	 * 我的需求文本 ↔ 供给索引（Square 推荐位）
	 * @param wantTexts 用户全部 onswap 物品的 want_desc 集合
	 * @return 命中列表（可能为空=索引未构建/不可用，调用方降级）
	 */
	public List<MatchHit> forMe(List<String> wantTexts, int limit) {
		Map<String, Object> body = new HashMap<>();
		body.put("want_texts", wantTexts);
		body.put("limit", limit);
		Map<String, Object> data = postForData("/exchange-match/for-me", body, tenantHeaders(), QUERY_TIMEOUT);
		return parseHits(data);
	}

	/**
	 * 物品供给向量 ↔ 需求索引（详情页「可能想换的人」+ 推送扫描；返回需求侧物品，由调用方反查 owner）
	 * @return 命中列表（可能为空，调用方降级）
	 */
	public List<MatchHit> wanters(long itemId, int limit) {
		Map<String, Object> data = getForData("/exchange-match/wanters",
				Map.of("item_id", itemId, "limit", limit), tenantHeaders(), QUERY_TIMEOUT);
		return parseHits(data);
	}

	/**
	 * 增量同步换物双索引（Quartz 每 30 分钟调用）
	 * @return 索引内物品总数（失败返回 0，下轮重试）
	 */
	public int reindex() {
		Map<String, Object> data = postForData("/exchange-match/reindex", Map.of(), Map.of(), REINDEX_TIMEOUT);
		if (data == null || data.get("indexed") == null) {
			return 0;
		}
		return ((Number) data.get("indexed")).intValue();
	}

	// ====== 内部 ======

	private List<MatchHit> parseHits(Map<String, Object> data) {
		List<MatchHit> hits = new ArrayList<>();
		if (data == null || !(data.get("hits") instanceof List<?> list)) {
			return hits;
		}
		for (Object o : list) {
			if (o instanceof Map<?, ?> m && m.get("itemId") != null) {
				hits.add(new MatchHit(((Number) m.get("itemId")).longValue(),
						m.get("score") == null ? 0D : ((Number) m.get("score")).doubleValue()));
			}
		}
		return hits;
	}

	/** 租户头（Quartz 线程由调用方 TenantContext.set 预置；无上下文时不带头，由上游 400 降级为空） */
	private Map<String, String> tenantHeaders() {
		Long tid = TenantContext.get();
		return tid == null ? Map.of() : Map.of("X-Tenant-Id", String.valueOf(tid));
	}

	/** GET 请求并解包 {code,msg,data}；code!=200 返回 null（降级），网络异常同样降级为 null */
	private Map<String, Object> getForData(String path, Map<String, Object> query, Map<String, String> headers,
			Duration timeout) {
		StringBuilder url = new StringBuilder(aiServiceUrl).append(path);
		if (!query.isEmpty()) {
			url.append('?');
			query.forEach((k, v) -> url.append(k).append('=').append(v).append('&'));
			url.setLength(url.length() - 1);
		}
		try {
			return unwrap(webClient.get()
				.uri(java.net.URI.create(url.toString()))
				.headers(h -> headers.forEach(h::set))
				.retrieve()
				.bodyToMono(Map.class)
				.block(timeout), path);
		}
		catch (Exception e) {
			log.warn("[换物匹配] 调用失败（降级空结果）: {} {}", path, e.getMessage());
			return null;
		}
	}

	private Map<String, Object> postForData(String path, Object body, Map<String, String> headers, Duration timeout) {
		try {
			return unwrap(webClient.post()
				.uri(aiServiceUrl + path)
				.contentType(MediaType.APPLICATION_JSON)
				.headers(h -> headers.forEach(h::set))
				.bodyValue(body)
				.retrieve()
				.bodyToMono(Map.class)
				.block(timeout), path);
		}
		catch (Exception e) {
			log.warn("[换物匹配] 调用失败（降级空结果）: {} {}", path, e.getMessage());
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> unwrap(Map<String, Object> resp, String path) {
		if (resp == null) {
			log.warn("[换物匹配] 上游无响应: {}", path);
			return null;
		}
		Object code = resp.get("code");
		if (code instanceof Number n && n.intValue() != 200) {
			// 503=embedding未配置/索引未构建，400=缺租户——一律视为"匹配通道不可用"降级
			log.warn("[换物匹配] 匹配通道不可用({}): code={}, msg={}", path, code, resp.get("msg"));
			return null;
		}
		Object data = resp.get("data");
		return data instanceof Map ? (Map<String, Object>) data : null;
	}

}
