package com.twelvet.product.server.service;

import java.util.List;
import java.util.Map;

/**
 * 商品分类服务接口
 *
 * @author twelvet
 */
public interface ICategoryService {

	/**
	 * 查询商品品类树(一级+二级)
	 * @return 品类树
	 */
	List<Map<String, Object>> selectCategoryTree();

	/**
	 * 查询扁平品类(下拉用)
	 * @return 扁平品类列表
	 */
	List<Map<String, Object>> selectFlatCategory();

	/**
	 * 查询城市列表
	 * @return 城市列表
	 */
	List<String> selectCities();

	/**
	 * 查询快递公司列表
	 * @return 快递公司列表
	 */
	List<Map<String, Object>> selectExpress();

}
