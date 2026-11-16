package com.twelvet.product.server.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.stp.StpUtil;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.framework.jdbc.web.utils.PageUtils;
import com.twelvet.product.api.domain.FunnelEvent;
import com.twelvet.product.api.domain.Product;
import com.twelvet.product.api.service.IFunnelEventService;
import com.twelvet.product.server.service.IProductService;
import com.twelvet.product.server.service.IRecommendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商品 C 端接口 (浏览/搜索/详情/发布/管理) 职责: HTTP参数绑定、权限校验、调用Service、包装响应 业务逻辑全部委托给 ProductService
 */
@Tag(description = "商品 C 端接口", name = "ProductController")
@RestController
@RequestMapping("/product")
public class ProductController extends TWTController {

	private static final Logger log = LoggerFactory.getLogger(ProductController.class);

	private final IProductService productService;

	private final IRecommendService recommendService;

	private final IFunnelEventService funnelEventService;

	public ProductController(IProductService productService, IRecommendService recommendService,
			IFunnelEventService funnelEventService) {
		this.productService = productService;
		this.recommendService = recommendService;
		this.funnelEventService = funnelEventService;
	}

	@SaIgnore
	@Operation(summary = "首页 - 推荐流(三路召回+规则打分, docs/二手市场首页推荐设计.md)")
	@GetMapping("/home/recommend")
	public JsonResult<TableDataInfo<Product>> recommend(@RequestParam(required = false) String catL1,
			@RequestParam(required = false, defaultValue = "1") Integer current,
			@RequestParam(required = false, defaultValue = "20") Integer pageSize) {
		Long userId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
		try {
			return JsonResult.success(recommendService.homeRecommend(userId, catL1, current, pageSize));
		}
		catch (Exception e) {
			// 降级：推荐链路异常(如画像表未初始化)时回退时间倒序，保证首页可用
			log.warn("[推荐] 推荐流异常，降级为时间倒序: {}", e.getMessage());
			PageUtils.startPage();
			List<Product> list = productService.selectProductList("onsale", catL1, null, null, null, null);
			annotateFavorited(list);
			return JsonResult.success(PageUtils.getDataTable(list));
		}
	}

	@SaIgnore
	@Operation(summary = "首页 - 同城商品(置顶+热度排序)")
	@GetMapping("/home/nearby")
	public JsonResult<TableDataInfo<Product>> nearby(@RequestParam(defaultValue = "深圳") String city) {
		PageUtils.startPage();
		List<Product> list = productService.selectProductList("onsale", null, city, null, null, "hot_score");
		annotateFavorited(list);
		return JsonResult.success(PageUtils.getDataTable(list));
	}

	@SaIgnore
	@Operation(summary = "首页 - 关注流(关注卖家的在售商品)")
	@GetMapping("/home/follow")
	public JsonResult<TableDataInfo<Product>> follow() {
		Long userId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
		List<Product> list = recommendService.selectFollowFeed(userId);
		annotateFavorited(list);
		return JsonResult.success(PageUtils.getDataTable(list));
	}

	@SaIgnore
	@Operation(summary = "详情页 - 相似商品(向量召回,降级同类目热门)")
	@GetMapping("/similar/{id}")
	public JsonResult<List<Product>> similar(@PathVariable Long id,
			@RequestParam(required = false, defaultValue = "6") Integer limit) {
		try {
			return JsonResult.success(recommendService.selectSimilar(id, limit));
		}
		catch (Exception e) {
			log.warn("[推荐] 相似商品查询异常: {}", e.getMessage());
			return JsonResult.success(java.util.Collections.emptyList());
		}
	}

	@SaIgnore
	@Operation(summary = "搜索商品")
	@GetMapping("/search")
	public JsonResult<TableDataInfo<Product>> search(@RequestParam(required = false) String q,
			@RequestParam(required = false) String catL1, @RequestParam(required = false) String city,
			@RequestParam(required = false, defaultValue = "default") String orderBy) {
		PageUtils.startPage();
		List<Product> list = productService.selectProductList("onsale", catL1, city, q, null, orderBy);
		annotateFavorited(list);
		// 搜索关键词埋点（兴趣信号基础设施，静默失败）
		recommendService.logSearchKeyword(StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null, q);
		return JsonResult.success(PageUtils.getDataTable(list));
	}

	// ====== 个性化扩展接口（docs/二手市场首页推荐设计.md） ======

	@SaIgnore
	@Operation(summary = "个人搜索历史(匿名返回空)")
	@GetMapping("/search/history")
	public JsonResult<List<String>> searchHistory() {
		Long userId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
		return JsonResult.success(recommendService.searchHistory(userId));
	}

	@Operation(summary = "清空个人搜索历史")
	@DeleteMapping("/search/history")
	public JsonResult<String> clearSearchHistory() {
		recommendService.clearSearchHistory(StpUtil.getLoginIdAsLong());
		return JsonResult.success("OK");
	}

	@SaIgnore
	@Operation(summary = "热搜榜(近7天,匿名可看)")
	@GetMapping("/search/hot")
	public JsonResult<List<java.util.Map<String, Object>>> searchHot() {
		return JsonResult.success(recommendService.searchHot());
	}

	@Operation(summary = "新用户兴趣引导(写入类目种子画像)")
	@PostMapping("/interest/init")
	public JsonResult<String> initInterest(@RequestBody java.util.Map<String, List<String>> body) {
		Long userId = StpUtil.getLoginIdAsLong();
		List<String> cats = body == null ? null : body.get("cats");
		recommendService.initInterest(userId, cats);
		return JsonResult.success("OK");
	}

