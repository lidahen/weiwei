package com.twelvet.product.server.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.framework.core.tenant.TenantSupport;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.framework.jdbc.web.utils.PageUtils;
import com.twelvet.product.api.domain.Feed;
import com.twelvet.product.api.domain.FeedComment;
import com.twelvet.product.server.mapper.FeedCommentMapper;
import com.twelvet.product.server.mapper.FeedLikeMapper;
import com.twelvet.product.server.mapper.FeedMapper;
import com.twelvet.product.server.mapper.UserRelationMapper;
import com.twelvet.product.server.service.IFeedService;
import com.twelvet.product.server.service.IReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service("productFeedServiceImpl")
public class FeedServiceImpl implements IFeedService {

	private static final Logger log = LoggerFactory.getLogger(FeedServiceImpl.class);

	private final FeedMapper feedMapper;

	private final FeedLikeMapper feedLikeMapper;

	private final FeedCommentMapper feedCommentMapper;

	private final UserRelationMapper userRelationMapper;

	private final TenantSupport tenantSupport;

	private final IReportService reportService;

	public FeedServiceImpl(FeedMapper feedMapper, FeedLikeMapper feedLikeMapper, FeedCommentMapper feedCommentMapper,
			UserRelationMapper userRelationMapper, TenantSupport tenantSupport, IReportService reportService) {
		this.feedMapper = feedMapper;
		this.feedLikeMapper = feedLikeMapper;
		this.feedCommentMapper = feedCommentMapper;
		this.userRelationMapper = userRelationMapper;
		this.tenantSupport = tenantSupport;
		this.reportService = reportService;
	}

	/**
	 * 当前租户ID（未识别租户时为 null，XML 中 <if> 不命中，过滤器不生效——零侵入回滚）
	 */
	private Long currentTid() {
		return TenantContext.get();
	}

	private static final String AUDIT_PENDING = "pending";

	private static final String AUDIT_APPROVED = "approved";

	private static final String AUDIT_REJECTED = "rejected";

	@Override
	public List<Feed> selectStream(String type, Long userId) {
		List<Feed> feeds = feedMapper.selectStream(type, userId, null, currentTid());
		annotateLiked(feeds);
		return feeds;
	}

