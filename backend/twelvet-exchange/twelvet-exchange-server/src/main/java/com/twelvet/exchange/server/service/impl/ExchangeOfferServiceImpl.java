package com.twelvet.exchange.server.service.impl;

import com.twelvet.exchange.api.domain.ExchangeItem;
import com.twelvet.exchange.api.domain.ExchangeOffer;
import com.twelvet.exchange.api.domain.ExchangeOrder;
import com.twelvet.exchange.server.domain.ExchangeOrderLog;
import com.twelvet.exchange.server.mapper.ExchangeItemMapper;
import com.twelvet.exchange.server.mapper.ExchangeOfferMapper;
import com.twelvet.exchange.server.service.IExchangeOfferService;
import com.twelvet.exchange.server.service.IExchangeReviewService;
import com.twelvet.framework.core.exception.TWTException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 换物意向服务实现
 *
 * @author twelvet
 */
@Service
public class ExchangeOfferServiceImpl implements IExchangeOfferService {

	private static final Logger log = LoggerFactory.getLogger(ExchangeOfferServiceImpl.class);

	/** 发起意向最低信用分（docs/交换广场二期-智能匹配与信用设计.md §4.4，二期常量、三期配置化） */
	public static final int MIN_CREDIT_TO_OFFER = 40;

	private final ExchangeOfferMapper offerMapper;

	private final ExchangeItemMapper itemMapper;

	private final IExchangeReviewService reviewService;

	private final ExchangeOrderStateMachine stateMachine;

