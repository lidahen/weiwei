package com.twelvet.product.server.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 搜索关键词流水 Mapper（兴趣信号基础设施 + 搜索历史/热搜榜数据源）
 */
@Mapper
public interface SearchKeywordLogMapper {

	/** 记录一次搜索（匿名 user_id 为空） */
	int insert(@Param("tenantId") Long tenantId, @Param("userId") Long userId, @Param("keyword") String keyword);

	/** 防刷：同用户同关键词 minutes 分钟内已记录过则返回 1 */
	int selectRecentSameKeyword(@Param("userId") Long userId, @Param("keyword") String keyword,
			@Param("minutes") Integer minutes);

	/** 个人搜索历史（去重，按最近搜索时间倒序） */
	List<String> selectHistory(@Param("userId") Long userId, @Param("limit") Integer limit);

	/** 热搜榜（近 N 天租户内搜索次数倒序） */
	List<Map<String, Object>> selectHot(@Param("tenantId") Long tenantId, @Param("days") Integer days,
			@Param("limit") Integer limit);

	/** 清空个人搜索历史 */
	int deleteByUser(@Param("userId") Long userId);

	/** 清理 N 天前的搜索词流水（保留策略） */
	int deleteOldKeywords(@Param("days") Integer days);

}
