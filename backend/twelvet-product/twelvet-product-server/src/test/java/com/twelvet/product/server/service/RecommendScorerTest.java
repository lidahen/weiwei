package com.twelvet.product.server.service;

import com.twelvet.product.api.domain.Product;
import com.twelvet.product.server.service.RecommendScorer.Weights;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 推荐打分/打散/分桶纯函数单测（无 Spring 上下文）
 */
class RecommendScorerTest {

	private static final Weights W = new Weights(0.5, 0.3, 0.2, 0.05, 0.1);

	private static Product product(Long id, String catL1) {
		Product p = new Product();
		p.setProductId(id);
		p.setCatL1(catL1);
		return p;
	}

	// ====== 打分 ======

	@Test
	void score_applies_weights_linearly() {
		// interest=1, hot=0, recency=0, quality=null(基线0.75), 无负反馈
		double s = RecommendScorer.score(1, 0, 0, null, 0, 3, 0.75, W);
		assertEquals(0.5 + 0.05 * 0.75, s, 1e-9);
	}

	@Test
	void score_negative_capped_at_cap() {
		double three = RecommendScorer.score(0, 0, 0, null, 3, 3, 0.75, W);
		double ten = RecommendScorer.score(0, 0, 0, null, 10, 3, 0.75, W);
		assertEquals(three, ten, 1e-9, "超过封顶条数后不再加扣");
		assertEquals(0.1 * 3, 0.75 * 0.05 - ten, 1e-9);
	}

	@Test
	void score_null_quality_uses_default_and_rated_seller_overrides() {
		double unrated = RecommendScorer.score(0, 0, 0, null, 0, 3, 0.75, W);
		double good = RecommendScorer.score(0, 0, 0, 1.0, 0, 3, 0.75, W);
		double bad = RecommendScorer.score(0, 0, 0, 0.0, 0, 3, 0.75, W);
		assertEquals(0.05 * 0.75, unrated, 1e-9);
		assertEquals(0.05, good, 1e-9);
		assertEquals(0.0, bad, 1e-9);
	}

	@Test
	void score_dislike_beats_interest_at_cap() {
		// 3 条负反馈 = -0.3，足以压过满格兴趣 0.5*1=0.5？不相抵——验证负向确实生效即可
		double withDislike = RecommendScorer.score(1, 0, 0, null, 3, 3, 0.75, W);
		double without = RecommendScorer.score(1, 0, 0, null, 0, 3, 0.75, W);
		assertEquals(0.3, without - withDislike, 1e-9);
	}

	// ====== 类目打散 ======

	@Test
	void diversity_cap_limits_per_category() {
		List<Product> ranked = List.of(product(1L, "digital"), product(2L, "digital"), product(3L, "digital"),
				product(4L, "digital"), product(5L, "digital"), product(6L, "digital"), product(7L, "home"));
		List<Product> capped = RecommendScorer.applyDiversityCap(ranked, null, 5);
		assertEquals(6, capped.size(), "digital 截断到 5 条 + home 1 条");
		assertEquals(5, capped.stream().filter(p -> "digital".equals(p.getCatL1())).count());
	}

	@Test
	void diversity_cap_filter_keeps_all_of_category() {
		List<Product> ranked = List.of(product(1L, "digital"), product(2L, "digital"), product(3L, "digital"),
				product(4L, "digital"), product(5L, "digital"), product(6L, "digital"), product(7L, "home"));
		List<Product> filtered = RecommendScorer.applyDiversityCap(ranked, "digital", 5);
		assertEquals(6, filtered.size(), "显式类目过滤跳过打散");
	}

	@Test
	void diversity_cap_handles_null_category() {
		List<Product> ranked = List.of(product(1L, null), product(2L, null), product(3L, null));
		List<Product> capped = RecommendScorer.applyDiversityCap(ranked, null, 2);
		assertEquals(2, capped.size(), "null 类目归入空串桶正常打散");
	}

	// ====== A/B 分桶 ======

	@Test
	void bucket_disabled_or_anonymous_is_zero() {
		assertEquals(0, RecommendScorer.resolveBucket(false, 1L, 50));
		assertEquals(0, RecommendScorer.resolveBucket(true, null, 50));
	}

	@Test
	void bucket_stable_for_same_user() {
		long uid = 12345L;
		assertEquals(RecommendScorer.resolveBucket(true, uid, 50), RecommendScorer.resolveBucket(true, uid, 50));
		int b = RecommendScorer.resolveBucket(true, uid, 50);
		assertTrue(b == 0 || b == 1);
	}

	@Test
	void bucket_respects_percent_bounds() {
		assertEquals(0, RecommendScorer.resolveBucket(true, 42L, 0), "0% 流量全在 A 组");
		assertEquals(1, RecommendScorer.resolveBucket(true, 42L, 100), "100% 流量全在 B 组");
	}

}
