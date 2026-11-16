package com.twelvet.product.server.service.impl;

import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.framework.RedisUtils;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.framework.jdbc.web.utils.PageUtils;
import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.im.server.service.IImService;
import com.twelvet.product.api.domain.FunnelEvent;
import com.twelvet.product.api.domain.Product;
import com.twelvet.product.api.service.IFunnelEventService;
import com.twelvet.product.server.client.RecommendVectorClient;
import com.twelvet.product.server.client.RecommendVectorClient.VectorHit;
import com.twelvet.product.server.domain.RecommendImpression;
import com.twelvet.product.server.domain.UserInterestStat;
import com.twelvet.product.server.mapper.FavoriteMapper;
import com.twelvet.product.server.mapper.ProductMapper;
import com.twelvet.product.server.mapper.RecommendImpressionMapper;
import com.twelvet.product.server.mapper.SearchKeywordLogMapper;
import com.twelvet.product.server.mapper.UserInterestStatMapper;
import com.twelvet.product.server.mapper.UserMapper;
import com.twelvet.product.server.mapper.UserRelationMapper;
import com.twelvet.product.server.service.ICategoryService;
import com.twelvet.product.server.service.IRecommendService;
import com.twelvet.product.server.service.RecommendScorer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 首页推荐服务实现：多路召回（兴趣类目/热门/新品/向量）→ 过滤 → 规则打分 → 类目打散 → 手工分页
 * <p>
 * 打分 score = 0.5×interest + 0.3×hotNorm + 0.2×recency，权重与口径见 docs/二手市场首页推荐设计.md §2；
 * 向量通道（二期）仅做召回扩充，不参与打分（待 CTR 数据验证后再融入权重），失败静默降级。
 * </p>
 *
 * @author twelvet
 */
@Service
public class RecommendServiceImpl implements IRecommendService {

	private static final Logger log = LoggerFactory.getLogger(RecommendServiceImpl.class);

	/** 打分权重：application.yml recommend.weights.* 可覆盖（调参无需改代码） */
	@Value("${recommend.weights.interest:0.5}")
	private double wInterest;

	@Value("${recommend.weights.hot:0.3}")
	private double wHot;

	@Value("${recommend.weights.recency:0.2}")
	private double wRecency;

	@Value("${recommend.weights.quality:0.05}")
	private double wQuality;

	@Value("${recommend.weights.negative:0.1}")
	private double negativePenalty;

	/** A/B 实验：B 组流量百分比（userId%100 < bPercent 进入 B 组）；未开启时全部走默认权重 */
	@Value("${recommend.ab.enabled:false}")
	private boolean abEnabled;

	@Value("${recommend.ab.b-percent:50}")
	private int abBPercent;

	@Value("${recommend.ab.b.weights.interest:0.5}")
	private double abBInterest;

	@Value("${recommend.ab.b.weights.hot:0.3}")
	private double abBHot;

	@Value("${recommend.ab.b.weights.recency:0.2}")
	private double abBRecency;

	@Value("${recommend.ab.b.weights.quality:0.05}")
	private double abBQuality;

	@Value("${recommend.ab.b.weights.negative:0.1}")
	private double abBNegative;

	/** recency 半衰期(天)，与画像聚合的衰减口径一致 */
	private static final double RECENCY_HALF_LIFE_DAYS = 14.0;

	/** 类目打散：排序结果中同 cat_l1 最多保留条数(防信息茧房) */
	private static final int MAX_PER_CATEGORY = 5;

	private static final int TOP_CATS = 3;

	private static final int INTEREST_RECALL_LIMIT = 60;

	private static final int HOT_RECALL_LIMIT = 40;

	private static final int RECENT_RECALL_LIMIT = 30;

	private static final int INTEREST_WINDOW_DAYS = 30;

	private static final int RECENT_WINDOW_DAYS = 7;

	/** 近 N 天浏览过的商品不再推荐 */
	private static final int VIEWED_DEDUP_DAYS = 3;

	/** 向量召回 seed 窗口：近 N 天浏览过的商品作为用户兴趣向量 */
	private static final int VECTOR_SEED_WINDOW_DAYS = 30;

	private static final int VECTOR_RECALL_LIMIT = 20;

	/** "看过又看"协同召回容量 */
	private static final int ALSO_VIEWED_LIMIT = 20;

	/** 用户级召回缓存（向量/协同候选）TTL 分钟数 */
	private static final long USER_RECALL_CACHE_MINUTES = 10;

	/** 负反馈同 cat_l1 扣分封顶条数 */
	private static final int NEGATIVE_CAP = 3;

