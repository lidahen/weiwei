package com.twelvet.im.server.service;

import com.twelvet.im.api.domain.ImConversation;
import com.twelvet.im.api.domain.ImMessage;
import com.twelvet.im.api.domain.ImNotice;

import java.util.List;

/**
 * IM Service接口
 *
 * @author twelvet
 */
public interface IImService {

	/**
	 * 查询用户的会话列表(含对方资料、最后消息、关联商品信息)
	 * @param userId 用户ID
	 * @return 会话列表
	 */
	List<ImConversation> selectConversationList(Long userId);

	/**
	 * 查询用户资料
	 * @param userId 用户ID
	 * @return 用户信息Map
	 */
	java.util.Map<String, Object> selectUserInfo(Long userId);

	/**
	 * 确保会话存在(不存在则创建)
	 * @param userId 当前用户ID
	 * @param peerId 对方用户ID
	 * @param productId 关联商品ID
	 * @return 会话ID
	 */
	Long ensureConversation(Long userId, Long peerId, Long productId);

	/**
	 * 查询会话消息列表
	 * @param convId 会话ID
	 * @return 消息列表
	 */
	List<ImMessage> selectMessageList(Long convId);

	/**
	 * 发送消息
	 * @param userId 发送者ID
	 * @param peerId 接收者ID
	 * @param productId 关联商品ID
	 * @param type 消息类型
	 * @param content 消息内容
	 * @return 消息ID和会话ID
	 */
	java.util.Map<String, Object> sendMessage(Long userId, Long peerId, Long productId, String type, String content);

	/**
	 * 查询用户通知列表
	 * @param userId 用户ID
	 * @return 通知列表
	 */
	List<ImNotice> selectNoticeList(Long userId);

	/**
	 * 写一条站内通知（im_notice）
	 * <p>
	 * 不加事务注解：本方法在举报处置事务内被调用，若自带事务，抛异常会把共享事务标 rollback-only， 外层提交时抛
	 * UnexpectedRollbackException —— 与「通知失败不影响处理」正好相反。 异常向外抛，由调用方 try/catch 决定是否吞掉。
	 * </p>
	 * @param userId 收件人ID（为 null 直接跳过）
	 * @param type 通知类型（order/platform/interact）
	 * @param title 标题
	 * @param content 内容
	 */
	void sendNotice(Long userId, String type, String title, String content);

	/**
	 * 未读汇总：当前用户全部会话的未读总数 + 按会话分布 口径：会话内 msg_id 大于用户已读位点(im_unread.last_read_msg_id)的他人消息
	 * @param userId 用户ID
	 * @return {total: 未读总数, convs: [{convId, unread}]}（仅含有未读的会话）
	 */
	java.util.Map<String, Object> selectUnreadSummary(Long userId);

	/**
	 * 标记单个会话已读（已读位点推进到 messageId，缺省为会话最后一条消息）
	 * @param userId 用户ID
	 * @param convId 会话ID
	 * @param messageId 读到的消息ID(可空)
	 * @return {total: 标记后的剩余未读总数}
	 */
	java.util.Map<String, Object> markConversationRead(Long userId, Long convId, Long messageId);

	/**
	 * 全部会话标记已读（各会话位点推进到最后一条消息）
	 * @param userId 用户ID
	 * @return {total: 标记后的剩余未读总数}
	 */
	java.util.Map<String, Object> markAllConversationRead(Long userId);

}
