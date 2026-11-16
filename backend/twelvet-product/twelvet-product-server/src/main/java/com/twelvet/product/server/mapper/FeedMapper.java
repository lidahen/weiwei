package com.twelvet.product.server.mapper;

import com.twelvet.product.api.domain.Feed;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component("productFeedMapper")
@Mapper
public interface FeedMapper {

	List<Feed> selectStream(@Param("type") String type, @Param("userId") Long userId,
			@Param("maxHotScore") BigDecimal maxHotScore, @Param("tenantId") Long tenantId);;

	List<Feed> selectMyFeeds(@Param("userId") Long userId, @Param("visibility") String visibility,
			@Param("tenantId") Long tenantId);

	Feed selectById(@Param("feedId") Long feedId, @Param("tenantId") Long tenantId);

	List<Feed> search(@Param("keyword") String keyword, @Param("userId") Long userId, @Param("tenantId") Long tenantId);

	int insert(Feed feed);

	int update(Feed feed);

	int updateStatus(@Param("feedId") Long feedId, @Param("status") String status,
			@Param("auditStatus") String auditStatus, @Param("tenantId") Long tenantId);

	int updateStatusWithReason(@Param("feedId") Long feedId, @Param("status") String status,
			@Param("auditStatus") String auditStatus, @Param("auditReason") String auditReason,
			@Param("tenantId") Long tenantId);

	/**
	 * 带前置状态排除的更新（举报处置专用）
	 * @param feedId 动态ID
	 * @param status 目标状态
	 * @param auditStatus 目标审核状态
	 * @param excludeStatus 不允许被更新的前置状态（通常是 deleted）
	 * @param tenantId 租户ID(为 null 不过滤)
	 * @return 影响行数；0 表示已被删除或行不存在，调用方须按处置失败处理
	 */
	int updateStatusIfNot(@Param("feedId") Long feedId, @Param("status") String status,
			@Param("auditStatus") String auditStatus, @Param("excludeStatus") String excludeStatus,
			@Param("tenantId") Long tenantId);

	int deleteById(@Param("feedId") Long feedId, @Param("tenantId") Long tenantId);

	int incrementLikeCount(@Param("feedId") Long feedId, @Param("tenantId") Long tenantId);

	int incrementCommentCount(@Param("feedId") Long feedId, @Param("tenantId") Long tenantId);

	int incrementForwardCount(@Param("feedId") Long feedId, @Param("tenantId") Long tenantId);

	int decrementCommentCount(@Param("feedId") Long feedId, @Param("tenantId") Long tenantId);

	int decrementLikeCount(@Param("feedId") Long feedId, @Param("tenantId") Long tenantId);

	List<Long> selectLikedFeedIds(@Param("userId") Long userId, @Param("tenantId") Long tenantId);

	/** 检查指定用户对指定动态是否点赞 */
	int countLikeByFeedIdAndUserId(@Param("feedId") Long feedId, @Param("userId") Long userId,
			@Param("tenantId") Long tenantId);

	Feed selectRelatedProduct(@Param("relatedProductId") Long productId, @Param("tenantId") Long tenantId);

	int updateHotScore(@Param("feedId") Long feedId, @Param("hotScore") BigDecimal hotScore,
			@Param("tenantId") Long tenantId);

	/** 运营审核列表 */
	List<Feed> selectPendingFeeds();

	/**
	 * 按 ID 批量查动态（举报处理用：软删的动态也要能显示摘要，故不过滤 status）
	 * @param feedIds 动态ID列表
	 * @param tenantId 租户ID(为 null 不过滤)
	 * @return 动态列表
	 */
	List<Feed> selectByIds(@Param("feedIds") List<Long> feedIds, @Param("tenantId") Long tenantId);

}
