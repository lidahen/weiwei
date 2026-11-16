package com.twelvet.trade.server.mapper;

import com.twelvet.trade.api.domain.TradeReview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 交易评价Mapper
 *
 * @author twelvet
 */
@Mapper
public interface TradeReviewMapper {

	/**
	 * 插入评价（tenantId 取自实体属性，status 固定 normal）
	 * @param review 评价实体
	 * @return 影响行数
	 */
	int insertReview(TradeReview review);

	/**
	 * 按订单+方向查询单条评价
	 * @param orderId 订单ID
	 * @param role buyer/seller
	 * @param tenantId 租户ID(为 null 不过滤)
	 * @return 评价
	 */
	TradeReview selectByOrderAndRole(@Param("orderId") Long orderId, @Param("role") String role,
			@Param("tenantId") Long tenantId);

	/**
	 * 查询订单的全部评价(双向)
	 * @param orderId 订单ID
	 * @param tenantId 租户ID(为 null 不过滤)
	 * @return 评价列表
	 */
	List<TradeReview> selectByOrderId(@Param("orderId") Long orderId, @Param("tenantId") Long tenantId);

	/**
	 * 商品页评价列表(买家评卖家、未隐藏，匿名脱敏)
	 * @param productId 商品ID
	 * @param offset 分页偏移
	 * @param pageSize 每页条数
	 * @param tenantId 租户ID(为 null 不过滤)
	 * @return 评价列表
	 */
	List<TradeReview> selectPageByProduct(@Param("productId") Long productId, @Param("offset") int offset,
			@Param("pageSize") int pageSize, @Param("tenantId") Long tenantId);

	/**
	 * 商品页评价汇总(数量 + 均分，reviewCount 兼作分页 total)
	 * @param productId 商品ID
	 * @param tenantId 租户ID(为 null 不过滤)
	 * @return reviewCount/avgScore
	 */
	Map<String, Object> selectProductSummary(@Param("productId") Long productId, @Param("tenantId") Long tenantId);

	/**
	 * 某用户作为卖家收到的评价列表(匿名脱敏)
	 * @param revieweeId 被评价人ID
	 * @param offset 分页偏移
	 * @param pageSize 每页条数
	 * @param tenantId 租户ID(为 null 不过滤)
	 * @return 评价列表
	 */
	List<TradeReview> selectPageByReviewee(@Param("revieweeId") Long revieweeId, @Param("offset") int offset,
			@Param("pageSize") int pageSize, @Param("tenantId") Long tenantId);

	/**
	 * 用户作为卖家的评分汇总(实时聚合,不加冗余表)
	 * @param revieweeId 被评价人ID
	 * @param tenantId 租户ID(为 null 不过滤)
	 * @return reviewCount/avgScore/goodCount
	 */
	Map<String, Object> selectUserSummary(@Param("revieweeId") Long revieweeId, @Param("tenantId") Long tenantId);

	/**
	 * 运营后台评价列表(平台视角,不过滤租户,不脱敏)
	 * @param status normal/hidden,为空查全部
	 * @param role buyer/seller,为空查全部
	 * @param keyword 匹配评语/订单号/评价人昵称
	 * @param offset 分页偏移
	 * @param pageSize 每页条数
	 * @return 评价列表(含订单号/商品标题/被评价人昵称)
	 */
	List<Map<String, Object>> selectAdminPage(@Param("status") String status, @Param("role") String role,
			@Param("keyword") String keyword, @Param("offset") int offset, @Param("pageSize") int pageSize);

	/**
	 * 运营后台评价总数
	 * @param status normal/hidden,为空查全部
	 * @param role buyer/seller,为空查全部
	 * @param keyword 匹配评语/订单号/评价人昵称
	 * @return 总数
	 */
	long countAdminPage(@Param("status") String status, @Param("role") String role, @Param("keyword") String keyword);

	/**
	 * 运营隐藏/恢复评价
	 * @param reviewId 评价ID
	 * @param status normal/hidden
	 * @return 影响行数
	 */
	int updateStatus(@Param("reviewId") Long reviewId, @Param("status") String status);

}
