package com.twelvet.trade.server.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.framework.core.tenant.TenantSupport;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.product.api.domain.FunnelEvent;
import com.twelvet.product.api.service.IFunnelEventService;
import com.twelvet.trade.api.domain.TradeOrder;
import com.twelvet.trade.api.domain.TradeReview;
import com.twelvet.trade.server.mapper.TradeOrderMapper;
import com.twelvet.trade.server.mapper.TradeReviewMapper;
import com.twelvet.trade.server.service.ITradeReviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 交易评价Service业务层处理
 * <p>
 * 评分挂在订单上：写入前置条件是真实完成交易（订单 done），天然防刷。 校验规则镜像 RunnerReviewServiceImpl，聚合实时 AVG
 * 计算（docs/交易评价设计.md）。
 *
 * @author twelvet
 */
@Service
public class TradeReviewServiceImpl implements ITradeReviewService {

	private static final Logger log = LoggerFactory.getLogger(TradeReviewServiceImpl.class);

	private static final String ROLE_BUYER = "buyer";

	private static final String ROLE_SELLER = "seller";

	private static final int RATING_MIN = 1;

	private static final int RATING_MAX = 5;

	private static final int CONTENT_MAX = 500;

	private static final int IMAGES_MAX = 9;

	private static final int TAGS_MAX = 5;

	private static final int PAGE_SIZE_MAX = 50;

	private static final String STATUS_NORMAL = "normal";

	private static final String STATUS_HIDDEN = "hidden";

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private final TradeReviewMapper reviewMapper;

	private final TradeOrderMapper orderMapper;

	private final TenantSupport tenantSupport;

	private final IFunnelEventService funnelEventService;

	public TradeReviewServiceImpl(TradeReviewMapper reviewMapper, TradeOrderMapper orderMapper,
			TenantSupport tenantSupport, IFunnelEventService funnelEventService) {
		this.reviewMapper = reviewMapper;
		this.orderMapper = orderMapper;
		this.tenantSupport = tenantSupport;
		this.funnelEventService = funnelEventService;
	}

	/**
	 * 当前租户ID（未识别租户时为 null，XML 中 <if> 不命中——零侵入回滚）
	 */
	private Long currentTid() {
		return TenantContext.get();
	}

	/**
	 * 提交评价
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public Map<String, Object> submit(Long orderId, Long userId, Integer rating, String content, String images,
			String tags, Boolean anonymous) {
		// 防刷 1：订单存在 + 仅订单双方
		TradeOrder order = orderMapper.selectOrderDetail(orderId, currentTid());
		if (order == null) {
			throw new TWTException("订单不存在");
		}
		String role;
		Long revieweeId;
		if (userId.equals(order.getBuyerId())) {
			role = ROLE_BUYER;
			revieweeId = order.getSellerId();
		}
		else if (userId.equals(order.getSellerId())) {
			role = ROLE_SELLER;
			revieweeId = order.getBuyerId();
		}
		else {
			throw new TWTException("仅订单买卖双方可评价");
		}
		// 防刷 2：仅确认收货后可评
		if (!"done".equals(order.getStatus())) {
			throw new TWTException("仅确认收货后可评价，当前状态: " + order.getStatus());
		}
		// 防刷 3：内容合法性
		if (rating == null || rating < RATING_MIN || rating > RATING_MAX) {
			throw new TWTException("评分必须为 1-5 星");
		}
		if (content != null && content.length() > CONTENT_MAX) {
			throw new TWTException("评语最多 " + CONTENT_MAX + " 字");
		}
		validateJsonArray(images, "图片", IMAGES_MAX);
		validateJsonArray(tags, "标签", TAGS_MAX);
		// 防刷 4：一单一方向一评（DB 唯一键 uk_order_role 兜底并发）
		if (reviewMapper.selectByOrderAndRole(orderId, role, currentTid()) != null) {
			throw new TWTException("该订单已评价过");
		}

		TradeReview review = new TradeReview();
		review.setOrderId(orderId);
		review.setRole(role);
		review.setReviewerId(userId);
		review.setRevieweeId(revieweeId);
		review.setProductId(order.getProductId());
		review.setOverallScore(rating);
		review.setContent(content);
		review.setImages(images);
		review.setTags(tags);
		review.setAnonymous(Boolean.TRUE.equals(anonymous));
		review.setTenantId(tenantSupport.writeTid());
		try {
			reviewMapper.insertReview(review);
		}
		catch (DuplicateKeyException e) {
			throw new TWTException("该订单已评价过");
		}

		// 核心漏斗（spec §6.3）：提交评价
		FunnelEvent funnelEvent = new FunnelEvent();
		funnelEvent.setEventType(FunnelEvent.TYPE_REVIEW_CREATE);
		funnelEvent.setTenantId(review.getTenantId());
		funnelEvent.setUserId(userId);
		funnelEvent.setRole(role);
		funnelEvent.setProductId(review.getProductId());
		funnelEvent.setBizId(String.valueOf(review.getReviewId()));
		funnelEvent.setEventKey("review_create:" + review.getReviewId());
		funnelEventService.record(funnelEvent);

		Map<String, Object> resp = new HashMap<>();
		resp.put("reviewId", review.getReviewId());
		resp.put("role", role);
		resp.put("revieweeId", revieweeId);
		return resp;
	}

	/**
	 * JSON 数组字符串校验（条数上限；非数组视为格式错误）
	 */
	private void validateJsonArray(String json, String label, int max) {
		if (json == null || json.isEmpty()) {
			return;
		}
		try {
			JsonNode node = OBJECT_MAPPER.readTree(json);
			if (!node.isArray()) {
				throw new TWTException(label + "数据格式错误");
			}
			if (node.size() > max) {
				throw new TWTException(label + "最多 " + max + " 个");
			}
		}
		catch (TWTException e) {
			throw e;
		}
		catch (Exception e) {
			throw new TWTException(label + "数据格式错误");
		}
	}

