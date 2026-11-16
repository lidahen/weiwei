package com.twelvet.exchange.server.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twelvet.exchange.api.domain.ExchangeItem;
import com.twelvet.exchange.server.client.ExchangeMatchClient;
import com.twelvet.exchange.server.mapper.ExchangeAiModerationMapper;
import com.twelvet.exchange.server.mapper.ExchangeItemMapper;
import com.twelvet.exchange.server.mapper.ExchangeMatchPushMapper;
import com.twelvet.exchange.server.service.IExchangeMatchService;
import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.im.server.service.IImService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 换物向量匹配服务实现（docs/交换广场二期-智能匹配与信用设计.md §4.2/§4.3）
 * <p>
 * 向量通道是增强而非依赖：recommend/wanters 任何失败（向量服务不可用/查卡片异常）降级空列表， 绝不阻塞广场与详情主流程；
 * 推送扫描单物品失败跳过不炸整批。推送去重靠 twelve_exchange_match_push 快照 diff—— 同一需求方对同一物品只推一次。
 * </p>
 *
 * @author twelvet
 */
@Service
public class ExchangeMatchServiceImpl implements IExchangeMatchService {

	private static final Logger log = LoggerFactory.getLogger(ExchangeMatchServiceImpl.class);

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	/** 推荐位/wanters 默认条数（docs §4.2：recommend 8 / wanters 6） */
	private static final int DEFAULT_LIMIT = 8;

	/** 单次召回上限（防透传超大 limit 打爆索引检索） */
	private static final int MAX_LIMIT = 50;

	/** 推荐位取用户 onswap 物品上限（与 myItems 现有口径一致） */
	private static final int MY_ITEM_LIMIT = 50;

	/** 推送扫描分页大小 */
	private static final int SCAN_PAGE_SIZE = 200;

	/** 逐物品反向召回条数（wanters top3） */
	private static final int MATCH_WANTERS_LIMIT = 3;

	/** pushed_user_ids 列容量（VARCHAR(1000)，超长丢弃最旧记录保 JSON 合法） */
	private static final int PUSHED_JSON_MAX = 1000;

	private final ExchangeItemMapper itemMapper;

	private final ExchangeMatchPushMapper pushMapper;

	private final ExchangeAiModerationMapper moderationMapper;

	private final ExchangeMatchClient matchClient;

	private final IImService imService;

	public ExchangeMatchServiceImpl(ExchangeItemMapper itemMapper, ExchangeMatchPushMapper pushMapper,
			ExchangeAiModerationMapper moderationMapper, ExchangeMatchClient matchClient, IImService imService) {
		this.itemMapper = itemMapper;
		this.pushMapper = pushMapper;
		this.moderationMapper = moderationMapper;
		this.matchClient = matchClient;
		this.imService = imService;
	}

	@Override
	public List<ExchangeItem> recommendForMe(Long userId, int limit) {
		int n = (limit <= 0) ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
		try {
			Long tid = TenantContext.get();
			// 取用户全部 onswap 物品的 want_desc 作为需求文本（无需求文本 → 冷启动自然为空）
			List<String> wantTexts = itemMapper.selectMyItems(userId, tid, ExchangeItem.STATUS_ONSWAP, MY_ITEM_LIMIT)
				.stream()
				.map(ExchangeItem::getWantDesc)
				.filter(d -> d != null && !d.isBlank())
				.map(String::trim)
				.distinct()
				.collect(Collectors.toList());
			if (wantTexts.isEmpty()) {
				return new ArrayList<>();
			}
			return resolveHits(matchClient.forMe(wantTexts, n), tid, userId);
		}
		catch (Exception e) {
			log.warn("[换物匹配] 推荐位召回失败（降级空列表）: {}", e.getMessage());
			return new ArrayList<>();
		}
	}

