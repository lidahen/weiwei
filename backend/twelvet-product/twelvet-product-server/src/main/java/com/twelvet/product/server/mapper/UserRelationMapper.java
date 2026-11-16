package com.twelvet.product.server.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 用户关注关系Mapper
 *
 * @author twelvet
 */
@Mapper
public interface UserRelationMapper {

	/**
	 * 查询用户关注数
	 * @param userId 用户ID
	 * @return 关注数
	 */
	Long countFollows(@Param("userId") Long userId);

	/**
	 * 查询用户粉丝数
	 * @param userId 用户ID
	 * @return 粉丝数
	 */
	Long countFans(@Param("userId") Long userId);

	/**
	 * 查询是否已关注
	 * @param userId 当前用户
	 * @param targetUserId 目标用户
	 * @return 关注关系(空表示未关注)
	 */
	Map<String, Object> selectRelation(@Param("userId") Long userId, @Param("targetUserId") Long targetUserId);

	/**
	 * 新增关注关系
	 * @param userId 当前用户
	 * @param targetUserId 目标用户
	 * @return 结果
	 */
	int insertRelation(@Param("userId") Long userId, @Param("targetUserId") Long targetUserId);

	/**
	 * 删除关注关系
	 * @param userId 当前用户
	 * @param targetUserId 目标用户
	 * @return 结果
	 */
	int deleteRelation(@Param("userId") Long userId, @Param("targetUserId") Long targetUserId);

	/**
	 * 查询用户的关注列表(含对方资料和互关状态)
	 * @param userId 用户ID
	 * @return 关注列表
	 */
	List<Map<String, Object>> selectFollowList(@Param("userId") Long userId);

	/**
	 * 查询用户关注的用户ID列表(首页关注流召回用)
	 * @param userId 用户ID
	 * @return 被关注者ID列表
	 */
	List<Long> selectFollowedIds(@Param("userId") Long userId);

	/**
	 * 查询用户的粉丝列表(含对方资料和互关状态)
	 * @param userId 用户ID
	 * @return 粉丝列表
	 */
	List<Map<String, Object>> selectFanList(@Param("userId") Long userId);

}
