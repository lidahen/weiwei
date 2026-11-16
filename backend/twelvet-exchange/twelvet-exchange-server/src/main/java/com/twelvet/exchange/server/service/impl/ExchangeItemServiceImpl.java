package com.twelvet.exchange.server.service.impl;

import com.twelvet.exchange.api.domain.ExchangeItem;
import com.twelvet.exchange.api.domain.ExchangeOffer;
import com.twelvet.exchange.server.domain.ExchangeOrderLog;
import com.twelvet.exchange.server.mapper.ExchangeCategoryMapper;
import com.twelvet.exchange.server.mapper.ExchangeItemMapper;
import com.twelvet.exchange.server.mapper.ExchangeOfferMapper;
import com.twelvet.exchange.server.mapper.ExchangeOrderMapper;
import com.twelvet.exchange.server.mapper.ExchangeReviewMapper;
import com.twelvet.exchange.server.service.IExchangeItemService;
import com.twelvet.exchange.server.service.IExchangeReviewService;
import com.twelvet.exchange.server.service.impl.ExchangeOrderStateMachine;
import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.framework.RedisUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 换物物品服务实现（发布/广场/我的物品/审核）
 *
 * @author twelvet
 */
@Service
public class ExchangeItemServiceImpl implements IExchangeItemService {

	private static final Logger log = LoggerFactory.getLogger(ExchangeItemServiceImpl.class);

	private final ExchangeItemMapper itemMapper;

	private final ExchangeOfferMapper offerMapper;

	private final ExchangeOrderMapper orderMapper;

	private final ExchangeReviewMapper reviewMapper;

	private final ExchangeCategoryMapper categoryMapper;

	private final ExchangeOrderStateMachine stateMachine;

	private final IExchangeReviewService reviewService;