	/** 卖家质量中性基线（无评价卖家） */
	private static final double QUALITY_DEFAULT = 0.75;

	/** 浏览埋点去重窗口（分钟）：防 F5 刷热度与画像 */
	private static final int VIEW_DEDUP_MINUTES = 15;

	/** 同城召回容量（受 enable_nearby_recommend 开关控制） */
	private static final int CITY_RECALL_LIMIT = 15;

	/** 好物日报每用户推送件数 */
	private static final int DAILY_PICKS_COUNT = 3;

	/** 行为流水保留天数（浏览/曝光/搜索词/负反馈，每日清理任务执行） */
	private static final int RETENTION_DAYS = 90;

	private static final int SIMILAR_LIMIT_DEFAULT = 6;

	private static final String SCENE_HOME = "home_recommend";

	private final ProductMapper productMapper;

	private final UserInterestStatMapper interestStatMapper;

	private final UserRelationMapper userRelationMapper;

	private final ICategoryService categoryService;

	private final RecommendVectorClient vectorClient;

	private final RecommendImpressionMapper impressionMapper;

	private final SearchKeywordLogMapper searchKeywordLogMapper;

	private final FavoriteMapper favoriteMapper;

	private final IImService imService;

	private final UserMapper userMapper;

	private final IFunnelEventService funnelEventService;

	public RecommendServiceImpl(ProductMapper productMapper, UserInterestStatMapper interestStatMapper,
			UserRelationMapper userRelationMapper, ICategoryService categoryService, RecommendVectorClient vectorClient,
			RecommendImpressionMapper impressionMapper, SearchKeywordLogMapper searchKeywordLogMapper,
			FavoriteMapper favoriteMapper, IImService imService, UserMapper userMapper,
			IFunnelEventService funnelEventService) {
		this.productMapper = productMapper;
		this.interestStatMapper = interestStatMapper;
		this.userRelationMapper = userRelationMapper;
		this.categoryService = categoryService;
		this.vectorClient = vectorClient;
		this.impressionMapper = impressionMapper;
		this.searchKeywordLogMapper = searchKeywordLogMapper;
		this.favoriteMapper = favoriteMapper;
		this.imService = imService;
		this.userMapper = userMapper;
		this.funnelEventService = funnelEventService;
	}

	@Override
	public TableDataInfo<Product> homeRecommend(Long userId, String catL1, int current, int pageSize) {
		return doRecommend(userId, catL1, current, pageSize, SCENE_HOME);
	}

