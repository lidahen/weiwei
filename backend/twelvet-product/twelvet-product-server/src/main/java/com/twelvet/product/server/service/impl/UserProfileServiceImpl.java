package com.twelvet.product.server.service.impl;

import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.product.api.domain.Product;
import com.twelvet.product.server.mapper.ProductMapper;
import com.twelvet.product.server.mapper.UserAddressMapper;
import com.twelvet.product.server.mapper.UserMapper;
import com.twelvet.product.server.mapper.UserRelationMapper;
import com.twelvet.product.server.service.IUserProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 用户资料Service业务层处理
 *
 * @author twelvet
 */
@Service
public class UserProfileServiceImpl implements IUserProfileService {

	private static final Logger log = LoggerFactory.getLogger(UserProfileServiceImpl.class);

	/** 无评价卖家的默认信用分（新人保护，与接单人口径一致） */
	private static final double DEFAULT_CREDIT_SCORE = 80;

	/** 每笔失信单扣分（近30天，与接单人口径一致） */
	private static final int PENALTY_PER_BAD_ORDER = 5;

	private final ProductMapper productMapper;

	private final UserRelationMapper userRelationMapper;

	private final UserAddressMapper userAddressMapper;

	private final UserMapper userMapper;

	public UserProfileServiceImpl(ProductMapper productMapper, UserRelationMapper userRelationMapper,
			UserAddressMapper userAddressMapper, UserMapper userMapper) {
		this.productMapper = productMapper;
		this.userRelationMapper = userRelationMapper;
		this.userAddressMapper = userAddressMapper;
		this.userMapper = userMapper;
	}

	/**
	 * 查询用户资料(聚合)
	 */
	@Override
	public Map<String, Object> selectUserProfile(Long userId, Long viewerId) {
		Map<String, Object> user = getUserInfo(userId);
		if (user.isEmpty()) {
			return user;
		}

		// 在售商品（租户隔离由 TenantContext 透传，关闭时为 null）
		List<Product> onSale = productMapper.selectBySellerId(userId, null, 100,
				com.twelvet.framework.core.tenant.TenantContext.get());
		user.put("onsaleProducts", onSale);
		user.put("onsaleCount", onSale.size());

		// 已售数量
		Long soldCount = productMapper.countSoldProducts(userId);
		user.put("soldCount", soldCount == null ? 0L : soldCount);

		// 卖家信用：好评率/完成订单数/均分 + 信用分（一次查询取全，口径见 creditProfile）
		Map<String, Object> credit = creditProfile(userId);
		user.put("goodReviewRate", credit.get("goodReviewRate"));
		user.put("completedOrders", credit.get("completedCount"));
		user.put("sellerRating", credit.get("avgRating"));
		user.put("creditScore", credit.get("creditScore"));
		user.put("creditLevel", credit.get("level"));
		// 扣分明细含个人失信记录，仅登录用户可见（匿名访客只看到分数与等级）
		if (viewerId != null) {
			user.put("credit", credit);
		}

		// 关注/粉丝数
		Long followCount = userRelationMapper.countFollows(userId);
		Long fansCount = userRelationMapper.countFans(userId);
		user.put("followCount", followCount == null ? 0L : followCount);
		user.put("fansCount", fansCount == null ? 0L : fansCount);

		// 当前查看者是否已关注
		boolean followed = false;
		if (viewerId != null && !viewerId.equals(userId)) {
			Map<String, Object> relation = userRelationMapper.selectRelation(viewerId, userId);
			followed = relation != null && relation.containsKey("user_id");
		}
		user.put("followed", followed);
		user.put("isMe", viewerId != null && viewerId.equals(userId));

		return user;
	}

