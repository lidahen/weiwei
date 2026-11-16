package com.twelvet.product.server.service.impl;

import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.framework.core.tenant.TenantSupport;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.product.api.domain.Product;
import com.twelvet.product.server.mapper.FavoriteMapper;
import com.twelvet.product.server.mapper.ProductMapper;
import com.twelvet.product.server.service.IFavoriteService;
import com.twelvet.product.server.service.IRecommendService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 收藏Service业务层处理
 *
 * @author twelvet
 */
@Service
public class FavoriteServiceImpl implements IFavoriteService {

	private static final Logger log = LoggerFactory.getLogger(FavoriteServiceImpl.class);

	private final FavoriteMapper favoriteMapper;

	private final ProductMapper productMapper;

	private final TenantSupport tenantSupport;

	private final IRecommendService recommendService;

	public FavoriteServiceImpl(FavoriteMapper favoriteMapper, ProductMapper productMapper, TenantSupport tenantSupport,
			IRecommendService recommendService) {
		this.favoriteMapper = favoriteMapper;
		this.productMapper = productMapper;
		this.tenantSupport = tenantSupport;
		this.recommendService = recommendService;
	}

	/**
	 * 当前租户ID（未识别租户时为 null，XML 中 <if> 不命中，过滤器不生效——零侵入回滚）
	 */
	private Long currentTid() {
		return TenantContext.get();
	}

	/**
	 * 查询收藏商品列表(分页)
	 */
	@Override
	public TableDataInfo<Product> selectFavoriteProductList(Long userId) {
		List<Product> list = favoriteMapper.selectFavoriteProductList(userId, currentTid());
		Long total = favoriteMapper.countFavorites(userId, currentTid());
		return TableDataInfo.page(list, total == null ? 0L : total);
	}

	/**
	 * 添加收藏
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public String addFavorite(Long userId, Long productId) {
		// 先确认商品存在于当前租户（跨租户 ID 枚举防护，与建订单同语义）
		Product product = productMapper.selectById(productId, currentTid());
		if (product == null) {
			throw new TWTException("商品不存在");
		}
		try {
			favoriteMapper.insertFavorite(userId, productId, product.getPrice(), tenantSupport.writeTid());
			favoriteMapper.incrementWantCount(productId);
			// 实时兴趣：收藏 = 3（docs/二手市场首页推荐设计.md 个性化改造）
			recommendService.bumpInterest(userId, productId, 3);
		}
		catch (Exception e) {
			// 重复收藏(唯一约束),静默忽略
			log.debug("用户{}已收藏商品{}", userId, productId);
		}
		return "OK";
	}

	/**
	 * 取消收藏
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public String removeFavorite(Long userId, Long productId) {
		favoriteMapper.deleteFavorite(userId, productId, currentTid());
		// 回减 want_count（与 addFavorite 的 increment 对称，GREATEST 防负数）
		favoriteMapper.decrementWantCount(productId);
		return "OK";
	}

	/**
	 * 查询收藏数量
	 */
	@Override
	public Long countFavorites(Long userId) {
		Long total = favoriteMapper.countFavorites(userId, currentTid());
		return total == null ? 0L : total;
	}

}
