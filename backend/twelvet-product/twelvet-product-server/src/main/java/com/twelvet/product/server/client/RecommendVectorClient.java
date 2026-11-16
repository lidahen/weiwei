package com.twelvet.product.server.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.twelvet.product.server.service.IRecommendService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品向量推荐客户端（Python ai-service /recommend/*，docs/二手市场首页推荐设计.md 二期）
 * <p>
 * 向量通道是增强而非依赖：所有方法失败时返回空结果/抛出异常，由调用方降级为规则通道 （首页降级热门+新品，详情页降级同类目热门）。
 * </p>
 */
@Component
public class RecommendVectorClient {

	private static final Logger log = LoggerFactory.getLogger(RecommendVectorClient.class);

	/** 相似/候选查询上限（首页与详情页都是同步展示链路，快速失败） */
	private static final Duration QUERY_TIMEOUT = Duration.ofSeconds(3);

	/** 全量增量同步（Quartz 每日一次，商品量级小，放宽超时） */
	private static final Duration REINDEX_TIMEOUT = Duration.ofSeconds(120);

	@Value("${ai.service.url}")
	private String aiServiceUrl;

	private final WebClient webClient;

	public RecommendVectorClient(@Qualifier("aiGuideWebClient") WebClient webClient) {
		this.webClient = webClient;
	}

	/** 向量召回命中：productId + 相似度分（cosine） */
	public record VectorHit(long productId, double score) {
	}

	/**
	 * 详情页相似商品
	 * @return 命中列表（可能为空=索引未构建/不可用，调用方降级）
	 */
	public List<VectorHit> similar(long tenantId, long productId, int limit) {
		Map<String, Object> data = getForData("/recommend/similar", Map.of("product_id", productId, "limit", limit),
				Map.of("X-Tenant-Id", String.valueOf(tenantId)), QUERY_TIMEOUT);
		return parseHits(data);
	}

	/**
	 * 首页向量召回候选（seed = 用户近期交互商品）
	 * @return 命中列表（可能为空，调用方降级）
	 */
	public List<VectorHit> candidates(long tenantId, List<Long> seedProductIds, int limit) {
		Map<String, Object> body = new HashMap<>();
		body.put("product_ids", seedProductIds);
		body.put("limit", limit);
		Map<String, Object> data = postForData("/recommend/candidates", body,
				Map.of("X-Tenant-Id", String.valueOf(tenantId)), QUERY_TIMEOUT);
		return parseHits(data);
	}

	/**
	 * 全量增量同步向量索引（Quartz 每日调用）
	 * @return 索引内商品总数
	 * @throws IllegalStateException 上游不可用/未配置 embedding（由任务记日志）
	 */
	public int reindex() {
		Map<String, Object> data = postForData("/recommend/reindex", Map.of(), Map.of(), REINDEX_TIMEOUT);
		if (data == null || data.get("indexed") == null) {
			throw new IllegalStateException("向量索引同步返回缺少 indexed 字段");
		}
		return ((Number) data.get("indexed")).intValue();
	}

	// ====== 内部 ======

	private List<VectorHit> parseHits(Map<String, Object> data) {
		List<VectorHit> hits = new ArrayList<>();
		if (data == null || !(data.get("hits") instanceof List<?> list)) {
			return hits;
		}
		for (Object o : list) {
			if (o instanceof Map<?, ?> m && m.get("productId") != null) {
				hits.add(new VectorHit(((Number) m.get("productId")).longValue(),
						m.get("score") == null ? 0D : ((Number) m.get("score")).doubleValue()));
			}
		}
		return hits;
	}

	/** GET 请求并解包 {code,msg,data}；code!=200 返回 null（降级），网络异常抛出由调用方捕获 */
	private Map<String, Object> getForData(String path, Map<String, Object> query, Map<String, String> headers,
			Duration timeout) {
		StringBuilder url = new StringBuilder(aiServiceUrl).append(path);
		if (!query.isEmpty()) {
			url.append('?');
			query.forEach((k, v) -> url.append(k).append('=').append(v).append('&'));
			url.setLength(url.length() - 1);
		}
		return unwrap(webClient.get()
			.uri(java.net.URI.create(url.toString()))
			.headers(h -> headers.forEach(h::set))
			.retrieve()
			.bodyToMono(Map.class)
			.block(timeout), path);
	}

	private Map<String, Object> postForData(String path, Object body, Map<String, String> headers, Duration timeout) {
		return unwrap(webClient.post()
			.uri(aiServiceUrl + path)
			.contentType(MediaType.APPLICATION_JSON)
			.headers(h -> headers.forEach(h::set))
			.bodyValue(body)
			.retrieve()
			.bodyToMono(Map.class)
			.block(timeout), path);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> unwrap(Map<String, Object> resp, String path) {
		if (resp == null) {
			throw new IllegalStateException("向量推荐上游无响应: " + path);
		}
		Object code = resp.get("code");
		if (code instanceof Number n && n.intValue() != 200) {
			// 503=embedding未配置/索引未构建，400=缺租户——一律视为"向量通道不可用"降级
			log.warn("[推荐] 向量通道不可用({}): code={}, msg={}", path, code, resp.get("msg"));
			return null;
		}
		Object data = resp.get("data");
		return data instanceof Map ? (Map<String, Object>) data : null;
	}

}