	/**
	 * 卖家信用画像
	 * <p>
	 * 公式（clamp 0~100，口径对齐接单人信用分）： <pre>
	 *   base           = avgScore × 20                 // 无评价默认 80，新人保护
	 *   completionRate = 近30天已完成 / 近30天成交订单   // 近30天无成交记 1.0，中性不奖不罚
	 *   creditScore    = round(base × (0.7 + 0.3 × completionRate)) - 5 × penalizedCount30d
	 * </pre> 失信单按 order_id 去重（同一单既退款又被判纠纷只扣一次），三类明细单独返回供前端解释扣分原因。
	 * </p>
	 */
	@Override
	public Map<String, Object> creditProfile(Long userId) {
		Map<String, Object> raw = userMapper.selectSellerCreditProfile(userId, TenantContext.get());
		if (raw == null) {
			raw = new HashMap<>();
		}

		long reviewCount = num(raw.get("reviewCount"));
		long goodCount = num(raw.get("goodCount"));
		long completedCount = num(raw.get("completedCount"));
		long paidCount = num(raw.get("paidCount"));
		long completedCount30d = num(raw.get("completedCount30d"));
		long paidCount30d = num(raw.get("paidCount30d"));
		long refundCount30d = num(raw.get("refundCount30d"));
		long disputeCount30d = num(raw.get("disputeCount30d"));
		long overdueCount30d = num(raw.get("overdueCount30d"));
		long penalizedCount30d = num(raw.get("penalizedCount30d"));
		Object avgObj = raw.get("avgScore");

		Map<String, Object> resp = new LinkedHashMap<>();
		resp.put("userId", userId);
		resp.put("reviewCount", reviewCount);
		resp.put("avgRating", avgObj);
		// 好评率 = 4-5 星占比（无评价返回 null，前端兜底 100）
		resp.put("goodReviewRate", reviewCount > 0 ? Math.round(goodCount * 100.0 / reviewCount) : null);
		resp.put("completedCount", completedCount);
		resp.put("paidCount", paidCount);
		resp.put("completedCount30d", completedCount30d);
		resp.put("paidCount30d", paidCount30d);
		// 近30天无成交时返回 null（前端展示"—"），公式里按 1.0 中性处理
		resp.put("completionRate30d",
				paidCount30d > 0 ? Math.round(completedCount30d * 100.0 / paidCount30d) / 100.0 : null);
		resp.put("penalizedCount30d", penalizedCount30d);

		// 扣分明细：让卖家能看到"为什么被扣分"，而不是只看到一个分数
		Map<String, Object> penaltyDetail = new LinkedHashMap<>();
		penaltyDetail.put("refund", refundCount30d);
		penaltyDetail.put("dispute", disputeCount30d);
		penaltyDetail.put("overdue", overdueCount30d);
		resp.put("penaltyDetail", penaltyDetail);

		double base = avgObj != null ? Double.parseDouble(String.valueOf(avgObj)) * 20 : DEFAULT_CREDIT_SCORE;
		double completionRate = paidCount30d > 0 ? (double) completedCount30d / paidCount30d : 1.0;
		double rawScore = base * (0.7 + 0.3 * completionRate) - PENALTY_PER_BAD_ORDER * penalizedCount30d;
		int creditScore = (int) Math.max(0, Math.min(100, Math.round(rawScore)));

		resp.put("creditScore", creditScore);
		resp.put("level", creditLevel(creditScore));
		resp.put("hasData", avgObj != null || completedCount > 0);
		return resp;
	}

	/**
	 * 信用等级文案（分档用于前端展示与检索）
	 */
	private String creditLevel(int score) {
		if (score >= 90) {
			return "优秀";
		}
		if (score >= 80) {
			return "良好";
		}
		if (score >= 60) {
			return "一般";
		}
		return "较差";
	}

	/**
	 * 关注用户
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public String followUser(Long userId, Long targetUserId) {
		try {
			userRelationMapper.insertRelation(userId, targetUserId);
		}
		catch (Exception e) {
			// 重复关注,静默忽略
			log.debug("用户{}已关注用户{}", userId, targetUserId);
		}
		return "OK";
	}

	/**
	 * 取消关注
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public String unfollowUser(Long userId, Long targetUserId) {
		userRelationMapper.deleteRelation(userId, targetUserId);
		return "OK";
	}

	/**
	 * 查询关注/粉丝列表
	 */
	@Override
	public List<Map<String, Object>> selectFollowList(Long userId, String tab) {
		if ("fans".equals(tab)) {
			return userRelationMapper.selectFanList(userId);
		}
		return userRelationMapper.selectFollowList(userId);
	}

	/**
	 * 查询用户收货地址列表
	 */
	@Override
	public List<Map<String, Object>> selectAddressList(Long userId) {
		return userAddressMapper.selectAddressList(userId);
	}

	/**
	 * 新增收货地址
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public String saveAddress(Long userId, Map<String, Object> address) {
		Object isDefault = address.getOrDefault("isDefault", 0);
		// 如果设为默认,先取消旧默认
		if ("1".equals(String.valueOf(isDefault)) || Integer.valueOf(1).equals(isDefault)) {
			userAddressMapper.clearDefaultAddress(userId);
		}
		userAddressMapper.insertAddress(userId, String.valueOf(address.get("receiver")),
				String.valueOf(address.get("mobile")), String.valueOf(address.get("province")),
				String.valueOf(address.get("city")), String.valueOf(address.get("district")),
				String.valueOf(address.get("detail")), isDefault);
		return "OK";
	}

	/**
	 * 删除收货地址
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public String removeAddress(Long addressId, Long userId) {
		userAddressMapper.deleteAddress(addressId, userId);
		return "OK";
	}

	/**
	 * 查询卖家在售商品
	 */
	@Override
	public List<Product> selectOnSaleProducts(Long sellerId, Long excludeProductId, int limit) {
		return productMapper.selectBySellerId(sellerId, excludeProductId, limit,
				com.twelvet.framework.core.tenant.TenantContext.get());
	}

	/**
	 * 获取用户基本信息
	 */
	private Map<String, Object> getUserInfo(Long userId) {
		try {
			Map<String, Object> user = userMapper.selectUserById(userId);
			if (user == null || user.isEmpty()) {
				log.warn("用户{}不存在", userId);
				return new HashMap<>();
			}
			return user;
		}
		catch (Exception e) {
			log.warn("查询用户{}信息失败", userId, e);
			return new HashMap<>();
		}
	}

	private long num(Object o) {
		return o instanceof Number n ? n.longValue() : 0;
	}

}
