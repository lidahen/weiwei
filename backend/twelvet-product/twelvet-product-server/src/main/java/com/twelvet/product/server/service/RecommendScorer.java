package com.twelvet.product.server.service;

import com.twelvet.product.api.domain.Product;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 推荐打分与打散的纯函数集合（无 IO，可单测；口径见 docs/二手市场首页推荐设计.md §2）
 */
public final class RecommendScorer {

	private RecommendScorer() {
	}

	/**
	 * 打分权重（application.yml recommend.weights.* 注入；B 组实验权重可覆盖）
	 */
	public record Weights(double interest, double hot, double recency, double quality, double negative) {
	}

	/**
	 * 单商品打分：interest/hot 已归一化(0~1)，recency 已算好(0~1)，negative 按 cat_l1 计数封顶扣减
	 * @param sellerGoodRate 卖家好评率(null=无评价，走中性基线)
	 * @param dislikeCount 该商品 cat_l1 的负反馈条数(封顶 negativeCap)
	 */
	public static double score(double interest, double hotNorm, double recency, Double sellerGoodRate, int dislikeCount,
			int negativeCap, double qualityDefault, Weights w) {
		double quality = sellerGoodRate == null ? qualityDefault : sellerGoodRate;
		double negative = w.negative() * Math.min(Math.max(dislikeCount, 0), negativeCap);
		return w.interest() * interest + w.hot() * hotNorm + w.recency() * recency + w.quality() * quality - negative;
	}

	/**
	 * 类目打散：排序结果中同 cat_l1 最多 maxPerCategory 条（防信息茧房）。 catL1Filter
	 * 非空时为显式类目过滤，跳过打散（保留该类目全部结果）。
	 */
	public static List<Product> applyDiversityCap(List<Product> ranked, String catL1Filter, int maxPerCategory) {
		boolean filterByCat = catL1Filter != null && !catL1Filter.isEmpty();
		List<Product> result = new ArrayList<>(ranked.size());
		Map<String, Integer> catCount = new HashMap<>();
		for (Product p : ranked) {
			if (filterByCat) {
				if (catL1Filter.equals(p.getCatL1())) {
					result.add(p);
				}
				continue;
			}
			String cat = p.getCatL1() == null ? "" : p.getCatL1();
			int count = catCount.getOrDefault(cat, 0);
			if (count >= maxPerCategory) {
				continue;
			}
			catCount.put(cat, count + 1);
			result.add(p);
		}
		return result;
	}

	/**
	 * A/B 分桶：userId 稳定哈希（取模，与登录态无关，同一用户恒定同组）。 未开启 AB / 匿名 → 0（A 组默认权重）。
	 * @param bPercent B 组百分比(0~100)，userId % 100 < bPercent 进入 B 组
	 */
	public static int resolveBucket(boolean abEnabled, Long userId, int bPercent) {
		if (!abEnabled || userId == null) {
			return 0;
		}
		int pct = Math.max(0, Math.min(100, bPercent));
		return userId % 100 < pct ? 1 : 0;
	}

}
