package com.twelvet.trade.server.service;

import com.twelvet.framework.jdbc.web.page.TableDataInfo;

import java.util.Map;

/**
 * 交易评价Service接口
 *
 * @author twelvet
 */
public interface ITradeReviewService {

	/**
	 * 提交评价（买卖双方互评，订单确认收货后可评）
	 * @param orderId 订单ID
	 * @param userId 当前用户(评价人)
	 * @param rating 评分 1-5
	 * @param content 评语(可空)
	 * @param images 图片 JSON 数组字符串(可空)
	 * @param tags 标签 JSON 数组字符串(可空)
	 * @param anonymous 是否匿名
	 * @return reviewId/role/revieweeId
	 */
	Map<String, Object> submit(Long orderId, Long userId, Integer rating, String content, String images, String tags,
			Boolean anonymous);

	/**
	 * 订单评价视图(评价页/订单详情用)：本人角色、是否已评、双向评价
	 * @param orderId 订单ID
	 * @param userId 当前用户
	 * @return myRole/myReviewed/buyerReview/sellerReview
	 */
	Map<String, Object> orderReviewView(Long orderId, Long userId);

	/**
	 * 商品页评价列表(买家评卖家，未隐藏)
	 * @param productId 商品ID
	 * @param pageNum 页码(1起)
	 * @param pageSize 每页条数
	 * @return rows/total/avgScore/reviewCount
	 */
	Map<String, Object> listByProduct(Long productId, int pageNum, int pageSize);

	/**
	 * 某用户作为卖家收到的评价列表
	 * @param userId 被评价人ID
	 * @param pageNum 页码(1起)
	 * @param pageSize 每页条数
	 * @return rows/total/avgScore/reviewCount/goodReviewRate
	 */
	Map<String, Object> listByUser(Long userId, int pageNum, int pageSize);

	/**
	 * 运营后台评价列表(平台视角)
	 * @param status normal/hidden,为空查全部
	 * @param role buyer/seller,为空查全部
	 * @param keyword 匹配评语/订单号/评价人昵称
	 * @param pageNum 页码(1起)
	 * @param pageSize 每页条数
	 * @return 分页结果
	 */
	TableDataInfo<Map<String, Object>> adminPage(String status, String role, String keyword, int pageNum, int pageSize);

	/**
	 * 运营隐藏/恢复评价
	 * @param reviewId 评价ID
	 * @param status normal/hidden
	 * @return OK
	 */
	String updateStatus(Long reviewId, String status);

}