	@Operation(summary = "负反馈-不感兴趣(该商品不再推荐+类目降权)")
	@PostMapping("/interest/dislike")
	public JsonResult<String> dislike(@RequestParam Long productId) {
		Long userId = StpUtil.getLoginIdAsLong();
		recommendService.dislike(userId, productId);
		return JsonResult.success("OK");
	}

	@SaIgnore
	@Operation(summary = "商品详情")
	@GetMapping("/detail/{id}")
	public org.springframework.http.ResponseEntity<JsonResult<Product>> detail(@PathVariable Long id) {
		Product product = productService.selectProductById(id);
		// 跨租户访问或商品不存在统一返回 404（不泄露存在性，避免被 ID 枚举探测）
		if (product == null) {
			return org.springframework.http.ResponseEntity.status(404).body(JsonResult.error(404, "商品不存在或无权访问"));
		}
		Long userId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
		productService.annotateFavorited(java.util.Collections.singletonList(product), userId);
		// 浏览埋点（view_count + view_log，画像/向量seed/CTR 的数据源）
		recommendService.recordView(id, userId);
		// 核心漏斗（spec §6.3）：详情浏览
		FunnelEvent funnelEvent = new FunnelEvent();
		funnelEvent.setEventType(FunnelEvent.TYPE_PRODUCT_DETAIL_VIEW);
		funnelEvent.setUserId(userId);
		funnelEvent.setRole(userId == null ? "visitor" : "buyer");
		funnelEvent.setProductId(id);
		funnelEvent.setCategory(product.getCatL1());
		funnelEvent.setScene("detail");
		funnelEventService.record(funnelEvent);
		return org.springframework.http.ResponseEntity.ok(JsonResult.success(product));
	}

	@SaIgnore
	@Operation(summary = "卖家其他在售商品")
	@GetMapping("/seller-others")
	public JsonResult<List<Product>> sellerOthers(@RequestParam Long sellerId,
			@RequestParam(required = false) Long excludeProductId, @RequestParam(defaultValue = "6") Integer limit) {
		List<Product> list = productService.selectBySellerId(sellerId, excludeProductId, limit);
		return JsonResult.success(list);
	}

	@Operation(summary = "我的商品列表")
	@GetMapping("/my/page")
	public JsonResult<TableDataInfo<Product>> myProducts(@RequestParam(required = false) String status) {
		Long userId = StpUtil.getLoginIdAsLong();
		PageUtils.startPage();
		String s = (status == null || status.isEmpty() || "all".equals(status)) ? null : status;
		List<Product> list = productService.selectProductList(s, null, null, null, userId, null);
		return JsonResult.success(PageUtils.getDataTable(list));
	}

	// ====== 发布/编辑/操作 ======

	@Operation(summary = "发布商品 (提交后进入审核)")
	@PostMapping("/publish")
	public JsonResult<Long> publish(@RequestBody Product product) {
		Long userId = StpUtil.getLoginIdAsLong();
		return JsonResult.success(productService.publish(product, userId));
	}

	@Operation(summary = "编辑商品 (更新后重新进入审核)")
	@PutMapping("/update")
	public JsonResult<String> update(@RequestBody Product product) {
		Long userId = StpUtil.getLoginIdAsLong();
		String result = productService.updateProduct(product, userId);
		if ("OK".equals(result)) {
			return JsonResult.success("OK");
		}
		return JsonResult.error(result);
	}

	@Operation(summary = "下架商品")
	@PostMapping("/offshelf")
	public JsonResult<String> offshelf(@RequestParam Long productId) {
		Long userId = StpUtil.getLoginIdAsLong();
		String result = productService.offshelf(productId, userId);
		if ("OK".equals(result)) {
			return JsonResult.success("OK");
		}
		return JsonResult.error(result);
	}

	@Operation(summary = "删除商品")
	@DeleteMapping("/delete")
	public JsonResult<String> delete(@RequestParam Long productId) {
		Long userId = StpUtil.getLoginIdAsLong();
		String result = productService.deleteProduct(productId, userId);
		if ("OK".equals(result)) {
			return JsonResult.success("OK");
		}
		return JsonResult.error(result);
	}

	// ====== 草稿 ======

	@Operation(summary = "获取草稿")
	@GetMapping("/draft/get")
	public JsonResult<String> getDraft() {
		Long userId = StpUtil.getLoginIdAsLong();
		return JsonResult.success(productService.getDraft(userId));
	}

	@Operation(summary = "保存草稿")
	@PostMapping("/draft/save")
	public JsonResult<String> saveDraft(@RequestBody String snapshot) {
		Long userId = StpUtil.getLoginIdAsLong();
		productService.saveDraft(userId, snapshot);
		return JsonResult.success("OK");
	}

	@Operation(summary = "删除草稿")
	@DeleteMapping("/draft/delete")
	public JsonResult<String> deleteDraft() {
		Long userId = StpUtil.getLoginIdAsLong();
		productService.deleteDraft(userId);
		return JsonResult.success("OK");
	}

	@SaIgnore
	@Operation(summary = "Ping")
	@GetMapping("/ping")
	public JsonResult<String> ping() {
		return JsonResult.success("pong from product");
	}

	/** 给商品列表标注当前用户是否已收藏 */
	private void annotateFavorited(List<Product> list) {
		if (list == null || list.isEmpty() || !StpUtil.isLogin())
			return;
		productService.annotateFavorited(list, StpUtil.getLoginIdAsLong());
	}

}
