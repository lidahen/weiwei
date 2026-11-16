package com.twelvet.product.server.task;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.im.server.service.IImService;
import com.twelvet.product.server.client.AiModerationClient;
import com.twelvet.product.server.mapper.FavoriteMapper;
import com.twelvet.product.server.mapper.FeedMapper;
import com.twelvet.product.server.mapper.ProductAiModerationMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AI 初审定时任务（docs/AI初审与发布助手设计.md）
 * <p>
 * 扫描 待审商品/动态 → 调 ai-service /ai/moderation/content → 按风险档位处理： low 自动通过（auto-action
 * 开启时）、high 自动驳回并 IM 通知作者、medium 仅标注留人工。
 * <p>
 * 幂等与重试：已有一条非 error 初审记录的对象跳过；error 记录 30 分钟内不重试， 累计 3 次失败放弃转人工。单条失败不影响其余条目；Quartz
 * 线程无租户上下文， 逐条 TenantContext.set/clear（与 AI 模块社区日报任务
 * {@code com.twelvet.ai.server.service.impl.ReportServiceImpl} 惯例一致；注意与本模块同名类
 * {@code com.twelvet.product.server.service.impl.ReportServiceImpl} 不是同一个）。 sys_job 注册见
 * docs/db 基线（productAiModeration 相关 Job）。
 */
@Component("productAiModeration")
public class ProductAiModerationTask {

	private static final Logger log = LoggerFactory.getLogger(ProductAiModerationTask.class);

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	/** 单条业务对象最大初审失败次数，超过后不再自动重试（转人工） */
	private static final int MAX_RETRY = 3;

	/** 上一次 error 后的静默期（毫秒），避免每 10 分钟都撞同一个故障 */
	private static final long ERROR_BACKOFF_MS = 30 * 60 * 1000L;

	private static final int MAX_IMAGES = 3;

	private final ProductAiModerationMapper moderationMapper;

	private final FeedMapper feedMapper;

	private final FavoriteMapper favoriteMapper;

	private final IImService imService;

	private final AiModerationClient moderationClient;

	@Value("${ai.moderation.enabled:true}")
	private boolean enabled;

	@Value("${ai.moderation.auto-action:true}")
	private boolean autoAction;

	@Value("${ai.moderation.batch-size:50}")
	private int batchSize;

	public ProductAiModerationTask(ProductAiModerationMapper moderationMapper, FeedMapper feedMapper,
			FavoriteMapper favoriteMapper, IImService imService, AiModerationClient moderationClient) {
		this.moderationMapper = moderationMapper;
		this.feedMapper = feedMapper;
		this.favoriteMapper = favoriteMapper;
		this.imService = imService;
		this.moderationClient = moderationClient;
	}

	/**
	 * 商品初审（sys_job: productAiModeration.auditProducts）
	 */
	public void auditProducts() {
		if (!enabled) {
			return;
		}
		List<Map<String, Object>> products = moderationMapper.selectAuditingProducts(batchSize);
		int[] stats = new int[3]; // pass / reject / error
		for (Map<String, Object> row : products) {
			Long tenantId = asLong(row.get("tenantId"));
			if (tenantId == null) {
				continue;
			}
			TenantContext.set(tenantId);
			try {
				processProduct(row, stats);
			}
			catch (Exception e) {
				log.warn("[AI初审] 商品 {} 处理异常: {}", row.get("productId"), e.getMessage());
			}
			finally {
				TenantContext.clear();
			}
		}
		if (!products.isEmpty()) {
			log.info("[AI初审] 商品本轮 {} 条：自动通过 {}，自动驳回 {}，失败/跳过 {}", products.size(), stats[0], stats[1], stats[2]);
		}
	}

