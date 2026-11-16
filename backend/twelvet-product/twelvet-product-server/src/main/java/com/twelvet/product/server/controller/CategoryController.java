package com.twelvet.product.server.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.product.server.service.ICategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 商品分类字典 职责: HTTP参数绑定、调用Service、包装响应 业务逻辑全部委托给 CategoryService
 */
@Tag(description = "商品分类", name = "CategoryController")
@RestController
@RequestMapping("/product/category")
public class CategoryController extends TWTController {

	private final ICategoryService categoryService;

	public CategoryController(ICategoryService categoryService) {
		this.categoryService = categoryService;
	}

	@SaIgnore
	@Operation(summary = "商品品类(一级+二级树形)")
	@GetMapping("/product-cat")
	public JsonResult<List<Map<String, Object>>> categoryTree() {
		return JsonResult.success(categoryService.selectCategoryTree());
	}

	@SaIgnore
	@Operation(summary = "扁平品类(下拉用)")
	@GetMapping("/cat")
	public JsonResult<List<Map<String, Object>>> flatCategory() {
		return JsonResult.success(categoryService.selectFlatCategory());
	}

	@SaIgnore
	@Operation(summary = "城市列表")
	@GetMapping("/cities")
	public JsonResult<List<String>> cities() {
		return JsonResult.success(categoryService.selectCities());
	}

	@SaIgnore
	@Operation(summary = "快递公司")
	@GetMapping("/express")
	public JsonResult<List<Map<String, Object>>> express() {
		return JsonResult.success(categoryService.selectExpress());
	}

}
