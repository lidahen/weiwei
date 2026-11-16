package com.twelvet.im.server.service.impl;

import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.framework.core.tenant.TenantSupport;
import com.twelvet.product.api.domain.FunnelEvent;
import com.twelvet.product.api.service.IFunnelEventService;
import com.twelvet.im.api.domain.ImConversation;
import com.twelvet.im.api.domain.ImMessage;
import com.twelvet.im.api.domain.ImNotice;
import com.twelvet.im.server.mapper.ImConversationMapper;
import com.twelvet.im.server.mapper.ImMessageMapper;
import com.twelvet.im.server.mapper.ImNoticeMapper;
import com.twelvet.im.server.mapper.ImUnreadMapper;
import com.twelvet.im.server.mapper.ImUserMapper;
import com.twelvet.im.server.service.IImService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * IM Service业务层处理
 *
 * @author twelvet
 */
@Service
public class ImServiceImpl implements IImService {

	private static final Logger log = LoggerFactory.getLogger(ImServiceImpl.class);

	private final ImConversationMapper conversationMapper;

	private final ImMessageMapper messageMapper;

	private final ImNoticeMapper noticeMapper;

	private final ImUnreadMapper unreadMapper;

	private final ImUserMapper imUserMapper;

	private final TenantSupport tenantSupport;

	private final IFunnelEventService funnelEventService;

	public ImServiceImpl(ImConversationMapper conversationMapper, ImMessageMapper messageMapper,
			ImNoticeMapper noticeMapper, ImUnreadMapper unreadMapper, ImUserMapper imUserMapper,
			TenantSupport tenantSupport, IFunnelEventService funnelEventService) {
		this.conversationMapper = conversationMapper;
		this.messageMapper = messageMapper;
		this.noticeMapper = noticeMapper;
		this.unreadMapper = unreadMapper;
		this.imUserMapper = imUserMapper;
		this.tenantSupport = tenantSupport;
		this.funnelEventService = funnelEventService;
	}

	/**
	 * 当前租户ID（未识别租户时为 null，XML 中 <if> 不命中，过滤器不生效——零侵入回滚）
	 */
	private Long currentTid() {
		return TenantContext.get();
	}

	/**
	 * 查询用户的会话列表
	 */
	@Override
	public List<ImConversation> selectConversationList(Long userId) {
		return conversationMapper.selectConversationListByUserId(userId, currentTid());
	}

	/**
	 * 查询用户资料
	 */
	@Override
	public Map<String, Object> selectUserInfo(Long userId) {
		Map<String, Object> user = imUserMapper.selectUserBaseInfo(userId);
		if (user == null || user.isEmpty()) {
			log.warn("用户{}不存在", userId);
			return new java.util.HashMap<>();
		}
		return user;
	}

	/**
	 * 确保会话存在(不存在则创建)
	 */
	@Override
	public Long ensureConversation(Long userId, Long peerId, Long productId) {
		Long a = Math.min(userId, peerId);
		Long b = Math.max(userId, peerId);

		// 先查询是否已存在（按当前租户过滤）
		ImConversation existing = conversationMapper.selectConversationByUsers(a, b, currentTid());
		if (existing != null && existing.getConvId() != null) {
			return existing.getConvId();
		}

		// 不存在则创建（tenantId 由 TenantFieldInterceptor 从上下文自动注入，开关关闭时落 default）
		ImConversation conversation = new ImConversation();
		conversation.setUserAId(a);
		conversation.setUserBId(b);
		conversation.setRelatedProductId(productId);
		conversationMapper.insertConversation(conversation);
		// 核心漏斗（spec §6.3）：只有新建会话才记 im_start，重复进入不重复计数
		FunnelEvent funnelEvent = new FunnelEvent();
		funnelEvent.setEventType(FunnelEvent.TYPE_IM_START);
		funnelEvent.setTenantId(currentTid());
		funnelEvent.setUserId(userId);
		funnelEvent.setRole("buyer");
		funnelEvent.setProductId(productId);
		funnelEvent.setBizId(String.valueOf(conversation.getConvId()));
		funnelEvent.setEventKey("im_start:" + conversation.getConvId());
		funnelEventService.record(funnelEvent);
		return conversation.getConvId();
	}

	/**
	 * 查询会话消息列表
	 */
	@Override
	public List<ImMessage> selectMessageList(Long convId) {
		return messageMapper.selectMessageListByConvId(convId, 200, currentTid());
	}

