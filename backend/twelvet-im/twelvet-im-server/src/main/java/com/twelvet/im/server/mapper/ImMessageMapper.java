package com.twelvet.im.server.mapper;

import com.twelvet.im.api.domain.ImMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 消息Mapper
 *
 * @author twelvet
 */
@Mapper
public interface ImMessageMapper {

	/**
	 * 查询会话消息列表
	 * @param convId 会话ID
	 * @param limit 限制数量
	 * @param tenantId 租户ID(为 null 不过滤)
	 * @return 消息列表
	 */
	List<ImMessage> selectMessageListByConvId(@Param("convId") Long convId, @Param("limit") int limit,
			@Param("tenantId") Long tenantId);

	/**
	 * 新增消息
	 * @param message 消息信息
	 * @return 结果
	 */
	int insertMessage(ImMessage message);

}