	private TableDataInfo<Product> doRecommend(Long userId, String catL1, int current, int pageSize, String scene) {
		Long tid = TenantContext.get();
		// A/B 分桶：B 组用户用实验权重（未开启恒为 A 组）
		int bucket = RecommendScorer.resolveBucket(abEnabled, userId, abBPercent);
		RecommendScorer.Weights weights = bucket == 1 ? abBWeights() : defaultWeights();
		// 1. 用户兴趣 top 类目（匿名/无画像时为空 → 自然降级为热门+新品双路）
		Map<String, BigDecimal> catWeights = new LinkedHashMap<>();
		if (userId != null) {
			for (UserInterestStat s : interestStatMapper.selectTopCats(userId, tid, TOP_CATS)) {
				catWeights.put(s.getCatL1(), s.getWeight() == null ? BigDecimal.ZERO : s.getWeight());
			}
		}

		// 2. 多路召回合并去重（LinkedHashMap 保序，记录首见通道供 reason 使用）
		Map<Long, Product> merged = new LinkedHashMap<>();
		Set<Long> fromInterest = new HashSet<>();
		Set<Long> fromRecent = new HashSet<>();
		Set<Long> fromCity = new HashSet<>();
		if (!catWeights.isEmpty()) {
			for (Product p : productMapper.selectInterestCandidates(tid, new ArrayList<>(catWeights.keySet()),
					INTEREST_WINDOW_DAYS, INTEREST_RECALL_LIMIT)) {
				if (merged.putIfAbsent(p.getProductId(), p) == null) {
					fromInterest.add(p.getProductId());
				}
			}
		}
		for (Product p : productMapper.selectHotCandidates(tid, HOT_RECALL_LIMIT)) {
			merged.putIfAbsent(p.getProductId(), p);
		}
		for (Product p : productMapper.selectRecentCandidates(tid, RECENT_WINDOW_DAYS, RECENT_RECALL_LIMIT)) {
			if (merged.putIfAbsent(p.getProductId(), p) == null) {
				fromRecent.add(p.getProductId());
			}
		}
		// 2b. 行为协同 + 向量通道（增强项：seed=近 30 天浏览商品；均失败静默降级）
		Set<Long> fromVector = new HashSet<>();
		Set<Long> fromAlso = new HashSet<>();
		List<Long> seeds = userId == null ? Collections.emptyList()
				: interestStatMapper.selectRecentViewedProductIds(userId, VECTOR_SEED_WINDOW_DAYS);
		if (userId != null && tid != null && !seeds.isEmpty()) {
			// 向量召回：seed 商品向量均值 → 语义相似商品（Redis 缓存 10min，含空结果防 ai-service 宕机时每请求吃超时）
			try {
				List<VectorHit> hits = vectorCandidatesCached(tid, userId, seeds);
				if (!hits.isEmpty()) {
					List<Long> hitIds = hits.stream().map(VectorHit::productId).toList();
					Map<Long, Product> byId = productMapper.selectOnsaleByIds(tid, hitIds)
						.stream()
						.collect(Collectors.toMap(Product::getProductId, p -> p));
					for (VectorHit hit : hits) {
						Product p = byId.get(hit.productId());
						if (p != null && merged.putIfAbsent(p.getProductId(), p) == null) {
							fromVector.add(p.getProductId());
						}
					}
				}
			}
			catch (Exception e) {
				log.warn("[推荐] 向量召回通道不可用，降级规则通道: {}", e.getMessage());
			}
			// "看过又看"协同召回：与 seed 有共同浏览者的商品（行为协同，与向量语义相似互补；Redis 缓存 10min）
			try {
				List<Long> alsoIds = alsoViewedCached(tid, userId, seeds);
				if (!alsoIds.isEmpty()) {
					Map<Long, Product> byId = productMapper.selectOnsaleByIds(tid, alsoIds)
						.stream()
						.collect(Collectors.toMap(Product::getProductId, p -> p));
					for (Long pid : alsoIds) {
						Product p = byId.get(pid);
						if (p != null && merged.putIfAbsent(pid, p) == null) {
							fromAlso.add(pid);
						}
					}
				}
			}
			catch (Exception e) {
				log.warn("[推荐] 协同召回通道异常，跳过: {}", e.getMessage());
			}
		}
		// 2c. 同城召回：默认收货地址城市（enable_nearby_recommend 隐私开关，未配置视为开启）
		if (userId != null && tid != null) {
			try {
				Integer nearbyEnabled = userMapper.selectNearbyRecommendEnabled(userId);
				if (nearbyEnabled == null || nearbyEnabled == 1) {
					String city = userMapper.selectDefaultCity(userId);
					if (city != null && !city.isEmpty()) {
						for (Product p : productMapper.selectCityCandidates(tid, city, VECTOR_SEED_WINDOW_DAYS,
								CITY_RECALL_LIMIT)) {
							if (merged.putIfAbsent(p.getProductId(), p) == null) {
								fromCity.add(p.getProductId());
							}
						}
					}
				}
			}
			catch (Exception e) {
				log.warn("[推荐] 同城召回通道异常，跳过: {}", e.getMessage());
			}
		}

		// 3. 过滤：本人商品 / 近 N 天已浏览 / 用户点过"不感兴趣"的商品
		Set<Long> viewedIds = userId == null ? Collections.emptySet()
				: new HashSet<>(interestStatMapper.selectRecentViewedProductIds(userId, VIEWED_DEDUP_DAYS));
		Set<Long> dislikedIds = new HashSet<>();
		Map<String, Integer> dislikeCats = new HashMap<>();
		if (userId != null) {
			for (Map<String, Object> d : interestStatMapper.selectUserDislikes(userId, VECTOR_SEED_WINDOW_DAYS)) {
				Object pid = d.get("productId");
				if (pid != null) {
					dislikedIds.add(((Number) pid).longValue());
				}
				Object cat = d.get("catL1");
				if (cat != null) {
					dislikeCats.merge((String) cat, 1, Integer::sum);
				}
			}
		}
		List<Product> candidates = merged.values()
			.stream()
			.filter(p -> userId == null || !userId.equals(p.getSellerId()))
			.filter(p -> !viewedIds.contains(p.getProductId()))
			.filter(p -> !dislikedIds.contains(p.getProductId()))
			.collect(Collectors.toList());

		// 4. 打分排序（同分按发布时间倒序）
		double maxHot = candidates.stream()
			.map(Product::getHotScore)
			.filter(java.util.Objects::nonNull)
			.mapToDouble(BigDecimal::doubleValue)
			.max()
			.orElse(0D);
		double maxCatWeight = catWeights.values().stream().mapToDouble(BigDecimal::doubleValue).max().orElse(0D);
		// 批量卖家好评率（打分 tie-breaker；无评价走中性基线）
		Map<Long, Double> goodRates = new HashMap<>();
		List<Long> sellerIds = candidates.stream()
			.map(Product::getSellerId)
			.filter(java.util.Objects::nonNull)
			.distinct()
			.collect(Collectors.toList());
		if (!sellerIds.isEmpty()) {
			try {
				for (Map<String, Object> row : userMapper.selectSellerGoodRates(sellerIds, tid)) {
					Object sid = row.get("sellerId");
					Object rate = row.get("goodRate");
					if (sid != null && rate != null) {
						goodRates.put(((Number) sid).longValue(), ((Number) rate).doubleValue());
					}
				}
			}
			catch (Exception e) {
				log.warn("[推荐] 卖家质量批量查询失败: {}", e.getMessage());
			}
		}
		Map<Long, Double> scores = new HashMap<>(candidates.size());
		for (Product p : candidates) {
			double interest = maxCatWeight > 0 && catWeights.containsKey(p.getCatL1())
					? catWeights.get(p.getCatL1()).doubleValue() / maxCatWeight : 0D;
			double hot = maxHot > 0 && p.getHotScore() != null ? p.getHotScore().doubleValue() / maxHot : 0D;
			Double quality = p.getSellerId() == null ? null : goodRates.get(p.getSellerId());
			scores.put(p.getProductId(), RecommendScorer.score(interest, hot, recencyScore(p), quality,
					dislikeCats.getOrDefault(p.getCatL1(), 0), NEGATIVE_CAP, QUALITY_DEFAULT, weights));
		}
		candidates.sort((a, b) -> {
			int c = Double.compare(scores.getOrDefault(b.getProductId(), 0D),
					scores.getOrDefault(a.getProductId(), 0D));
			if (c != 0) {
				return c;
			}
			LocalDateTime ta = a.getPublishTime();
			LocalDateTime tb = b.getPublishTime();
			if (ta == null || tb == null) {
				return ta == null && tb == null ? 0 : (ta == null ? 1 : -1);
			}
			return tb.compareTo(ta);
		});

		// 5. catL1 显式过滤（跳过打散）或类目打散（同 cat_l1 最多 MAX_PER_CATEGORY 条）
		List<Product> ranked = RecommendScorer.applyDiversityCap(candidates, catL1, MAX_PER_CATEGORY);

		// 6. 推荐理由（按召回通道优先级：兴趣 > 新品 > 向量 > 协同 > 同城 > 热门）
		Map<String, String> catNames = catNames(catWeights.keySet());
		for (Product p : ranked) {
			p.setReason(reasonOf(p, fromInterest, fromRecent, fromVector, fromAlso, fromCity, catNames));
		}

		// 7. 手工分页（推荐流为内存排序，不走 PageHelper）+ 曝光流水（静默失败）
		int from = Math.max(0, (current - 1) * pageSize);
		List<Product> page = from >= ranked.size() ? Collections.emptyList()
				: new ArrayList<>(ranked.subList(from, Math.min(from + pageSize, ranked.size())));
		logImpressions(tid, userId, page, from, scene, bucket);
		return TableDataInfo.page(page, ranked.size());
	}