	public ExchangeOfferServiceImpl(ExchangeOfferMapper offerMapper, ExchangeItemMapper itemMapper,
			IExchangeReviewService reviewService, ExchangeOrderStateMachine stateMachine) {
		this.offerMapper = offerMapper;
		this.itemMapper = itemMapper;
		this.reviewService = reviewService;
		this.stateMachine = stateMachine;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Long create(ExchangeOffer offer, Long offerorId) {
		if (offer == null || offer.getItemId() == null) {
			throw new TWTException("意向内容不能为空");
		}
		// 信用门槛：低信用用户不可发起意向（无数据新人默认 70 分不受影响，docs §4.4）
		Map<String, Object> credit = reviewService.creditProfile(offerorId);
		int creditScore = credit.get("creditScore") instanceof Number n ? n.intValue() : 0;
		if (creditScore < MIN_CREDIT_TO_OFFER) {
			throw new TWTException("您的换物信用分低于 " + MIN_CREDIT_TO_OFFER + "，暂不能发起换物意向");
		}
		Long tid = stateMachine.currentTid();
		ExchangeItem item = itemMapper.selectById(offer.getItemId(), tid);
		if (item == null) {
			throw new TWTException("物品不存在");
		}
		// 反作弊：不能对自己的物品发起意向（docs/交换广场模块设计方案.md §8.4）
		if (offerorId.equals(item.getOwnerId())) {
			throw new TWTException("不能对自己的物品发起换物意向");
		}
		// 被锁定/下架物品不可发起
		if (!ExchangeItem.STATUS_ONSWAP.equals(item.getStatus())) {
			throw new TWTException("物品当前不可发起换物意向: " + item.getStatus());
		}
		// offer_item_id 或 offer_snapshot 二选一
		if (offer.getOfferItemId() == null
				&& (offer.getOfferSnapshot() == null || offer.getOfferSnapshot().isBlank())) {
			throw new TWTException("请选择一件自己的物品或填写物品描述（二选一）");
		}
		if (offer.getOfferItemId() != null) {
			ExchangeItem myItem = itemMapper.selectById(offer.getOfferItemId(), tid);
			if (myItem == null || !offerorId.equals(myItem.getOwnerId())) {
				throw new TWTException("选择的交换物品不存在或不属于您");
			}
			if (!ExchangeItem.STATUS_ONSWAP.equals(myItem.getStatus())) {
				throw new TWTException("您选择的物品已下架或锁定，请换一件");
			}
		}
		// 同一物品同一人仅一个 pending 意向（前置友好提示；真正的并发防护由 insertIfNoPending 原子插入兜底）
		if (offerMapper.countPendingByItemAndOfferor(offer.getItemId(), offerorId, tid) > 0) {
			throw new TWTException("您对该物品已有一条待处理意向");
		}

		offer.setOwnerId(item.getOwnerId());
		offer.setOfferorId(offerorId);
		offer.setStatus(ExchangeOffer.STATUS_PENDING);
		// 意向 72h 未处理自动 expired（docs/交换广场模块设计方案.md §4.2）
		offer.setExpireTime(LocalDateTime.now().plusHours(ExchangeOffer.EXPIRE_HOURS));
		offer.setTenantId(stateMachine.writeTid());
		if (offerMapper.insertIfNoPending(offer) == 0) {
			throw new TWTException("您对该物品已有一条待处理意向");
		}
		// 收到的意向数 +1（热度加权）
		itemMapper.incrementOfferCount(offer.getItemId(), tid);

		// IM 通知物品主人 A（docs/交换广场模块设计方案.md §8.3）
		stateMachine.publishOfferNotify(offer.getId(), ExchangeOrderLog.ACTION_OFFER_RECEIVED, offerorId, tid);
		return offer.getId();
	}

	@Override
	public List<ExchangeOffer> received(Long userId) {
		return offerMapper.selectReceived(userId, stateMachine.currentTid(), 50);
	}

	@Override
	public List<ExchangeOffer> sent(Long userId) {
		return offerMapper.selectSent(userId, stateMachine.currentTid(), 50);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Long accept(Long offerId, Long userId) {
		Long tid = stateMachine.currentTid();
		ExchangeOffer offer = offerMapper.selectById(offerId, tid);
		if (offer == null) {
			throw new TWTException("意向不存在");
		}
		if (!userId.equals(offer.getOwnerId())) {
			throw new TWTException("仅物品主人可接受意向");
		}
		if (!ExchangeOffer.STATUS_PENDING.equals(offer.getStatus())) {
			throw new TWTException("该意向已处理: " + offer.getStatus());
		}
		if (offer.getOfferItemId() == null) {
			// item_b_id 非空约束：快照意向无法直接建单，须对方先发布正式物品再成交（docs §十一待确认项）
			throw new TWTException("该意向未关联正式物品，请对方发布物品后再接受");
		}
		ExchangeItem itemA = itemMapper.selectById(offer.getItemId(), tid);
		ExchangeItem itemB = itemMapper.selectById(offer.getOfferItemId(), tid);
		if (itemA == null || itemB == null) {
			throw new TWTException("交换物品不存在");
		}
		ExchangeOrder order = stateMachine.doAccept(offer, itemA, itemB, userId);
		return order.getId();
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void reject(Long offerId, Long userId) {
		Long tid = stateMachine.currentTid();
		ExchangeOffer offer = offerMapper.selectById(offerId, tid);
		if (offer == null) {
			throw new TWTException("意向不存在");
		}
		if (!userId.equals(offer.getOwnerId())) {
			throw new TWTException("仅物品主人可婉拒意向");
		}
		if (offerMapper.rejectAtomically(offerId, userId, tid) == 0) {
			throw new TWTException("婉拒失败（意向已被处理）");
		}
		stateMachine.publishOfferNotify(offerId, ExchangeOrderLog.ACTION_OFFER_REJECTED, userId, tid);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void withdraw(Long offerId, Long userId) {
		Long tid = stateMachine.currentTid();
		ExchangeOffer offer = offerMapper.selectById(offerId, tid);
		if (offer == null) {
			throw new TWTException("意向不存在");
		}
		if (!userId.equals(offer.getOfferorId())) {
			throw new TWTException("仅意向发起人可撤回");
		}
		if (offerMapper.withdrawAtomically(offerId, userId, tid) == 0) {
			throw new TWTException("撤回失败（意向已被处理）");
		}
		stateMachine.publishOfferNotify(offerId, ExchangeOrderLog.ACTION_OFFER_WITHDRAWN, userId, tid);
	}

	@Override
	public int scanTimeoutOffer() {
		List<ExchangeOffer> expired = offerMapper.selectExpiredPending(LocalDateTime.now(), 200);
		int count = 0;
		for (ExchangeOffer offer : expired) {
			Long tid = offer.getTenantId();
			if (offerMapper.expireAtomically(offer.getId(), tid) == 0) {
				continue;
			}
			count++;
			// IM 通知发起人（operator=0 走租户系统发送人，docs/交换广场模块设计方案.md §8.3）
			stateMachine.publishOfferNotify(offer.getId(), ExchangeOrderLog.ACTION_OFFER_EXPIRED, 0L, tid);
		}
		return count;
	}

}