	@Override
	public List<ExchangeItem> wanters(Long itemId, int limit) {
		int n = (limit <= 0) ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
		try {
			Long tid = TenantContext.get();
			ExchangeItem item = itemMapper.selectById(itemId, tid);
			if (item == null) {
				return new ArrayList<>();
			}
			// 反向召回命中的是需求侧物品（别人的 onswap 物品），点击跳对方物品详情形成"以物找人"闭环
			return resolveHits(matchClient.wanters(itemId, n), tid, item.getOwnerId());
		}
		catch (Exception e) {
			log.warn("[换物匹配] wanters 召回失败（降级空列表） itemId={}: {}", itemId, e.getMessage());
			return new ArrayList<>();
		}
	}

	@Override
	public int scanMatchPush() {
		// 1. 分页扫全租户 onswap 物品（selectOnswapAll 全局扫描，租户隔离由下游逐行以行内 tenant_id 处理）
		List<Map<String, Object>> items = new ArrayList<>();
		long offset = 0;
		while (true) {
			List<Map<String, Object>> page = itemMapper.selectOnswapAll(offset, SCAN_PAGE_SIZE);
			items.addAll(page);
			if (page.size() < SCAN_PAGE_SIZE) {
				break;
			}
			offset += SCAN_PAGE_SIZE;
		}
		if (items.isEmpty()) {
			return 0;
		}
		// Quartz 线程无租户上下文 → 按行内租户分组取系统发送人（镜像 RecommendServiceImpl.scanPriceDrop）
		Map<Long, List<Map<String, Object>>> byTenant = items.stream()
			.filter(r -> r.get("tenantId") != null)
			.collect(Collectors.groupingBy(r -> ((Number) r.get("tenantId")).longValue()));
		int pushed = 0;
		for (Map.Entry<Long, List<Map<String, Object>>> entry : byTenant.entrySet()) {
			Long senderId = moderationMapper.selectSenderUserId(entry.getKey());
			if (senderId == null) {
				log.warn("[换物匹配] 租户 {} 未找到系统发送人，跳过 {} 条", entry.getKey(), entry.getValue().size());
				continue;
			}
			for (Map<String, Object> row : entry.getValue()) {
				pushed += pushForItem(row, entry.getKey(), senderId);
			}
		}
		log.info("[换物匹配] 反向匹配扫描完成：扫描 {} 件在架物品，推送 {} 人", items.size(), pushed);
		return pushed;
	}

	/**
	 * 单物品反向匹配推送（设计文档 §4.3 第 2-5 步）：wanters top3 排除物品主人 → 快照 diff 只推新需求方 →
	 * IM 推送 → upsert 快照。单物品失败跳过不炸整批。
	 */
	private int pushForItem(Map<String, Object> row, Long tenantId, Long senderId) {
		Long itemId = asLong(row.get("itemId"));
		Long ownerId = asLong(row.get("ownerId"));
		String title = str(row.get("title"));
		if (itemId == null || ownerId == null) {
			return 0;
		}
		TenantContext.set(tenantId);
		try {
			// 2. 反向召回（排除自我需求：命中需求物品的主人等于物品主人时跳过）
			List<ExchangeMatchClient.MatchHit> hits = matchClient.wanters(itemId, MATCH_WANTERS_LIMIT);
			if (hits.isEmpty()) {
				return 0;
			}
			Map<Long, ExchangeItem> cardById = itemMapper.selectOnswapByIds(
					hits.stream().map(ExchangeMatchClient.MatchHit::itemId).distinct().collect(Collectors.toList()),
					tenantId).stream()
				.collect(Collectors.toMap(ExchangeItem::getId, Function.identity(), (a, b) -> a));
			// 每个需求方只推一次，取其第一条命中物品做文案（同一人多件需求物品同轮不重复打扰）
			Map<Long, ExchangeItem> wanterByUser = new LinkedHashMap<>();
			for (ExchangeMatchClient.MatchHit hit : hits) {
				ExchangeItem card = cardById.get(hit.itemId());
				if (card == null || card.getOwnerId() == null || ownerId.equals(card.getOwnerId())) {
					continue;
				}
				wanterByUser.putIfAbsent(card.getOwnerId(), card);
			}
			if (wanterByUser.isEmpty()) {
				return 0;
			}
			// 3. 快照 diff：只推新出现的需求方（同一人对同一物品只推一次）
			Set<Long> pushedIds = readPushedIds(pushMapper.selectByItemId(itemId, tenantId));
			List<Long> newIds = wanterByUser.keySet()
				.stream()
				.filter(uid -> !pushedIds.contains(uid))
				.collect(Collectors.toList());
			if (newIds.isEmpty()) {
				return 0;
			}
			// 4. IM 推送（单用户失败不炸整批）
			for (Long uid : newIds) {
				ExchangeItem wanterItem = wanterByUser.get(uid);
				try {
					imService.sendMessage(senderId, uid, null, "system",
							"【换物】有人想用「" + wanterItem.getTitle() + "」换您的「" + title + "」，去看看 → /exchange/detail/"
									+ wanterItem.getId());
				}
				catch (Exception e) {
					log.warn("[换物匹配] userId={} itemId={} 推送失败: {}", uid, itemId, e.getMessage());
				}
			}
			// 5. upsert 快照（合并已推 + 新推名单）
			pushMapper.upsertPush(tenantId, itemId, mergePushedIds(pushedIds, newIds));
			return newIds.size();
		}
		catch (Exception e) {
			log.warn("[换物匹配] 物品 {} 推送扫描异常（跳过）: {}", itemId, e.getMessage());
			return 0;
		}
		finally {
			TenantContext.clear();
		}
	}

