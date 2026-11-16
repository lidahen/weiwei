package com.twelvet.product.server.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.twelvet.framework.core.exception.QuotaExceededException;
import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.framework.utils.SpringContextHolder;
import com.twelvet.product.api.domain.Product;
import com.twelvet.product.server.mapper.ProductMapper;
import com.twelvet.product.server.service.IProductService;
import com.twelvet.system.api.domain.SysTenant;
import com.twelvet.system.api.mapper.SysTenantMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 商品服务实现
 *
 * @author twelvet
 */
@Service
public class ProductServiceImpl implements IProductService {

	private static final Logger log = LoggerFactory.getLogger(ProductServiceImpl.class);

	private final ProductMapper productMapper;

	@Value("${billing.enabled:true}")
	private boolean billingEnabled;

	public ProductServiceImpl(ProductMapper productMapper) {
		this.productMapper = productMapper;
	}

	/**
	 * 当前租户ID（未识别租户时为 null，XML 中 <if> 不命中，过滤器不生效——零侵入回滚）
	 */
	private Long currentTid() {
		return TenantContext.get();
	}

	@Override
	public List<Product> selectProductList(String status, String catL1, String city, String keyword, Long sellerId,
			String orderBy) {
		return productMapper.selectList(status, catL1, city, keyword, sellerId, orderBy, currentTid());
	}

	@Override
	public Product selectProductById(Long productId) {
		return productMapper.selectById(productId, currentTid());
	}

	@Override
	public List<Product> selectBySellerId(Long sellerId, Long excludeProductId, Integer limit) {
		return productMapper.selectBySellerId(sellerId, excludeProductId, limit, currentTid());
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Long publish(Product product, Long sellerId) {
		product.setSellerId(sellerId);
		product.setStatus("auditing");
		product.setPublishTime(LocalDateTime.now());
		product.setCreateTime(LocalDateTime.now());
		product.setUpdateTime(LocalDateTime.now());
		// 商业化 P0：商品配额检查（在 mapper.insert 之前抛 QuotaExceededException，避开 MyBatis 拦截器异常包装）
		enforceProductQuota();
		// tenantId 由 TenantFieldInterceptor 自动注入（XML 写入 #{tenantId}）
		productMapper.insert(product);
		return product.getProductId();
	}

	/**
	 * 商业化 P0：检查租户商品配额。超限抛 {@link QuotaExceededException}（→ HTTP 429）。 第三轮
	 * P1：套餐过期自动降级（expireAt < now → 429），过期的 Pro 也拦截。 仅 billing.enabled=true 且租户已识别时生效。
	 */
	private void enforceProductQuota() {
		if (!billingEnabled) {
			return; // 商业化开关关闭 → 零侵入放行
		}
		Long tid = currentTid();
		if (tid == null) {
			return; // 未识别租户由 TenantFieldInterceptor 抛"缺少租户上下文"
		}
		SysTenantMapper mapper = SpringContextHolder.getBean(SysTenantMapper.class);
		SysTenant tenant = mapper.selectById(tid);
		if (tenant == null) {
			return;
		}
		if (tenant.getExpireAt() != null && tenant.getExpireAt().isBefore(LocalDateTime.now())) {
			throw new QuotaExceededException("套餐已于 " + tenant.getExpireAt().toLocalDate() + " 过期，请续费后发布商品");
		}
		Integer max = tenant.getQuotaProductMax();
		if (max == null || max == 0) {
			return; // 0=不限（Pro 套餐）
		}
		int used = mapper.countProducts(tid);
		if (used >= max) {
			throw new QuotaExceededException("商品配额已用完（" + used + "/" + max + "），请升级套餐");
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public String updateProduct(Product product, Long sellerId) {
		Product exist = productMapper.selectById(product.getProductId(), currentTid());
		if (exist == null)
			return "商品不存在";
		if (!exist.getSellerId().equals(sellerId))
			return "无权操作";
		if (!"draft".equals(exist.getStatus()) && !"rejected".equals(exist.getStatus())) {
			return "当前状态不允许编辑";
		}
		product.setSellerId(sellerId);
		product.setStatus("auditing");
		product.setUpdateTime(LocalDateTime.now());
		productMapper.update(product);
		return "OK";
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public String offshelf(Long productId, Long sellerId) {
		Product exist = productMapper.selectById(productId, currentTid());
		if (exist == null)
			return "商品不存在";
		if (!exist.getSellerId().equals(sellerId))
			return "无权操作";
		if (!"onsale".equals(exist.getStatus()))
			return "只有在售商品可下架";
		productMapper.updateStatus(productId, "offshelf", null, currentTid());
		return "OK";
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public String deleteProduct(Long productId, Long sellerId) {
		Product exist = productMapper.selectById(productId, currentTid());
		if (exist == null)
			return "商品不存在";
		if (!exist.getSellerId().equals(sellerId))
			return "无权操作";
		if ("sold".equals(exist.getStatus()))
			return "已售商品不可删除";
		productMapper.deleteById(productId, currentTid());
		return "OK";
	}

	@Override
	public String getDraft(Long sellerId) {
		return productMapper.selectDraftBySellerId(sellerId);
	}

	@Override
	public void saveDraft(Long sellerId, String snapshot) {
		productMapper.upsertDraft(sellerId, snapshot);
	}

	@Override
	public void deleteDraft(Long sellerId) {
		productMapper.deleteDraftBySellerId(sellerId);
	}

	@Override
	public void annotateFavorited(List<Product> list, Long userId) {
		if (list == null || list.isEmpty() || userId == null)
			return;
		List<Long> productIds = list.stream().map(Product::getProductId).collect(Collectors.toList());
		List<Long> favIds = productMapper.selectFavoriteProductIdsByUserAndIds(userId, productIds, currentTid());
		Set<Long> favSet = new HashSet<>(favIds);
		for (Product p : list) {
			p.setFavorited(favSet.contains(p.getProductId()));
		}
	}

	@Override
	public List<Product> selectForAi(String status, String catL1, String city, String keyword, BigDecimal priceMin,
			BigDecimal priceMax, String condition, String orderBy, Integer limit) {
		return productMapper.selectForAi(status, catL1, city, keyword, priceMin, priceMax, condition, orderBy, limit,
				currentTid());
	}

	@Override
	public Long countSoldProducts(Long sellerId) {
		return productMapper.countSoldProducts(sellerId);
	}

}