	/**
	 * 发送消息
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public Map<String, Object> sendMessage(Long userId, Long peerId, Long productId, String type, String content) {
		if (content == null) {
			content = "";
		}
		if (type == null || type.trim().isEmpty()) {
			type = "text";
		}

		// 商业化第三轮：跨租户发送校验（关闭 tenant-isolation-2 摘要记录的"跨租户会话对端不可见"边界）
		// P2 修复：卖家可能跨租户卖货而账号绑定单一租户（第四轮 global_user_id 前的过渡形态），
		// marketplace 聊天是产品驱动的——对方在当前租户有在售商品即放行
		Long tid = currentTid();
		if (tid != null) {
			Long peerTid = imUserMapper.selectTenantIdByUserId(peerId);
			if (peerTid != null && !peerTid.equals(tid)) {
				Long onsale = imUserMapper.countOnsaleProductsInTenant(peerId, tid);
				if (onsale == null || onsale == 0) {
					throw new TWTException("对方不在当前租户，无法发送");
				}
			}
		}

		Long convId = ensureConversation(userId, peerId, productId);

		// 插入消息（非 BaseEntity，显式写租户归属；开关开启且无上下文时抛异常）
		ImMessage message = new ImMessage();
		message.setConvId(convId);
		message.setSenderId(userId);
		message.setReceiverId(peerId);
		message.setType(type);
		message.setContent(content);
		message.setTenantId(tenantSupport.writeTid());
		messageMapper.insertMessage(message);

		// 更新会话最后消息
		conversationMapper.updateLastMessage(convId, message.getMsgId(), currentTid());

		// 维护服务端未读闭环（im_unread）：接收方插入/递增；发送者清零并把已读位点推进到本条消息
		unreadMapper.insertIgnoreRow(peerId, convId);
		unreadMapper.increaseUnread(peerId, convId);
		unreadMapper.insertIgnoreRow(userId, convId);
		unreadMapper.markRead(userId, convId, message.getMsgId());

		java.util.Map<String, Object> result = new java.util.HashMap<>();
		result.put("msgId", message.getMsgId());
		result.put("convId", convId);
		return result;
	}

	/**
	 * 查询用户通知列表
	 */
	@Override
	public List<ImNotice> selectNoticeList(Long userId) {
		return noticeMapper.selectNoticeListByUserId(userId);
	}

	/**
	 * 写一条站内通知
	 * <p>
	 * 刻意不加 @Transactional：本方法在举报处置事务内被调用，若自带事务，抛异常会把共享事务标 rollback-only，外层提交时抛
	 * UnexpectedRollbackException —— 与「通知失败不影响处理」正好相反。 异常向外抛，由调用方 try/catch 决定是否吞掉。
	 * </p>
	 */
	@Override
	public void sendNotice(Long userId, String type, String title, String content) {
		if (userId == null) {
			return;
		}
		ImNotice notice = new ImNotice();
		notice.setUserId(userId);
		notice.setType(type);
		notice.setTitle(title);
		notice.setContent(content);
		notice.setIsRead(0);
		noticeMapper.insertNotice(notice);
	}

	/**
	 * 未读汇总：总数 + 按会话分布（口径：会话内 msg_id > 已读位点的他人消息，撤回不计）
	 */
	@Override
	public Map<String, Object> selectUnreadSummary(Long userId) {
		List<Map<String, Object>> convs = unreadMapper.selectUnreadGroupByConv(userId, currentTid());
		long total = 0;
		for (Map<String, Object> conv : convs) {
			Object unread = conv.get("unread");
			if (unread instanceof Number n) {
				total += n.longValue();
			}
		}
		Map<String, Object> summary = new java.util.HashMap<>();
		summary.put("total", total);
		summary.put("convs", convs);
		return summary;
	}

	/**
	 * 标记单个会话已读（校验参与者；messageId 缺省取会话最后一条，超出部分截断到位点合法范围）
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public Map<String, Object> markConversationRead(Long userId, Long convId, Long messageId) {
		ImConversation conv = conversationMapper.selectConversationByConvId(convId, currentTid());
		boolean participant = conv != null && convId != null
				&& (userId.equals(conv.getUserAId()) || userId.equals(conv.getUserBId()));
		if (!participant) {
			throw new TWTException("会话不存在");
		}

		// 读到位点：显式传 messageId 用之（上限截断为会话最后一条，防止越界位点吞掉后续消息），否则推进到最后一条
		Long lastMsgId = conv.getLastMsgId() == null ? 0L : conv.getLastMsgId();
		Long position = messageId == null ? lastMsgId : Math.min(messageId, lastMsgId);
		if (position < 0) {
			position = 0L;
		}

		// 两段式幂等写入：行不存在先兜底插入，再推进位点/清零计数
		unreadMapper.insertIgnoreRow(userId, convId);
		unreadMapper.markRead(userId, convId, position);

		Map<String, Object> result = new java.util.HashMap<>();
		result.put("convId", convId);
		result.put("total", computeUnreadTotal(userId));
		return result;
	}

	/**
	 * 全部会话标记已读（按当前租户口径，与未读数统计一致）
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public Map<String, Object> markAllConversationRead(Long userId) {
		Long tid = currentTid();
		unreadMapper.insertIgnoreAllConversations(userId, tid);
		unreadMapper.markAllRead(userId, tid);

		Map<String, Object> result = new java.util.HashMap<>();
		result.put("total", computeUnreadTotal(userId));
		return result;
	}

	/**
	 * 重新计算未读总数（标记后返回剩余值，前端免二次轮询）
	 */
	private long computeUnreadTotal(Long userId) {
		Object total = selectUnreadSummary(userId).get("total");
		return total instanceof Number n ? n.longValue() : 0L;
	}

}
