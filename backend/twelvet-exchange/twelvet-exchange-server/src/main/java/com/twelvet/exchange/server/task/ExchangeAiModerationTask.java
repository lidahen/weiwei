package com.twelvet.exchange.server.task;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twelvet.exchange.server.client.ExchangeAiModerationClient;
import com.twelvet.exchange.server.mapper.ExchangeAiModerationMapper;
import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.im.server.service.IImService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 交换广场 AI 初审定时任务（docs/交换广场模块设计方案.md §4.1）
 * <p>
 * 扫描待审换物物品 → 调 ai-service /ai/moderation/content → 按风险档位处理：low 自动过（onswap +
 * publish_time）、high 自动驳回并 IM 通知主人、medium 仅标注留人工（后台 /admin/exchange/item 待审列表）。
 * <p>
 * 幂等与重试：已有一条非 error 初审记录的物品跳过；error 记录 30 分钟内不重试， 累计 3 次失败放弃转人工。单条失败不影响其余条目； Quartz
 * 线程无租户上下文， 逐条 TenantContext.set/clear（与 ProductAiModerationTask 惯例一致）。 sys_job
 * 注册：exchangeAiModeration.auditItems（每 10 分钟，job_id=121）。
 */
@Component("exchangeAiModeration")
public class ExchangeAiModerationTask {

	private static final Logger log = LoggerFactory.getLogger(ExchangeAiModerationTask.class);

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	/** 单件物品最大初审失败次数，超过后不再自动重试（转人工） */
	private static final int MAX_RETRY = 3;

	/** 上一次 error 后的静默期（毫秒），避免每 10 分钟都撞同一个故障 */
	private static final long ERROR_BACKOFF_MS = 30 * 60 * 1000L;

	private static final int MAX_IMAGES = 3;

	private static final String BIZ_TYPE = "exchange_item";

	private final ExchangeAiModerationMapper moderationMapper;

	private final IImService imService;

	private final ExchangeAiModerationClient moderationClient;

	@Value("${ai.moderation.enabled:true}")
	private boolean enabled;

	@Value("${ai.moderation.auto-action:true}")
	private boolean autoAction;

	@Value("${ai.moderation.batch-size:50}")
	private int batchSize;

	public ExchangeAiModerationTask(ExchangeAiModerationMapper moderationMapper, IImService imService,
			ExchangeAiModerationClient moderationClient) {
		this.moderationMapper = moderationMapper;
		this.imService = imService;
		this.moderationClient = moderationClient;
	}

	/**
	 * 换物物品初审（sys_job: exchangeAiModeration.auditItems）
	 */
	public void auditItems() {
		if (!enabled) {
			return;
		}
		List<Map<String, Object>> items = moderationMapper.selectAuditingItems(batchSize);
		int[] stats = new int[3]; // pass / reject / error
		for (Map<String, Object> row : items) {
			Long tenantId = asLong(row.get("tenantId"));
			if (tenantId == null) {
				continue;
			}
			TenantContext.set(tenantId);
			try {
				processItem(row, stats);
			}
			catch (Exception e) {
				log.warn("[AI初审] 换物物品 {} 处理异常: {}", row.get("itemId"), e.getMessage());
			}
			finally {
				TenantContext.clear();
			}
		}
		if (!items.isEmpty()) {
			log.info("[AI初审] 换物物品本轮 {} 条：自动通过 {}，自动驳回 {}，失败/跳过 {}", items.size(), stats[0], stats[1], stats[2]);
		}
	}

	private void processItem(Map<String, Object> row, int[] stats) {
		Long itemId = asLong(row.get("itemId"));
		Long tenantId = asLong(row.get("tenantId"));
		Long ownerId = asLong(row.get("ownerId"));
		String title = str(row.get("title"));
		// 幂等：已有结论（非 error）的不再重复审；error 记录按静默期与重试上限控制
		if (shouldSkip(itemId)) {
			return;
		}
		Map<String, Object> payload = new HashMap<>();
		payload.put("bizType", BIZ_TYPE);
		payload.put("title", title);
		payload.put("description", str(row.get("description")));
		String catName = str(row.get("catL1"));
		String catL2 = str(row.get("catL2"));
		if (!catL2.isEmpty()) {
			catName = catName + "/" + catL2;
		}
		payload.put("categoryName", catName);
		payload.put("images", parseImages(str(row.get("images"))));

		Map<String, Object> verdict = moderationClient.moderate(tenantId, payload);
		if (verdict == null) {
			stats[2]++;
			moderationMapper.insertModerationLog(tenantId, BIZ_TYPE, itemId, null, "error", null, null, null,
					"AI 服务不可用或未配置");
			return;
		}
		String risk = str(verdict.get("riskLevel"));
		String action = resolveAction(risk);
		String summary = str(verdict.get("summary"));
		moderationMapper.insertModerationLog(tenantId, BIZ_TYPE, itemId, risk, action,
				toJson(verdict.get("violations")), summary, str(verdict.get("modelName")), null);
		if ("auto_pass".equals(action)) {
			moderationMapper.approveItemAuto(itemId, tenantId);
			stats[0]++;
		}
		else if ("auto_reject".equals(action)) {
			String reason = "AI初审：" + (summary.isEmpty() ? "内容涉嫌违规" : summary);
			moderationMapper.rejectItemAuto(itemId, reason, tenantId);
			notifyOwner(tenantId, ownerId, title, reason);
			stats[1]++;
		}
	}

	/** 已有非 error 结论 → 跳过；error 记录：静默期内或超上限时跳过 */
	private boolean shouldSkip(Long itemId) {
		Map<String, Object> latest = moderationMapper.selectLatestModeration(BIZ_TYPE, itemId);
		if (latest == null) {
			return false;
		}
		if (!"error".equals(latest.get("action"))) {
			return true;
		}
		if (moderationMapper.countModerationErrors(BIZ_TYPE, itemId) >= MAX_RETRY) {
			log.info("[AI初审] 换物物品 {} 连续 {} 次失败，转人工处理", itemId, MAX_RETRY);
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

	private void notifyOwner(Long tenantId, Long ownerId, String title, String reason) {
		if (ownerId == null) {
			return;
		}
		try {
			Long senderId = moderationMapper.selectSenderUserId(tenantId);
			if (senderId == null) {
				log.warn("[AI初审] 租户 {} 未找到系统发送人，跳过 IM 通知", tenantId);
				return;
			}
			imService.sendMessage(senderId, ownerId, null, "system",
					"🔔 您的物品「" + title + "」未通过审核：" + reason + "。可在「我的物品」中查看后重新发布");
		}
		catch (Exception e) {
			log.warn("[AI初审] 通知用户 {} 失败: {}", ownerId, e.getMessage());
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