	/**
	 * 命中 → onswap 物品卡片（按命中相似度顺序；过滤已下架/锁定与主人自身需求）
	 */
	private List<ExchangeItem> resolveHits(List<ExchangeMatchClient.MatchHit> hits, Long tid, Long excludeOwnerId) {
		if (hits.isEmpty()) {
			return new ArrayList<>();
		}
		Map<Long, ExchangeItem> cardById = itemMapper.selectOnswapByIds(
				hits.stream().map(ExchangeMatchClient.MatchHit::itemId).distinct().collect(Collectors.toList()), tid)
			.stream()
			.collect(Collectors.toMap(ExchangeItem::getId, Function.identity(), (a, b) -> a));
		List<ExchangeItem> cards = new ArrayList<>();
		for (ExchangeMatchClient.MatchHit hit : hits) {
			ExchangeItem card = cardById.get(hit.itemId());
			// 索引滞后：物品已下架/锁定/换出后卡片查不到 → 跳过
			if (card == null || (excludeOwnerId != null && excludeOwnerId.equals(card.getOwnerId()))) {
				continue;
			}
			cards.add(card);
		}
		return cards;
	}

	/** 快照已推名单解析（JSON 数组；快照损坏视为空，宁多推一次不漏推） */
	private Set<Long> readPushedIds(Map<String, Object> snap) {
		if (snap == null || snap.get("pushedUserIds") == null) {
			return Set.of();
		}
		try {
			Set<Long> ids = new LinkedHashSet<>();
			for (Object o : (List<?>) OBJECT_MAPPER.readValue(String.valueOf(snap.get("pushedUserIds")), List.class)) {
				Long id = asLong(o);
				if (id != null) {
					ids.add(id);
				}
			}
			return ids;
		}
		catch (Exception e) {
			return Set.of();
		}
	}

	/** 合并已推 + 新推名单；VARCHAR(1000) 容量保护：超长丢弃最旧记录（保 JSON 合法） */
	private String mergePushedIds(Set<Long> oldIds, List<Long> newIds) {
		Deque<Long> merged = new ArrayDeque<>(oldIds);
		merged.addAll(newIds);
		String json = writeIds(merged);
		while (json.length() > PUSHED_JSON_MAX && !merged.isEmpty()) {
			merged.pollFirst();
			json = writeIds(merged);
		}
		return json;
	}

	private String writeIds(Collection<Long> ids) {
		try {
			return OBJECT_MAPPER.writeValueAsString(ids);
		}
		catch (Exception e) {
			return "[]";
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