	/**
	 * 动态初审（sys_job: productAiModeration.auditFeeds，与商品任务错峰）
	 */
	public void auditFeeds() {
		if (!enabled) {
			return;
		}
		List<Map<String, Object>> feeds = moderationMapper.selectPendingFeedsForModeration(batchSize);
		int[] stats = new int[3];
		for (Map<String, Object> row : feeds) {
			Long tenantId = asLong(row.get("tenantId"));
			if (tenantId == null) {
				continue;
			}
			TenantContext.set(tenantId);
			try {
				processFeed(row, stats);
			}
			catch (Exception e) {
				log.warn("[AI初审] 动态 {} 处理异常: {}", row.get("feedId"), e.getMessage());
			}
			finally {
				TenantContext.clear();
			}
		}
		if (!feeds.isEmpty()) {
			log.info("[AI初审] 动态本轮 {} 条：自动通过 {}，自动驳回 {}，失败/跳过 {}", feeds.size(), stats[0], stats[1], stats[2]);
		}
	}

	private void processProduct(Map<String, Object> row, int[] stats) {
		Long productId = asLong(row.get("productId"));
		Long tenantId = asLong(row.get("tenantId"));
		Long sellerId = asLong(row.get("sellerId"));
		String title = str(row.get("title"));
		// 幂等：已有结论（非 error）的不再重复审；error 记录按静默期与重试上限控制
		if (shouldSkip("product", productId)) {
			return;
		}
		Map<String, Object> payload = new HashMap<>();
		payload.put("bizType", "product");
		payload.put("title", title);
		payload.put("description", str(row.get("description")));
		String catName = str(row.get("catL1Name"));
		String catL2Name = str(row.get("catL2Name"));
		if (!catL2Name.isEmpty()) {
			catName = catName + "/" + catL2Name;
		}
		payload.put("categoryName", catName);
		Object price = row.get("price");
		if (price != null) {
			payload.put("price", new java.math.BigDecimal(String.valueOf(price)));
		}
		payload.put("condition", str(row.get("condition")));
		payload.put("images", parseImages(str(row.get("images"))));

		Map<String, Object> verdict = moderationClient.moderate(tenantId, payload);
		if (verdict == null) {
			stats[2]++;
			moderationMapper.insertModerationLog(tenantId, "product", productId, null, "error", null, null, null,
					"AI 服务不可用或未配置");
			return;
		}
		String risk = str(verdict.get("riskLevel"));
		String action = resolveAction(risk);
		String summary = str(verdict.get("summary"));
		moderationMapper.insertModerationLog(tenantId, "product", productId, risk, action,
				toJson(verdict.get("violations")), summary, str(verdict.get("modelName")), null);
		if ("auto_pass".equals(action)) {
			moderationMapper.approveProductAuto(productId, tenantId);
			moderationMapper.insertAiAuditLog(productId, "pass", "ai_auto", summary);
			stats[0]++;
		}
		else if ("auto_reject".equals(action)) {
			String reason = "AI初审：" + (summary.isEmpty() ? "内容涉嫌违规" : summary);
			moderationMapper.rejectProductAuto(productId, reason, tenantId);
			moderationMapper.insertAiAuditLog(productId, "reject", "ai_auto_reject", summary);
			notifyAuthor(tenantId, sellerId, "🔔 你的商品「" + title + "」未通过审核：" + reason + "。可在「我的发布」中编辑后重新提交。");
			stats[1]++;
		}
	}

	private void processFeed(Map<String, Object> row, int[] stats) {
		Long feedId = asLong(row.get("feedId"));
		Long tenantId = asLong(row.get("tenantId"));
		Long userId = asLong(row.get("userId"));
		String content = str(row.get("content"));
		if (shouldSkip("post", feedId)) {
			return;
		}
		List<String> images = parseImages(str(row.get("images")));
		if (content.isEmpty() && images.isEmpty()) {
			return; // 空帖无可审内容，直接留人工
		}
		Map<String, Object> payload = new HashMap<>();
		payload.put("bizType", "post");
		payload.put("description", content);
		payload.put("images", images);

		Map<String, Object> verdict = moderationClient.moderate(tenantId, payload);
		if (verdict == null) {
			stats[2]++;
			moderationMapper.insertModerationLog(tenantId, "post", feedId, null, "error", null, null, null,
					"AI 服务不可用或未配置");
			return;
		}
		String risk = str(verdict.get("riskLevel"));
		String action = resolveAction(risk);
		String summary = str(verdict.get("summary"));
		moderationMapper.insertModerationLog(tenantId, "post", feedId, risk, action, toJson(verdict.get("violations")),
				summary, str(verdict.get("modelName")), null);
		if ("auto_pass".equals(action)) {
			// 与人工通过一致：status 保持 normal，audit_status 置 approved
			feedApprove(feedId, tenantId);
			stats[0]++;
		}
		else if ("auto_reject".equals(action)) {
			String reason = "AI初审：" + (summary.isEmpty() ? "内容涉嫌违规" : summary);
			feedReject(feedId, reason, tenantId);
			notifyAuthor(tenantId, userId, "🔔 你的动态未通过审核：" + reason);
			stats[1]++;
		}
	}

