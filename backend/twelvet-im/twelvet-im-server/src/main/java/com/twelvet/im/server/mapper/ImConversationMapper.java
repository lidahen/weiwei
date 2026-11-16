package com.twelvet.im.server.mapper;

import com.twelvet.im.api.domain.ImConversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 会话Mapper
 *
 * @author twelvet
 */
@Mapper
public interface ImConversationMapper {

	/**
	 * 查询用户的会话列表
	 * @param userId 用户ID
	 * @param tenantId 租户ID(为 null 不过滤)
	 * @return 会话列表
	 */
	List<ImConversation> selectConversationListByUserId(@Param("userId") Long userId, @Param("tenantId") Long tenantId);

	/**
	 * 根据双方用户ID查询会话
	 * @param userAId 较小用户ID
	 * @param userBId 较大用户ID
	 * @param tenantId 租户ID(为 null 不过滤)
	 * @return 会话信息
	 */
	ImConversation selectConversationByUsers(@Param("userAId") Long userAId, @Param("userBId") Long userBId,
			@Param("tenantId") Long tenantId);

	/**
	 * 根据会话ID查询会话（已读接口校验参与者/取位点用）
	 * @param convId 会话ID
	 * @param tenantId 租户ID(为 null 不过滤)
	 * @return 会话信息
	 */
	ImConversation selectConversationByConvId(@Param("convId") Long convId, @Param("tenantId") Long tenantId);

	/**
	 * 新增会话
	 * @param conversation 会话信息
	 * @return 结果
	 */
	int insertConversation(ImConversation conversation);

	/**
	 * 更新会话最后消息
	 * @param convId 会话ID
	 * @param lastMsgId 最后消息ID
	 * @param tenantId 租户ID(为 null 不过滤)
	 * @return 结果
	 */
	int updateLastMessage(@Param("convId") Long convId, @Param("lastMsgId") Long lastMsgId,
			@Param("tenantId") Long tenantId);

}