	@Override
	public List<Product> selectFollowFeed(Long userId) {
		if (userId == null) {
			return Collections.emptyList();
		}
		List<Long> followedIds = userRelationMapper.selectFollowedIds(userId);
		if (followedIds.isEmpty()) {
			return Collections.emptyList();
		}
		// 先解析关注 ID 再 startPage，避免 PageHelper 错误作用在 selectFollowedIds 上
		PageUtils.startPage();
		return productMapper.selectFollowFeed(TenantContext.get(), followedIds);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public int refreshInterestStat() {
		interestStatMapper.deleteAll();
		int rows = interestStatMapper.insertViewInterest();
		rows += interestStatMapper.insertFavoriteInterest();
		rows += interestStatMapper.insertOrderInterest();
		return rows;
	}

	@Override
	public int refreshHotScore() {
		return productMapper.refreshHotScore();
	}

	@Override
	public List<Product> selectSimilar(Long productId, int limit) {
		Long tid = TenantContext.get();
		Product self = productMapper.selectById(productId, tid);
		if (self == null) {
			return Collections.emptyList();
		}
		if (tid != null) {
			// 向量召回 + "看过又看"协同合并（协同为详情页补充行为信号）
			Map<Long, Product> byId = new LinkedHashMap<>();
			try {
				List<VectorHit> hits = vectorClient.similar(tid, productId, limit);
				if (!hits.isEmpty()) {
					List<Long> ids = hits.stream().map(VectorHit::productId).toList();
					productMapper.selectOnsaleByIds(tid, ids).forEach(p -> byId.put(p.getProductId(), p));
					hits.forEach(h -> byId.computeIfPresent(h.productId(),
							(k, p) -> p.getReason() == null ? withReason(p, "相似推荐") : p));
				}
			}
			catch (Exception e) {
				log.warn("[推荐] 相似商品向量召回失败: {}", e.getMessage());
			}
			try {
				List<Long> alsoIds = interestStatMapper.selectAlsoViewedForProduct(productId, VECTOR_SEED_WINDOW_DAYS,
						limit);
				if (!alsoIds.isEmpty()) {
					Map<Long, Product> also = productMapper.selectOnsaleByIds(tid, alsoIds)
						.stream()
						.collect(Collectors.toMap(Product::getProductId, p -> p));
					for (Long id : alsoIds) {
						Product p = also.get(id);
						if (p != null && byId.putIfAbsent(id, withReason(p, "大家都在看")) == null && byId.size() >= limit) {
							break;
						}
					}
				}
			}
			catch (Exception e) {
				log.warn("[推荐] 详情页协同召回异常: {}", e.getMessage());
			}
			if (!byId.isEmpty()) {
				List<Product> result = byId.values()
					.stream()
					.filter(p -> !p.getProductId().equals(productId))
					.limit(limit)
					.collect(Collectors.toList());
				result.forEach(p -> {
					if (p.getReason() == null) {
						p.setReason("相似推荐");
					}
				});
				if (!result.isEmpty()) {
					return result;
				}
			}
		}
		List<Product> fallback = productMapper.selectSimilarFallback(tid, self.getCatL1(), productId, limit);
		fallback.forEach(p -> p.setReason("同类热门"));
		return fallback;
	}

	private Product withReason(Product p, String reason) {
		p.setReason(reason);
		return p;
	}

	@Override
	public int syncVectors() {
		return vectorClient.reindex();
	}

	@Override
	public Map<String, Object> recommendStats(Integer days, Integer bucket) {
		int d = days == null || days <= 0 ? 7 : Math.min(days, 90);
		Map<String, Object> result = new HashMap<>();
		result.put("daily", impressionMapper.selectDailyStats(d));
		result.put("top", impressionMapper.selectTopProducts(d, 10, bucket));
		return result;
	}

	@Override
	public void recordView(Long productId, Long userId) {
		try {
			productMapper.incrementView(productId, TenantContext.get());
			if (userId != null) {
				// 15 分钟窗口去重：防 F5 连刷灌水热度分与画像权重
				if (interestStatMapper.selectRecentViewExists(userId, productId, VIEW_DEDUP_MINUTES) == 0) {
					// view_log.user_id NOT NULL，匿名浏览只累加 view_count
					interestStatMapper.insertViewLog(productId, userId);
					// 实时兴趣：浏览 = 1（批量重建每 30 分钟兜底修正）
					interestStatMapper.bumpInterestByProduct(userId, productId, 1);
				}
			}
		}
		catch (Exception e) {
			log.warn("[推荐] 浏览埋点失败: {}", e.getMessage());
		}
	}

	@Override
	public void logSearchKeyword(Long userId, String keyword) {
		try {
			// 仅记录登录用户：匿名请求可被脚本刷搜索词污染热搜榜
			if (userId == null || keyword == null) {
				return;
			}
			String kw = keyword.trim();
			if (kw.isEmpty() || kw.length() > 60) {
				return;
			}
			// 同用户同词 5 分钟去重（连点/刷新不重复计数）
			if (searchKeywordLogMapper.selectRecentSameKeyword(userId, kw, 5) > 0) {
				return;
			}
			searchKeywordLogMapper.insert(TenantContext.get(), userId, kw);
		}
		catch (Exception e) {
			log.warn("[推荐] 搜索关键词记录失败: {}", e.getMessage());
		}
	}

	/**
	 * 向量候选（Redis 缓存 10min，含空结果）： 向量 HTTP 是首页延迟尾部的主要来源，缓存后重复请求与 ai-service 故障期间不再反复打到上游
	 */
	private List<VectorHit> vectorCandidatesCached(Long tid, Long userId, List<Long> seeds) {
		String key = "recommend:vector:" + tid + ":" + userId;
		try {
			List<Long> cached = RedisUtils.getCacheObject(key);
			if (cached != null) {
				return cached.stream().map(id -> new VectorHit(id, 0D)).collect(Collectors.toList());
			}
		}
		catch (Exception e) {
			log.warn("[推荐] 向量候选缓存读取失败: {}", e.getMessage());
		}
		List<VectorHit> hits = vectorClient.candidates(tid, seeds, VECTOR_RECALL_LIMIT);
		try {
			RedisUtils.setCacheObject(key, hits.stream().map(VectorHit::productId).collect(Collectors.toList()),
					USER_RECALL_CACHE_MINUTES, TimeUnit.MINUTES);
		}
		catch (Exception e) {
			log.warn("[推荐] 向量候选缓存写入失败: {}", e.getMessage());
		}
		return hits;
	}

	/** "看过又看"候选（Redis 缓存 10min）：协同自连接是 DB 侧最重的查询 */
	private List<Long> alsoViewedCached(Long tid, Long userId, List<Long> seeds) {
		String key = "recommend:also:" + tid + ":" + userId;
		try {
			List<Long> cached = RedisUtils.getCacheObject(key);
			if (cached != null) {
				return cached;
			}
		}
		catch (Exception e) {
			log.warn("[推荐] 协同候选缓存读取失败: {}", e.getMessage());
		}
		List<Long> ids = interestStatMapper.selectAlsoViewed(seeds, VECTOR_SEED_WINDOW_DAYS, ALSO_VIEWED_LIMIT);
		try {
			RedisUtils.setCacheObject(key, ids, USER_RECALL_CACHE_MINUTES, TimeUnit.MINUTES);
		}
		catch (Exception e) {
			log.warn("[推荐] 协同候选缓存写入失败: {}", e.getMessage());
		}
		return ids;
	}

	/** 曝光流水批量落库（点击判定由统计 SQL 关联 view_log 完成） */
	void logImpressions(Long tenantId, Long userId, List<Product> page, int offset, String scene, int bucket) {
		try {
			if (page.isEmpty()) {
				return;
			}
			List<RecommendImpression> rows = new ArrayList<>(page.size());
			for (int i = 0; i < page.size(); i++) {
				RecommendImpression r = new RecommendImpression();
				r.setTenantId(tenantId);
				r.setUserId(userId);
				r.setProductId(page.get(i).getProductId());
				r.setScene(scene);
				r.setPosition(offset + i + 1);
				r.setBucket(bucket);
				rows.add(r);
			}
			impressionMapper.batchInsert(rows);
			// 核心漏斗（spec §6.3）：曝光是漏斗第一步，必须带租户/类目/场景才能按社区切片
			List<FunnelEvent> events = new ArrayList<>(page.size());
			for (Product p : page) {
				FunnelEvent event = new FunnelEvent();
				event.setTenantId(tenantId);
				event.setEventType(FunnelEvent.TYPE_PRODUCT_EXPOSURE);
				event.setUserId(userId);
				event.setRole(userId == null ? "visitor" : "buyer");
				event.setProductId(p.getProductId());
				event.setCategory(p.getCatL1());
				event.setScene(scene);
				events.add(event);
			}
			funnelEventService.recordBatch(events);
		}
		catch (Exception e) {
			log.warn("[推荐] 曝光记录失败: {}", e.getMessage());
		}
	}

	private double recencyScore(Product p) {
		if (p.getPublishTime() == null) {
			return 0D;
		}
		double days = Math.max(0, Duration.between(p.getPublishTime(), LocalDateTime.now()).toMillis() / 86400000.0);
		return Math.exp(-days * Math.log(2) / RECENCY_HALF_LIFE_DAYS);
	}

	private Map<String, String> catNames(Set<String> codes) {
		Map<String, String> names = new HashMap<>();
		if (codes.isEmpty()) {
			return names;
		}
		try {
			for (Map<String, Object> row : categoryService.selectFlatCategory()) {
				String code = (String) row.get("code");
				String name = (String) row.get("name");
				if (code != null && name != null) {
					names.put(code, name);
				}
			}
		}
		catch (Exception e) {
			log.warn("[推荐] 类目名称查询失败，推荐理由退化为类目编码", e);
		}
		return names;
	}

	private String reasonOf(Product p, Set<Long> fromInterest, Set<Long> fromRecent, Set<Long> fromVector,
			Set<Long> fromAlso, Set<Long> fromCity, Map<String, String> catNames) {
		if (fromInterest.contains(p.getProductId())) {
			return "你最近在看「" + catNames.getOrDefault(p.getCatL1(), p.getCatL1()) + "」";
		}
		if (fromRecent.contains(p.getProductId())) {
			return "新上架";
		}
		if (fromVector.contains(p.getProductId())) {
			return "猜你喜欢";
		}
		if (fromAlso.contains(p.getProductId())) {
			return "大家都在看";
		}
		if (fromCity.contains(p.getProductId())) {
			return "同城好物";
		}
		return "本周热门";
	}

	@Override
	public void bumpInterest(Long userId, Long productId, int weight) {
		try {
			if (userId == null || productId == null) {
				return;
			}
			interestStatMapper.bumpInterestByProduct(userId, productId, weight);
		}
		catch (Exception e) {
			log.warn("[推荐] 实时兴趣更新失败: {}", e.getMessage());
		}
	}

	@Override
	public void initInterest(Long userId, List<String> cats) {
		if (userId == null || cats == null || cats.isEmpty()) {
			throw new TWTException("请至少选择一个感兴趣的分类");
		}
		List<String> distinct = cats.stream()
			.filter(c -> c != null && !c.isBlank() && c.length() <= 20)
			.distinct()
			.limit(5)
			.collect(Collectors.toList());
		if (distinct.isEmpty()) {
			throw new TWTException("分类参数无效");
		}
		// 种子行 source='init'：行为聚合重建不清空；重复引导先清旧种子
		interestStatMapper.deleteInitInterest(userId);
		interestStatMapper.insertInitInterest(TenantContext.get(), userId, distinct);
	}

	@Override
	public void dislike(Long userId, Long productId) {
		try {
			if (userId == null || productId == null) {
				return;
			}
			interestStatMapper.insertDislike(TenantContext.get(), userId, productId);
		}
		catch (Exception e) {
			log.warn("[推荐] 负反馈记录失败: {}", e.getMessage());
		}
	}

	@Override
	public List<String> searchHistory(Long userId) {
		if (userId == null) {
			return Collections.emptyList();
		}
		try {
			return searchKeywordLogMapper.selectHistory(userId, 10);
		}
		catch (Exception e) {
			log.warn("[推荐] 搜索历史查询失败: {}", e.getMessage());
			return Collections.emptyList();
		}
	}

	@Override
	public void clearSearchHistory(Long userId) {
		if (userId != null) {
			searchKeywordLogMapper.deleteByUser(userId);
		}
	}

	@Override
	public List<Map<String, Object>> searchHot() {
		try {
			return searchKeywordLogMapper.selectHot(TenantContext.get(), 7, 10);
		}
		catch (Exception e) {
			log.warn("[推荐] 热搜查询失败: {}", e.getMessage());
			return Collections.emptyList();
		}
	}

	@Override
	public String guideInterestContext(Long userId) {
		if (userId == null) {
			return null;
		}
		try {
			Long tid = TenantContext.get();
			StringBuilder sb = new StringBuilder();
			Map<String, BigDecimal> catWeights = new LinkedHashMap<>();
			for (UserInterestStat s : interestStatMapper.selectTopCats(userId, tid, TOP_CATS)) {
				catWeights.put(s.getCatL1(), s.getWeight() == null ? BigDecimal.ZERO : s.getWeight());
			}
			if (!catWeights.isEmpty()) {
				Map<String, String> names = catNames(catWeights.keySet());
				List<String> nameList = catWeights.keySet()
					.stream()
					.map(c -> names.getOrDefault(c, c))
					.collect(Collectors.toList());
				sb.append("近期感兴趣的类目：").append(String.join("、", nameList));
			}
			List<Long> recent = interestStatMapper.selectRecentViewedProductIds(userId, 14);
			if (!recent.isEmpty() && tid != null) {
				List<Product> ps = productMapper.selectOnsaleByIds(tid, recent.subList(0, Math.min(5, recent.size())));
				if (!ps.isEmpty()) {
					sb.append(sb.length() > 0 ? "；" : "").append("最近浏览过：");
					sb.append(ps.stream()
						.map(p -> "「" + p.getTitle() + "」(¥" + p.getPrice() + ")")
						.collect(Collectors.joining("、")));
				}
			}
			if (sb.length() == 0) {
				return null;
			}
			return "【用户画像，仅供个性化参考：请自然融入推荐话术，不要直接罗列或向用户复述】" + sb + "。推荐商品时优先贴合以上兴趣。";
		}
		catch (Exception e) {
			log.warn("[推荐] 导购画像上下文构建失败: {}", e.getMessage());
			return null;
		}
	}

	@Override
	public int scanPriceDrop() {
		Long tid = TenantContext.get();
		// 先给无快照的存量收藏播种基线（不通知），保证对比从"上一次已知价格"开始
		int seeded = favoriteMapper.seedMissingSnapshots(tid);
		List<Map<String, Object>> drops = favoriteMapper.selectPriceDrops(tid);
		if (drops.isEmpty()) {
			return 0;
		}
		// Quartz 线程无租户上下文 → 按行内租户分组取系统发送人
		Map<Long, List<Map<String, Object>>> byTenant = drops.stream()
			.filter(r -> r.get("tenantId") != null)
			.collect(Collectors.groupingBy(r -> ((Number) r.get("tenantId")).longValue()));
		int notified = 0;
		for (Map.Entry<Long, List<Map<String, Object>>> entry : byTenant.entrySet()) {
			Long senderId = favoriteMapper.selectSenderUserId(entry.getKey());
			if (senderId == null) {
				log.warn("[降价提醒] 租户 {} 未找到系统发送人，跳过 {} 条", entry.getKey(), entry.getValue().size());
				continue;
			}
			for (Map<String, Object> row : entry.getValue()) {
				Long uid = ((Number) row.get("userId")).longValue();
				Long pid = ((Number) row.get("productId")).longValue();
				BigDecimal snap = new BigDecimal(String.valueOf(row.get("snapPrice")));
				BigDecimal now = new BigDecimal(String.valueOf(row.get("newPrice")));
				String title = String.valueOf(row.get("title"));
				try {
					imService.sendMessage(senderId, uid, null, "system",
							"🔔 降价提醒：你收藏的「" + title + "」降价啦，¥" + snap.stripTrailingZeros().toPlainString() + " → ¥"
									+ now.stripTrailingZeros().toPlainString() + "，快去看看吧！");
					// 推送后刷新快照：同一轮降价只提醒一次
					favoriteMapper.updateFavoriteSnapshot(uid, pid, now);
					notified++;
				}
				catch (Exception e) {
					log.warn("[降价提醒] userId={} productId={} 推送失败: {}", uid, pid, e.getMessage());
				}
			}
		}
		log.info("[降价提醒] 基线播种 {} 条，降价通知 {} 人", seeded, notified);
		return notified;
	}

	private RecommendScorer.Weights defaultWeights() {
		return new RecommendScorer.Weights(wInterest, wHot, wRecency, wQuality, negativePenalty);
	}

	private RecommendScorer.Weights abBWeights() {
		return new RecommendScorer.Weights(abBInterest, abBHot, abBRecency, abBQuality, abBNegative);
	}

	@Override
	public int refreshDailyStats() {
		int rows = interestStatMapper.upsertYesterdayViewStats();
		log.info("[Quartz] ProductDailyStatsJob 聚合完成：{} 个商品的昨日浏览桶", rows);
		return rows;
	}

	@Override
	public int purgeExpiredLogs() {
		int total = interestStatMapper.deleteOldViewLogs(RETENTION_DAYS);
		total += impressionMapper.deleteOldImpressions(RETENTION_DAYS);
		total += searchKeywordLogMapper.deleteOldKeywords(RETENTION_DAYS);
		total += interestStatMapper.deleteOldDislikes(RETENTION_DAYS);
		log.info("[Quartz] LogPurgeJob 清理完成：{} 行 {} 天前流水", total, RETENTION_DAYS);
		return total;
	}

	@Override
	public int pushDailyPicks() {
		int pushed = 0;
		try {
			List<Long> tenants = interestStatMapper.selectDistinctOnsaleTenants();
			for (Long tid : tenants) {
				Long senderId = favoriteMapper.selectSenderUserId(tid);
				if (senderId == null) {
					continue;
				}
				List<Long> userIds = favoriteMapper.selectTenantUserIds(tid);
				for (Long uid : userIds) {
					try {
						// 只推有画像的用户（无行为也未引导的用户推送无个性化意义，冷启动靠引导弹窗）
						if (interestStatMapper.selectTopCats(uid, tid, 1).isEmpty()) {
							continue;
						}
						TenantContext.set(tid);
						List<Product> picks = doRecommend(uid, null, 1, DAILY_PICKS_COUNT, "daily_push").getRecords();
						if (picks.isEmpty()) {
							continue;
						}
						String items = picks.stream()
							.map(p -> "「" + p.getTitle() + "」¥" + p.getPrice().stripTrailingZeros().toPlainString()
									+ (p.getReason() == null || p.getReason().isEmpty() ? ""
											: "（" + p.getReason() + "）"))
							.collect(Collectors.joining("、"));
						imService.sendMessage(senderId, uid, null, "system",
								"🛍 今日为你挑的好物：" + items + "，来首页「推荐」逛逛更多好物吧～");
						pushed++;
					}
					catch (Exception e) {
						log.warn("[好物日报] 租户 {} 用户 {} 推送失败: {}", tid, uid, e.getMessage());
					}
					finally {
						TenantContext.clear();
					}
				}
			}
		}
		catch (Exception e) {
			log.error("[好物日报] 推送任务异常: {}", e.getMessage(), e);
		}
		log.info("[Quartz] DailyPicksJob 推送完成：{} 人", pushed);
		return pushed;
	}

}
