package com.twelvet.exchange.server.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 换物类目字典 Mapper（product_category_dict，只读复用商品类目编码）
 *
 * @author twelvet
 */
@Mapper
public interface ExchangeCategoryMapper {

	/**
	 * 全量类目（扁平，service 内组树；product_category_dict 为平台级字典，不带租户）
	 */
	List<Map<String, Object>> selectCategoryTree();

}
