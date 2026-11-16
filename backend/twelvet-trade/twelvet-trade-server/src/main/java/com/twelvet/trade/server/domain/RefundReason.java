package com.twelvet.trade.server.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 退款原因白名单。
 *
 * <p>
 * C 端申请退款接口与后台售后详情页共用同一份白名单：编码用于落库，中文标签用于展示， 两者顺序必须稳定，否则两端会出现「存进去的编码查不到对应文案」的漂移。
 * </p>
 *
 * <p>
 * 部分原因不适用于退货退款（例如「未收到货」「不想要了」本身就没有可退回的实物）， 因此每个原因额外声明其支持的退款类型（refund_only /
 * return_refund）。
 * </p>
 *
 * @author twelvet
 */
public class RefundReason {

	/**
	 * 原因编码 → 中文标签，LinkedHashMap 保证展示顺序稳定。
	 */
	private static final Map<String, String> LABELS;

	/**
	 * 仅支持「仅退款」的原因编码。
	 */
	private static final Set<String> REFUND_ONLY = Set.of("not_received", "no_longer_needed");

	/**
	 * 仅退款类型编码。
	 */
	private static final String REFUND_ONLY_TYPE = "refund_only";

	/**
	 * 退货退款类型编码。
	 */
	private static final String RETURN_REFUND_TYPE = "return_refund";

	/**
	 * 未知编码回落用的标签。
	 */
	private static final String FALLBACK_LABEL = "其他";

	static {
		Map<String, String> labels = new LinkedHashMap<>();
		labels.put("not_as_described", "与描述不符");
		labels.put("damaged", "商品损坏");
		labels.put("wrong_item", "发错货");
		labels.put("not_received", "未收到货");
		labels.put("no_longer_needed", "不想要了");
		labels.put("other", "其他");
		LABELS = Collections.unmodifiableMap(labels);
	}

	/**
	 * 工具类，禁止实例化。
	 */
	private RefundReason() {
	}

	/**
	 * 判断原因编码是否在白名单内。
	 * @param code 原因编码
	 * @return 合法返回 true，null / 空串 / 未知编码返回 false
	 */
	public static boolean isValid(String code) {
		return code != null && LABELS.containsKey(code);
	}

	/**
	 * 取原因编码对应的中文标签，未知编码回落为「其他」。
	 * @param code 原因编码
	 * @return 中文标签
	 */
	public static String labelOf(String code) {
		String label = (code != null) ? LABELS.get(code) : null;
		return (label != null) ? label : FALLBACK_LABEL;
	}

	/**
	 * 判断某个原因是否适用于指定退款类型。
	 * @param code 原因编码
	 * @param refundType 退款类型，取 refund_only / return_refund
	 * @return 适用返回 true；未知原因、未知退款类型返回 false
	 */
	public static boolean supportsType(String code, String refundType) {
		if (!isValid(code)) {
			return false;
		}
		if (REFUND_ONLY_TYPE.equals(refundType)) {
			return true;
		}
		if (RETURN_REFUND_TYPE.equals(refundType)) {
			return !REFUND_ONLY.contains(code);
		}
		return false;
	}

	/**
	 * 按白名单顺序返回全部原因，每项形如 {@code {code, label, types}}，供 /trade/refund/reasons 直接序列化。
	 * @return 原因列表
	 */
	public static List<Map<String, Object>> all() {
		List<Map<String, Object>> reasons = new ArrayList<>(LABELS.size());
		for (Map.Entry<String, String> entry : LABELS.entrySet()) {
			String code = entry.getKey();
			Map<String, Object> item = new LinkedHashMap<>(4);
			item.put("code", code);
			item.put("label", entry.getValue());
			item.put("types", supportedTypes(code));
			reasons.add(item);
		}
		return reasons;
	}

	/**
	 * 取某原因支持的退款类型列表。
	 * @param code 原因编码
	 * @return 支持的退款类型列表
	 */
	private static List<String> supportedTypes(String code) {
		if (REFUND_ONLY.contains(code)) {
			return List.of(REFUND_ONLY_TYPE);
		}
		return List.of(REFUND_ONLY_TYPE, RETURN_REFUND_TYPE);
	}

}