	public ExchangeItemServiceImpl(ExchangeItemMapper itemMapper, ExchangeOfferMapper offerMapper,
			ExchangeOrderMapper orderMapper, ExchangeReviewMapper reviewMapper, ExchangeCategoryMapper categoryMapper,
			ExchangeOrderStateMachine stateMachine, IExchangeReviewService reviewService) {
		this.itemMapper = itemMapper;
		this.offerMapper = offerMapper;
		this.orderMapper = orderMapper;
		this.reviewMapper = reviewMapper;
		this.categoryMapper = categoryMapper;
		this.stateMachine = stateMachine;
		this.reviewService = reviewService;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Long publish(ExchangeItem item, Long userId) {
		// 校验
		if (item == null) {
			throw new TWTException("物品内容不能为空");
		}
		if (item.getTitle() == null || item.getTitle().isBlank()) {
			throw new TWTException("物品标题不能为空");
		}
		if (item.getCatL1() == null || item.getCatL1().isBlank()) {
			throw new TWTException("一级类目不能为空");
		}
		if (item.getConditionLevel() == null || item.getConditionLevel().isBlank()) {
			throw new TWTException("成色不能为空");
		}
		// 交付方式默认面交（社区场景默认，docs/交换广场模块设计方案.md §8.4）
		if (item.getDeliveryMode() == null || item.getDeliveryMode().isBlank()) {
			item.setDeliveryMode(ExchangeItem.DELIVERY_FACE);
		}
		if (item.getAcceptAny() == null) {
			item.setAcceptAny(0);
		}

		item.setItemNo(generateItemNo());
		item.setOwnerId(userId);
		item.setStatus(ExchangeItem.STATUS_AUDITING);
		item.setTenantId(stateMachine.writeTid());
		item.setCreateBy(String.valueOf(userId));
		itemMapper.insert(item);
		return item.getId();
	}

	@Override
	public Map<String, Object> detail(Long itemId, Long userId) {
		Long tid = stateMachine.currentTid();
		ExchangeItem item = itemMapper.selectById(itemId, tid);
		if (item == null) {
			return null;
		}
		// 浏览数 +1（热度加权；本人查看不计数，同用户 5 分钟内去重，Redis 异常时退化为直接计数）
		if (!item.getOwnerId().equals(userId) && markViewOnce(itemId, userId)) {
			itemMapper.incrementViewCount(itemId, tid);
		}

		Map<String, Object> resp = new HashMap<>();
		resp.put("item", item);
		// 主人换物主页摘要：累计换出次数 + 被评次数/平均分（docs/交换广场模块设计方案.md §6.1）+ 信用分（二期 §4.4）
		Map<String, Object> owner = new HashMap<>();
		owner.put("userId", item.getOwnerId());
		owner.put("swapCount", itemMapper.selectOwnerSwapTotal(item.getOwnerId(), tid));
		Map<String, Object> profile = reviewMapper.selectOwnerProfile(item.getOwnerId(), tid);
		owner.put("reviewCount", profile == null ? 0 : profile.get("reviewCount"));
		owner.put("avgScore", profile == null ? null : profile.get("avgScore"));
		owner.put("ownerCredit", reviewService.creditProfile(item.getOwnerId()).get("creditScore"));
		resp.put("owner", owner);
		resp.put("offerCount", item.getOfferCount());
		return resp;
	}

	/**
	 * 浏览计数去重键。
	 *
	 * <p>
	 * 详情页已开放给游客，游客 userId 为 null。若照旧拼出 {@code exchange:view:{id}:null}，
	 * 所有游客会共用同一个键，5 分钟内只有第一个游客的浏览被计数 —— 浏览量被系统性低估。
	 * 游客没有稳定身份可去重，返回 null 表示「不去重、每次计数」。
	 * </p>
	 * @return 去重键；null 表示本次不做去重
	 */
	static String buildViewKey(Long itemId, Long userId) {
		if (userId == null) {
			return null;
		}
		return "exchange:view:" + itemId + ":" + userId;
	}

	/**
	 * 浏览计数去重：同一用户对同一物品 5 分钟内只计一次；游客不去重；Redis 不可用时退化为每次都计（不影响主流程）
	 * @return true 表示本次应计数
	 */
	private boolean markViewOnce(Long itemId, Long userId) {
		String key = buildViewKey(itemId, userId);
		if (key == null) {
			return true;
		}
		try {
			if (RedisUtils.getCacheObject(key) != null) {
				return false;
			}
			RedisUtils.setCacheObject(key, "1", 5, TimeUnit.MINUTES);
			return true;
		}
		catch (Exception e) {
			log.warn("浏览计数去重失败（退化为直接计数） itemId={}: {}", itemId, e.getMessage());
			return true;
		}
	}

	@Override
	public TableDataInfo square(Long pageNum, Long pageSize, String catL1, String conditionLevel, String deliveryMode,
			String keyword) {
		long pn = (pageNum == null || pageNum < 1) ? 1 : pageNum;
		long ps = (pageSize == null || pageSize < 1) ? 20 : Math.min(pageSize, 200);
		long offset = (pn - 1) * ps;
		String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
		Long tid = stateMachine.currentTid();
		List<ExchangeItem> list = itemMapper.selectSquarePage(tid, catL1, conditionLevel, deliveryMode, kw, offset, ps);
		long total = itemMapper.countSquarePage(tid, catL1, conditionLevel, deliveryMode, kw);
		return TableDataInfo.page(list, total);
	}

	@Override
	public List<ExchangeItem> myItems(Long userId, String status) {
		return itemMapper.selectMyItems(userId, stateMachine.currentTid(), status, 50);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public String toggleOffshelf(Long itemId, Long userId) {
		ExchangeItem item = itemMapper.selectById(itemId, stateMachine.currentTid());
		if (item == null) {
			throw new TWTException("物品不存在");
		}
		if (!userId.equals(item.getOwnerId())) {
			throw new TWTException("仅物品主人可下架/重新上架");
		}
		if (ExchangeItem.STATUS_ONSWAP.equals(item.getStatus())) {
			if (itemMapper.transitionStatus(itemId, userId, ExchangeItem.STATUS_ONSWAP, ExchangeItem.STATUS_OFFSHELF,
					stateMachine.currentTid()) == 0) {
				throw new TWTException("下架失败（状态已被其他操作改变）");
			}
			// 下架时批量关闭未决意向（docs §4.2 closed 语义），逐条 IM 通知发起人
			List<ExchangeOffer> pendings = offerMapper.selectPendingByItem(itemId, stateMachine.currentTid());
			offerMapper.closePendingByItem(itemId, stateMachine.currentTid());
			for (ExchangeOffer pending : pendings) {
				stateMachine.publishOfferNotify(pending.getId(), ExchangeOrderLog.ACTION_OFFER_CLOSED, userId,
						stateMachine.currentTid());
			}
			return ExchangeItem.STATUS_OFFSHELF;
		}
		if (ExchangeItem.STATUS_OFFSHELF.equals(item.getStatus())) {
			if (itemMapper.transitionStatus(itemId, userId, ExchangeItem.STATUS_OFFSHELF, ExchangeItem.STATUS_ONSWAP,
					stateMachine.currentTid()) == 0) {
				throw new TWTException("重新上架失败（状态已被其他操作改变）");
			}
			return ExchangeItem.STATUS_ONSWAP;
		}
		throw new TWTException("当前状态不允许下架/上架: " + item.getStatus());
	}

	@Override
	public List<Map<String, Object>> categoryTree() {
		List<Map<String, Object>> all = categoryMapper.selectCategoryTree();
		return buildCategoryTree(all);
	}

	@Override
	public TableDataInfo adminPage(Long pageNum, Long pageSize, String status, String keyword) {
		// 平台管理：全状态分页查询（tenantId 取当前上下文，为 null 时不限定租户，与 runner 现有租户处理一致）
		long pn = (pageNum == null || pageNum < 1) ? 1 : pageNum;
		long ps = (pageSize == null || pageSize < 1) ? 20 : Math.min(pageSize, 200);
		long offset = (pn - 1) * ps;
		String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
		String st = (status == null || status.isBlank()) ? null : status.trim();
		Long tid = stateMachine.currentTid();
		List<ExchangeItem> list = itemMapper.selectAdminPage(tid, st, kw, offset, ps);
		long total = itemMapper.countAdminPage(tid, st, kw);
		return TableDataInfo.page(list, total);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void adminPass(Long itemId, Long operatorId) {
		if (itemMapper.passItem(itemId, stateMachine.currentTid()) == 0) {
			throw new TWTException("物品不在审核中，无法通过");
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void adminReject(Long itemId, Long operatorId, String reason) {
		if (reason == null || reason.isBlank()) {
			throw new TWTException("驳回原因不能为空");
		}
		if (itemMapper.rejectItem(itemId, reason, stateMachine.currentTid()) == 0) {
			throw new TWTException("物品不在审核中，无法驳回");
		}
		// IM 通知主人（docs/交换广场模块设计方案.md §三：人工 reject 同样通知）
		stateMachine.publishItemNotify(itemId, ExchangeOrderLog.ACTION_ITEM_REJECTED, operatorId, reason,
				stateMachine.currentTid());
	}

	@Override
	public Map<String, Object> stat() {
		Long tid = stateMachine.currentTid();
		Map<String, Object> itemStats = itemMapper.selectStatistics(tid);
		Map<String, Object> offerStats = offerMapper.selectStatistics(tid);
		Map<String, Object> orderStats = orderMapper.selectStatistics(tid);
		List<Map<String, Object>> hotCategories = itemMapper.selectHotCategories(tid, 5);

		long totalOrders = num(orderStats.get("totalOrders"));
		long completedOrders = num(orderStats.get("completedOrders"));
		long disputedOrders = num(orderStats.get("disputedOrders"));

		Map<String, Object> resp = new HashMap<>();
		resp.put("totalItems", num(itemStats.get("totalItems")));
		resp.put("onswapCount", num(itemStats.get("onswapCount")));
		resp.put("lockedCount", num(itemStats.get("lockedCount")));
		resp.put("swappedCount", num(itemStats.get("swappedCount")));
		resp.put("sumViewCount", num(itemStats.get("sumViewCount")));
		resp.put("totalOffers", num(offerStats.get("totalOffers")));
		resp.put("pendingOffers", num(offerStats.get("pendingOffers")));
		resp.put("totalOrders", totalOrders);
		resp.put("completedOrders", completedOrders);
		resp.put("disputedOrders", disputedOrders);
		// 成交率/纠纷率（无订单时 null，前端显示 --）
		resp.put("dealRate", totalOrders > 0 ? Math.round(completedOrders * 100.0 / totalOrders) / 100.0 : null);
		resp.put("disputeRate", totalOrders > 0 ? Math.round(disputedOrders * 100.0 / totalOrders) / 100.0 : null);
		resp.put("hotCategories", hotCategories);
		return resp;
	}

	private String generateItemNo() {
		String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
		int rand = (int) (Math.random() * 1000000);
		return "ESC" + time + String.format("%06d", rand);
	}

	/**
	 * 将扁平品类列表构建为树形结构（镜像 CategoryServiceImpl.buildCategoryTree）
	 */
	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> buildCategoryTree(List<Map<String, Object>> all) {
		List<Map<String, Object>> roots = new ArrayList<>();
		Map<String, Map<String, Object>> map = new HashMap<>();
		for (Map<String, Object> n : all) {
			n.put("children", new ArrayList<>());
			map.put((String) n.get("code"), n);
		}
		for (Map<String, Object> n : all) {
			String parent = (String) n.get("parentCode");
			if (parent == null || parent.isEmpty()) {
				roots.add(n);
			}
			else {
				Map<String, Object> p = map.get(parent);
				if (p != null)
					((List<Map<String, Object>>) p.get("children")).add(n);
			}
		}
		return roots;
	}

	private long num(Object o) {
		return o instanceof Number n ? n.longValue() : 0;
	}

}
