package com.twelvet.product.server.service;

import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.product.api.domain.Product;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品服务接口
 *
 * @author twelvet
 */
public interface IProductService {

	/**
	 * 查询商品列表(分页)
	 * @param status 商品状态
	 * @param catL1 一级分类
	 * @param city 城市
	 * @param keyword 关键词
	 * @param sellerId 卖家ID
	 * @param orderBy 排序
	 * @return 商品列表
	 */
	List<Product> selectProductList(String status, String catL1, String city, String keyword, Long sellerId,
			String orderBy);

	/**
	 * 查询商品详情
	 * @param productId 商品ID
	 * @return 商品
	 */
	Product selectProductById(Long productId);

	/**
	 * 卖家其他在售商品
	 * @param sellerId 卖家ID
	 * @param excludeProductId 排除的商品ID
	 * @param limit 数量限制
	 * @return 商品列表
	 */
	List<Product> selectBySellerId(Long sellerId, Long excludeProductId, Integer limit);

	/**
	 * 发布商品
	 * @param product 商品
	 * @param sellerId 卖家ID
	 * @return 商品ID
	 */
	Long publish(Product product, Long sellerId);

	/**
	 * 编辑商品
	 * @param product 商品
	 * @param sellerId 卖家ID
	 * @return 操作结果
	 */
	String updateProduct(Product product, Long sellerId);

	/**
	 * 下架商品
	 * @param productId 商品ID
	 * @param sellerId 卖家ID
	 * @return 操作结果
	 */
	String offshelf(Long productId, Long sellerId);

	/**
	 * 删除商品
	 * @param productId 商品ID
	 * @param sellerId 卖家ID
	 * @return 操作结果
	 */
	String deleteProduct(Long productId, Long sellerId);

	/**
	 * 获取草稿
	 * @param sellerId 卖家ID
	 * @return 草稿内容
	 */
	String getDraft(Long sellerId);

	/**
	 * 保存草稿
	 * @param sellerId 卖家ID
	 * @param snapshot 草稿内容
	 */
	void saveDraft(Long sellerId, String snapshot);

	/**
	 * 删除草稿
	 * @param sellerId 卖家ID
	 */
	void deleteDraft(Long sellerId);

	/**
	 * 标注列表商品的收藏状态
	 * @param list 商品列表
	 * @param userId 当前用户ID(可为空)
	 */
	void annotateFavorited(List<Product> list, Long userId);

	/**
	 * 面向 AI 导购的检索
	 * @param status 状态
	 * @param catL1 分类
	 * @param city 城市
	 * @param keyword 关键词
	 * @param priceMin 最低价
	 * @param priceMax 最高价
	 * @param condition 成色
	 * @param orderBy 排序
	 * @param limit 限制
	 * @return 商品列表
	 */
	List<Product> selectForAi(String status, String catL1, String city, String keyword, BigDecimal priceMin,
			BigDecimal priceMax, String condition, String orderBy, Integer limit);

	/**
	 * 获取卖家已售商品数
	 * @param sellerId 卖家ID
	 * @return 已售数量
	 */
	Long countSoldProducts(Long sellerId);

}
