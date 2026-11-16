package com.twelvet.im.server.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * IM未读Mapper（im_unread 表：已读位点 + 未读计数）
 *
 * <p>
 * 未读判定口径：会话内 msg_id 大于用户已读位点(last_read_msg_id)的他人消息； unread_count 为 sendMessage
 * 同步维护的冗余计数（接收方递增、读后清零），不作为判定依据。
 * </p>
 *
 * <p>
 * 写入一律"INSERT IGNORE 兜底 + UPDATE 生效"两段式，幂等且不依赖 ON DUPLICATE KEY 的版本语法；行由 uk_user_conv
 * 保证唯一。
 * </p>
 *
 * @author twelvet
 */
@Mapper
public interface ImUnreadMapper {

	/**
	 * 兜底插入未读行（已存在则忽略），初始位点 0
	 * @param userId 用户ID
	 * @param convId 会话ID
	 * @return 影响行数
	 */
	int insertIgnoreRow(@Param("userId") Long userId, @Param("convId") Long convId);

	/**
	 * 递增未读计数（sendMessage 时对接收方调用，行由 insertIgnoreRow 保证存在）
	 * @param userId 用户ID
	 * @param convId 会话ID
	 * @return 影响行数
	 */
	int increaseUnread(@Param("userId") Long userId, @Param("convId") Long convId);

	/**
	 * 标记已读：清零计数，已读位点推进到 max(原位点, msgId)
	 * @param userId 用户ID
	 * @param convId 会话ID
	 * @param msgId 读到的消息ID
	 * @return 影响行数
	 */
	int markRead(@Param("userId") Long userId, @Param("convId") Long convId, @Param("msgId") Long msgId);

	/**
	 * 兜底插入用户全部会话的未读行（位点=会话 last_msg_id，即视为已读）
	 * @param userId 用户ID
	 * @param tenantId 租户ID(为 null 不过滤)
	 * @return 影响行数
	 */
	int insertIgnoreAllConversations(@Param("userId") Long userId, @Param("tenantId") Long tenantId);

	/**
	 * 全部会话标记已读：清零计数，位点推进到各会话 last_msg_id
	 * @param userId 用户ID
	 * @param tenantId 租户ID(为 null 不过滤)
	 * @return 影响行数
	 */
	int markAllRead(@Param("userId") Long userId, @Param("tenantId") Long tenantId);

	/**
	 * 按会话统计未读数（仅返回有未读的会话）
	 * @param userId 用户ID
	 * @param tenantId 租户ID(为 null 不过滤)
	 * @return [{convId, unread}]
	 */
	List<Map<String, Object>> selectUnreadGroupByConv(@Param("userId") Long userId, @Param("tenantId") Long tenantId);

}