	/**
	 * 订单评价视图
	 */
	@Override
	public Map<String, Object> orderReviewView(Long orderId, Long userId) {
		TradeOrder order = orderMapper.selectOrderDetail(orderId, currentTid());
		if (order == null) {
			return new HashMap<>();
		}
		String myRole = null;
		if (userId.equals(order.getBuyerId())) {
			myRole = ROLE_BUYER;
		}
		else if (userId.equals(order.getSellerId())) {
			myRole = ROLE_SELLER;
		}

		List<TradeReview> reviews = reviewMapper.selectByOrderId(orderId, currentTid());
		Map<String, Object> buyerReview = null;
		Map<String, Object> sellerReview = null;
		for (TradeReview review : reviews) {
			if (ROLE_BUYER.equals(review.getRole())) {
				buyerReview = reviewToMap(review);
			}
			else if (ROLE_SELLER.equals(review.getRole())) {
				sellerReview = reviewToMap(review);
			}
		}

		Map<String, Object> resp = new HashMap<>();
		resp.put("orderId", orderId);
		resp.put("myRole", myRole);
		resp.put("myReviewed",
				myRole != null && (ROLE_BUYER.equals(myRole) ? buyerReview != null : sellerReview != null));
		resp.put("buyerReview", buyerReview);
		resp.put("sellerReview", sellerReview);
		return resp;
	}

	/**
	 * 商品页评价列表
	 */
	@Override
	public Map<String, Object> listByProduct(Long productId, int pageNum, int pageSize) {
		int[] page = normalizePage(pageNum, pageSize);
		Map<String, Object> summary = reviewMapper.selectProductSummary(productId, currentTid());
		long total = summary == null ? 0 : num(summary.get("reviewCount"));

		Map<String, Object> resp = new HashMap<>();
		resp.put("rows", reviewMapper.selectPageByProduct(productId, page[0], page[1], currentTid()));
		resp.put("total", total);
		resp.put("reviewCount", total);
		resp.put("avgScore", summary == null ? null : summary.get("avgScore"));
		return resp;
	}

	/**
	 * 某用户作为卖家收到的评价列表
	 */
	@Override
	public Map<String, Object> listByUser(Long userId, int pageNum, int pageSize) {
		int[] page = normalizePage(pageNum, pageSize);
		Map<String, Object> summary = reviewMapper.selectUserSummary(userId, currentTid());
		long total = summary == null ? 0 : num(summary.get("reviewCount"));

		Map<String, Object> resp = new HashMap<>();
		resp.put("rows", reviewMapper.selectPageByReviewee(userId, page[0], page[1], currentTid()));
		resp.put("total", total);
		resp.put("reviewCount", total);
		resp.put("avgScore", summary == null ? null : summary.get("avgScore"));
		long goodCount = summary == null ? 0 : num(summary.get("goodCount"));
		resp.put("goodReviewRate", total > 0 ? Math.round(goodCount * 100.0 / total) : null);
		return resp;
	}

	/**
	 * 运营后台评价列表
	 */
	@Override
	public TableDataInfo<Map<String, Object>> adminPage(String status, String role, String keyword, int pageNum,
			int pageSize) {
		int[] page = normalizePage(pageNum, pageSize);
		List<Map<String, Object>> rows = reviewMapper.selectAdminPage(status, role, keyword, page[0], page[1]);
		long total = reviewMapper.countAdminPage(status, role, keyword);
		return TableDataInfo.page(rows, total);
	}

	/**
	 * 隐藏/恢复评价
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public String updateStatus(Long reviewId, String status) {
		if (!STATUS_NORMAL.equals(status) && !STATUS_HIDDEN.equals(status)) {
			throw new TWTException("非法状态: " + status);
		}
		int rows = reviewMapper.updateStatus(reviewId, status);
		if (rows == 0) {
			log.warn("评价状态更新失败: review={}, status={}", reviewId, status);
		}
		return "OK";
	}

	/**
	 * TradeReview -> Map（C 端输出）
	 */
	private Map<String, Object> reviewToMap(TradeReview review) {
		Map<String, Object> map = new HashMap<>();
		map.put("reviewId", review.getReviewId());
		map.put("orderId", review.getOrderId());
		map.put("role", review.getRole());
		map.put("reviewerId", review.getReviewerId());
		map.put("revieweeId", review.getRevieweeId());
		map.put("overallScore", review.getOverallScore());
		map.put("content", review.getContent());
		map.put("images", review.getImages());
		map.put("tags", review.getTags());
		map.put("anonymous", review.getAnonymous());
		map.put("createTime", review.getCreateTime());
		map.put("reviewerNickName", review.getReviewerNickName());
		map.put("reviewerAvatar", review.getReviewerAvatar());
		return map;
	}

	/**
	 * 分页参数收敛：offset + 收敛后的 pageSize
	 */
	private int[] normalizePage(int pageNum, int pageSize) {
		int size = pageSize < 1 ? 10 : Math.min(pageSize, PAGE_SIZE_MAX);
		int no = pageNum < 1 ? 1 : pageNum;
		return new int[] { (no - 1) * size, size };
	}

	private long num(Object o) {
		return o instanceof Number n ? n.longValue() : 0;
	}

}
