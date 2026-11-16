package com.twelvet.runner.server.mapper;

import com.twelvet.runner.server.domain.RunnerReview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

/**
 * 互助任务评价 Mapper（twelve_runner_review）
 *
 * @author twelvet
 */
@Mapper
public interface RunnerReviewMapper {

	int insert(@Param("review") RunnerReview review, @Param("tenantId") Long tenantId);

	/**
	 * 按任务查评价（一任务一评）
	 */
	RunnerReview selectByTaskId(@Param("taskId") Long taskId, @Param("tenantId") Long tenantId);

	/**
	 * 接单人信用画像（SQL 实时聚合，不落冗余信用表）
	 * <p>
	 * 返回：completedCount / completedCount30d / acceptedCount30d / avgRating / reviewCount
	 * </p>
	 */
	Map<String, Object> selectCreditProfile(@Param("userId") Long userId, @Param("tenantId") Long tenantId);

}