	@Override
	public Feed selectDetail(Long feedId, Long currentUserId) {
		Feed feed = feedMapper.selectById(feedId, currentTid());
		if (feed == null)
			return null;
		if (!isPublic(feed) && !isSelfOrFollowed(feed, currentUserId)) {
			return null;
		}
		if (feed.getRelatedProductId() != null) {
			Feed related = feedMapper.selectRelatedProduct(feed.getRelatedProductId(), currentTid());
			if (related != null) {
				feed.setRelatedProductTitle(related.getRelatedProductTitle());
				feed.setRelatedProductCover(related.getRelatedProductCover());
				feed.setRelatedProductPrice(
						related.getRelatedProductPrice() != null ? related.getRelatedProductPrice().intValue() : null);
			}
		}
		if (currentUserId != null && StpUtil.isLogin()) {
			feed.setLiked(checkLiked(feedId, currentUserId));
		}
		List<FeedComment> comments = feedCommentMapper.selectByFeedId(feedId, currentTid());
		feed.setComments(comments);
		return feed;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Long publish(Feed feed, Long userId) {
		feed.setUserId(userId);
		feed.setStatus("normal");
		feed.setAuditStatus(AUDIT_PENDING);
		feed.setPublishTime(LocalDateTime.now());
		feed.setCreateTime(LocalDateTime.now());
		feed.setUpdateTime(LocalDateTime.now());
		feedMapper.insert(feed);
		return feed.getFeedId();
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void update(Feed feed, Long userId) {
		Feed exist = feedMapper.selectById(feed.getFeedId(), currentTid());
		if (exist == null)
			throw new TWTException("动态不存在");
		if (!exist.getUserId().equals(userId))
			throw new TWTException("无权操作");
		feed.setUpdateTime(LocalDateTime.now());
		// update 语句的 <if test="tenantId != null"> 走实体属性（BaseEntity）
		feed.setTenantId(currentTid());
		feedMapper.update(feed);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void delete(Long feedId, Long userId) {
		Feed exist = feedMapper.selectById(feedId, currentTid());
		if (exist == null)
			throw new TWTException("动态不存在");
		if (!exist.getUserId().equals(userId))
			throw new TWTException("无权操作");
		feedMapper.deleteById(feedId, currentTid());
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public String toggleLike(Long feedId, Long userId) {
		// 先确认动态存在于当前租户（跨租户 ID 枚举防护）
		Feed feed = feedMapper.selectById(feedId, currentTid());
		if (feed == null)
			throw new TWTException("动态不存在");
		Long count = feedLikeMapper.countLike(feedId, userId, currentTid());
		if (count != null && count > 0) {
			// 已点赞 → 取消点赞
			feedLikeMapper.deleteLike(feedId, userId, currentTid());
			feedMapper.decrementLikeCount(feedId, currentTid());
			updateHotScore(feedId);
			return "unliked";
		}
		// 未点赞 → 点赞
		feedLikeMapper.insertLike(feedId, userId, tenantSupport.writeTid());
		feedMapper.incrementLikeCount(feedId, currentTid());
		updateHotScore(feedId);
		return "liked";
	}

	@Override
	public int getLikeCount(Long feedId) {
		Feed feed = feedMapper.selectById(feedId, currentTid());
		return feed != null ? (feed.getLikeCount() != null ? feed.getLikeCount() : 0) : 0;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Long comment(FeedComment comment, Long userId) {
		// 先确认动态存在于当前租户（跨租户 ID 枚举防护）
		Feed feed = feedMapper.selectById(comment.getFeedId(), currentTid());
		if (feed == null)
			throw new TWTException("动态不存在");
		// 二级评论：服务端解析被回复评论归属（replyToUserId 不信任客户端传入）
		if (comment.getReplyToId() != null) {
			FeedComment target = feedCommentMapper.selectById(comment.getReplyToId(), currentTid());
			if (target == null || !target.getFeedId().equals(comment.getFeedId()))
				throw new TWTException("回复的评论不存在");
			comment.setReplyToUserId(target.getUserId());
		}
		else {
			comment.setReplyToUserId(null);
		}
		comment.setUserId(userId);
		comment.setStatus("normal");
		comment.setCreateTime(new Date());
		// 非 BaseEntity，显式写租户归属（开关开启且无上下文时抛异常）
		comment.setTenantId(tenantSupport.writeTid());
		feedCommentMapper.insert(comment);
		feedMapper.incrementCommentCount(comment.getFeedId(), currentTid());
		updateHotScore(comment.getFeedId());
		return comment.getCommentId();
	}

	@Override
	public List<FeedComment> selectComments(Long feedId) {
		return feedCommentMapper.selectByFeedId(feedId, currentTid());
	}

	@Override
	public TableDataInfo<Feed> selectMyFeeds(Long userId, String visibility) {
		PageUtils.startPage();
		List<Feed> feeds = feedMapper.selectMyFeeds(userId, visibility, currentTid());
		return PageUtils.getDataTable(feeds);
	}

	/** 运营审核列表：查询所有待审核动态 */
	@Override
	public TableDataInfo<Feed> selectPendingFeeds() {
		PageUtils.startPage();
		List<Feed> feeds = feedMapper.selectPendingFeeds();
		return PageUtils.getDataTable(feeds);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Long forward(Long feedId, Long userId) {
		Feed original = feedMapper.selectById(feedId, currentTid());
		if (original == null)
			throw new TWTException("动态不存在");
		Feed forward = new Feed();
		forward.setUserId(userId);
		forward.setContent("转发: " + original.getContent());
		forward.setImages(original.getImages());
		forward.setOriginalFeedId(feedId);
		forward.setAuditStatus(AUDIT_PENDING);
		forward.setStatus("normal");
		forward.setPublishTime(LocalDateTime.now());
		forward.setCreateTime(LocalDateTime.now());
		forward.setUpdateTime(LocalDateTime.now());
		feedMapper.insert(forward);
		feedMapper.incrementForwardCount(feedId, currentTid());
		return forward.getFeedId();
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public String report(Long feedId, Long userId, String reasonType, String reasonDetail) {
		// 旧路径保留（前端已发版），统一落到 report 表的唯一写入点
		reportService.submit("feed", feedId, userId, reasonType, reasonDetail, null);
		return "reported";
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public String audit(Long feedId, String action, String reason) {
		// 运营审核是平台视角，不过滤租户（tid 传 null，与 selectPendingFeeds 一致）
		Feed exist = feedMapper.selectById(feedId, null);
		if (exist == null)
			throw new TWTException("动态不存在");
		if ("pass".equals(action)) {
			feedMapper.updateStatus(feedId, "normal", AUDIT_APPROVED, null);
		}
		else {
			feedMapper.updateStatusWithReason(feedId, "normal", AUDIT_REJECTED, reason, null);
		}
		return action;
	}

	@Override
	public List<Feed> search(String keyword, Long userId) {
		return feedMapper.search(keyword, userId, currentTid());
	}

	// ===== Private methods =====

	private void annotateLiked(List<Feed> feeds) {
		if (feeds == null || feeds.isEmpty() || !StpUtil.isLogin())
			return;
		Long userId = StpUtil.getLoginIdAsLong();
		List<Long> likedIds = feedMapper.selectLikedFeedIds(userId, currentTid());
		java.util.Set<Long> likedSet = new java.util.HashSet<>(likedIds);
		for (Feed f : feeds) {
			f.setLiked(likedSet.contains(f.getFeedId()));
		}
	}

	private boolean isPublic(Feed feed) {
		return "public".equals(feed.getVisibility());
	}

	private boolean isSelfOrFollowed(Feed feed, Long userId) {
		if (userId == null)
			return false;
		if (feed.getUserId().equals(userId))
			return true;
		// 检查当前用户是否关注了动态作者
		Map<String, Object> relation = userRelationMapper.selectRelation(userId, feed.getUserId());
		return relation != null;
	}

	private boolean checkLiked(Long feedId, Long userId) {
		return feedMapper.countLikeByFeedIdAndUserId(feedId, userId, currentTid()) > 0;
	}

	private void updateHotScore(Long feedId) {
		Feed feed = feedMapper.selectById(feedId, currentTid());
		if (feed != null) {
			BigDecimal score = BigDecimal.valueOf((feed.getLikeCount() != null ? feed.getLikeCount() : 0) * 1
					+ (feed.getCommentCount() != null ? feed.getCommentCount() : 0) * 3);
			feedMapper.updateHotScore(feedId, score, currentTid());
		}
	}

}
