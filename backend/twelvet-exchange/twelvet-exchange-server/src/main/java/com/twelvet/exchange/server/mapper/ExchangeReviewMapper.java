package com.twelvet.exchange.server.mapper;

import com.twelvet.exchange.server.domain.ExchangeReview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 交换双向评价 Mapper（twelve_exchange_review）
 *
 * @author twelvet
 */
@Mapper
public interface ExchangeReviewMapper {

	int insert(@Param("review") ExchangeReview review, @Param("tenantId") Long tenantId);

	/**
	 * 一单一评（uk_order_reviewer 兜底并发，应用层先查防刷）
	 */
	ExchangeReview selectByOrderAndReviewer(@Param("orderId") Long orderId, @Param("reviewerId") Long reviewerId,
			@Param("tenantId") Long tenantId);

	/**
	 * 订单全部评价（详情页展示）
	 */
	List<ExchangeReview> selectByOrderId(@Param("orderId") Long orderId, @Param("tenantId") Long tenantId);

	/**
	 * 用户换物画像：被评次数 + 平均分（换物主页摘要）
	 */
	Map<String, Object> selectOwnerProfile(@Param("userId") Long userId, @Param("tenantId") Long tenantId);

	/**
	 * 信用画像：30 天履约 + 违约归因 + 评价均分（实时计算，无冗余表，docs/交换广场二期-智能匹配与信用设计.md §4.4）
	 */
	Map<String, Object> selectCreditProfile(@Param("userId") Long userId, @Param("tenantId") Long tenantId);

}