	private void feedApprove(Long feedId, Long tenantId) {
		feedMapper.updateStatus(feedId, "normal", "approved", tenantId);
	}

	private void feedReject(Long feedId, String reason, Long tenantId) {
		feedMapper.updateStatusWithReason(feedId, "normal", "rejected", reason, tenantId);
	}

	/** 已有非 error 结论 → 跳过；error 记录：静默期内或超上限时跳过 */
	private boolean shouldSkip(String bizType, Long bizId) {
		Map<String, Object> latest = moderationMapper.selectLatestModeration(bizType, bizId);
		if (latest == null) {
			return false;
		}
		if (!"error".equals(latest.get("action"))) {
			return true;
		}
		if (moderationMapper.countModerationErrors(bizType, bizId) >= MAX_RETRY) {
			log.info("[AI初审] {} {} 连续 {} 次失败，转人工处理", bizType, bizId, MAX_RETRY);
			return true;
		}
		Object ct = latest.get("createTime");
		if (ct instanceof Date date && System.currentTimeMillis() - date.getTime() < ERROR_BACKOFF_MS) {
			return true;
		}
		return false;
	}

	/** 风险档位 → 动作（auto-action 关闭时一律只标注） */
	private String resolveAction(String risk) {
		if (!autoAction) {
			return "manual";
		}
		return switch (risk == null ? "" : risk) {
			case "low" -> "auto_pass";
			case "high" -> "auto_reject";
			default -> "manual";
		};
	}

	private void notifyAuthor(Long tenantId, Long userId, String content) {
		if (userId == null) {
			return;
		}
		try {
			Long senderId = favoriteMapper.selectSenderUserId(tenantId);
			if (senderId == null) {
				log.warn("[AI初审] 租户 {} 未找到系统发送人，跳过 IM 通知", tenantId);
				return;
			}
			imService.sendMessage(senderId, userId, null, "system", content);
		}
		catch (Exception e) {
			log.warn("[AI初审] 通知用户 {} 失败: {}", userId, e.getMessage());
		}
	}

	private List<String> parseImages(String imagesJson) {
		if (imagesJson == null || imagesJson.isEmpty()) {
			return List.of();
		}
		try {
			List<?> raw = OBJECT_MAPPER.readValue(imagesJson, List.class);
			List<String> urls = new ArrayList<>();
			for (Object o : raw) {
				if (o instanceof String s && !s.isEmpty()) {
					urls.add(s);
				}
				if (urls.size() >= MAX_IMAGES) {
					break;
				}
			}
			return urls;
		}
		catch (Exception e) {
			return List.of();
		}
	}

	private String toJson(Object o) {
		if (o == null) {
			return null;
		}
		try {
			String json = OBJECT_MAPPER.writeValueAsString(o);
			return json.length() > 1000 ? json.substring(0, 1000) : json;
		}
		catch (Exception e) {
			return null;
		}
	}

	private static Long asLong(Object o) {
		if (o == null) {
			return null;
		}
		try {
			return Long.valueOf(String.valueOf(o));
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

	private static String str(Object o) {
		return o == null ? "" : String.valueOf(o).trim();
	}

}
