package com.twelvet.product.server.service.impl;

import com.twelvet.product.server.service.ICategoryService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品分类服务实现
 *
 * @author twelvet
 */
@Service
public class CategoryServiceImpl implements ICategoryService {

	private final JdbcTemplate jdbcTemplate;

	public CategoryServiceImpl(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	private static final List<String> CITIES = java.util.Arrays.asList("北京", "上海", "广州", "深圳", "杭州", "成都", "南京", "武汉",
			"西安", "天津");

	@Override
	public List<Map<String, Object>> selectCategoryTree() {
		List<Map<String, Object>> all = jdbcTemplate.queryForList(
				"SELECT code, parent_code as parentCode, level, name, sort FROM product_category_dict ORDER BY sort");
		return buildCategoryTree(all);
	}

	@Override
	public List<Map<String, Object>> selectFlatCategory() {
		return jdbcTemplate.queryForList(
				"SELECT code, parent_code as parentCode, level, name FROM product_category_dict WHERE level=1 ORDER BY sort");
	}

	@Override
	public List<String> selectCities() {
		return CITIES;
	}

	@Override
	public List<Map<String, Object>> selectExpress() {
		return jdbcTemplate.queryForList("SELECT code, name FROM express_company_dict ORDER BY sort");
	}

	/**
	 * 将扁平品类列表构建为树形结构
	 */
	private List<Map<String, Object>> buildCategoryTree(List<Map<String, Object>> all) {
		List<Map<String, Object>> roots = new ArrayList<>();
		Map<String, Map<String, Object>> map = new HashMap<>();
		for (Map<String, Object> n : all) {
			n.put("children", new ArrayList<>());
			map.put((String) n.get("code"), n);
		}
		for (Map<String, Object> n : all) {
			String parent = (String) n.get("parentCode");
			if (parent == null || parent.isEmpty()) {
				roots.add(n);
			}
			else {
				Map<String, Object> p = map.get(parent);
				if (p != null)
					((List<Map<String, Object>>) p.get("children")).add(n);
			}
		}
		return roots;
	}

}
