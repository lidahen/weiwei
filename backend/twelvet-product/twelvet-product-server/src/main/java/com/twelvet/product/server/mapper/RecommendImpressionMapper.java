package com.twelvet.product.server.mapper;

import com.twelvet.product.server.domain.RecommendImpression;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 推荐曝光流水 Mapper（写入 + CTR 统计）
 */
@Mapper
public interface RecommendImpressionMapper {

	/** 批量写入曝光（多值 INSERT，单语句） */
	int batchInsert(@Param("list") List<RecommendImpression> list);

	/** 近 N 天每日曝光/点击/UV（按 A/B 组拆分；点击 = 曝光后 30 分钟内同用户浏览该商品） */
	List<Map<String, Object>> selectDailyStats(@Param("days") Integer days);

	/** 近 N 天点击 Top 商品（bucket 可空过滤） */
	List<Map<String, Object>> selectTopProducts(@Param("days") Integer days, @Param("limit") Integer limit,
			@Param("bucket") Integer bucket);

	/** 清理 N 天前的曝光流水（保留策略） */
	int deleteOldImpressions(@Param("days") Integer days);

}
