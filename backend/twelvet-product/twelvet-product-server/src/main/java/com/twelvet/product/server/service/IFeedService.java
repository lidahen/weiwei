package com.twelvet.product.server.service;

import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.product.api.domain.Feed;
import com.twelvet.product.api.domain.FeedComment;

import java.util.List;

public interface IFeedService {

	/** Dynamic stream (latest/hot) */
	List<Feed> selectStream(String type, Long userId);

	/** Feed detail with interaction states */
	Feed selectDetail(Long feedId, Long currentUserId);

	/** Publish new feed */
	Long publish(Feed feed, Long userId);

	/** Edit feed */
	void update(Feed feed, Long userId);

	/** Delete feed (soft delete) */
	void delete(Long feedId, Long userId);

	/** Toggle like/unlike */
	String toggleLike(Long feedId, Long userId);

	/** Get like count for a feed */
	int getLikeCount(Long feedId);

	/** Post comment or reply */
	Long comment(FeedComment comment, Long userId);

	/** Get comment list for a feed */
	List<FeedComment> selectComments(Long feedId);

	/** My feeds with optional visibility filter */
	TableDataInfo<Feed> selectMyFeeds(Long userId, String visibility);

	/** 运营审核列表 */
	TableDataInfo<Feed> selectPendingFeeds();

	/** Forward a feed */
	Long forward(Long feedId, Long userId);

	/** 举报动态（委托 IReportService，落 report 表） */
	String report(Long feedId, Long userId, String reasonType, String reasonDetail);

	/** Audit (admin: pass/reject) */
	String audit(Long feedId, String action, String reason);

	/** Search feeds */
	List<Feed> search(String keyword, Long userId);

}
