package com.twelvet.product.server.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.product.server.service.IFavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/**
 * 收藏管理控制器 职责: HTTP参数绑定、权限校验、调用Service、包装响应 业务逻辑全部委托给FavoriteService
 *
 * @author twelvet
 */
@Tag(description = "用户收藏", name = "FavoriteController")
@RestController
@RequestMapping("/user/favorites")
public class FavoriteController extends TWTController {

	private final IFavoriteService favoriteService;

	public FavoriteController(IFavoriteService favoriteService) {
		this.favoriteService = favoriteService;
	}

	/**
	 * 我的收藏分页
	 */
	@Operation(summary = "我的收藏分页")
	@GetMapping("/page")
	public JsonResult<TableDataInfo<com.twelvet.product.api.domain.Product>> page() {
		Long userId = StpUtil.getLoginIdAsLong();
		return JsonResult.success(favoriteService.selectFavoriteProductList(userId));
	}

	/**
	 * 添加收藏
	 */
	@Operation(summary = "添加收藏")
	@PostMapping("/add")
	public JsonResult<String> add(@RequestParam Long productId) {
		Long userId = StpUtil.getLoginIdAsLong();
		return JsonResult.success(favoriteService.addFavorite(userId, productId));
	}

	/**
	 * 取消收藏
	 */
	@Operation(summary = "取消收藏")
	@DeleteMapping("/{productId}")
	public JsonResult<String> remove(@PathVariable Long productId) {
		Long userId = StpUtil.getLoginIdAsLong();
		return JsonResult.success(favoriteService.removeFavorite(userId, productId));
	}

	/**
	 * 收藏数
	 */
	@Operation(summary = "收藏数")
	@GetMapping("/count")
	public JsonResult<Long> count() {
		Long userId = StpUtil.getLoginIdAsLong();
		return JsonResult.success(favoriteService.countFavorites(userId));
	}

}
