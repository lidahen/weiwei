package com.twelvet.product.server.service;

import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.product.api.domain.Product;

import java.util.List;

/**
 * 收藏Service接口
 *
 * @author twelvet
 */
public interface IFavoriteService {

	/**
	 * 查询收藏商品列表(分页)
	 * @param userId 用户ID
	 * @return 收藏商品分页列表
	 */
	TableDataInfo<Product> selectFavoriteProductList(Long userId);

	/**
	 * 添加收藏
	 * @param userId 用户ID
	 * @param productId 商品ID
	 * @return 结果
	 */
	String addFavorite(Long userId, Long productId);

	/**
	 * 取消收藏
	 * @param userId 用户ID
	 * @param productId 商品ID
	 * @return 结果
	 */
	String removeFavorite(Long userId, Long productId);

	/**
	 * 查询收藏数量
	 * @param userId 用户ID
	 * @return 收藏数量
	 */
	Long countFavorites(Long userId);

}
