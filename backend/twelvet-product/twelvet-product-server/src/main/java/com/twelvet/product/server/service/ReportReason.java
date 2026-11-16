package com.twelvet.product.server.service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 举报原因枚举与 reason 列合成文本的拆分工具
 * <p>
 * report.reason 是单列 varchar(500)，存「标签：详情」合成文本（不新增 reason_type 列）。 compose 与
 * splitType/splitDetail 必须严格互逆，否则运营列表会把整串当详情显示。 前端 {@code ui/src/utils/report.ts} 的
 * REPORT_REASONS 与此处白名单一一对应，改一处必须同步另一处。
 * </p>
 *
 * @author twelvet
 */
public final class ReportReason {

	/** 全角冒号：compose 用它连接「标签：详情」，split 用它切分 */
	private static final char FULLWIDTH_COLON = '：';

	/** reason 列长度上限（report.reason varchar(500)） */
	private static final int MAX_LEN = 500;

	/** 原因编码 → 中文标签，LinkedHashMap 保持展示顺序 */
	private static final Map<String, String> LABELS = new LinkedHashMap<>();

	static {
		LABELS.put("spam", "垃圾广告 / 引流");
		LABELS.put("fraud", "欺诈 / 骗钱");
		LABELS.put("fake", "假冒伪劣 / 描述不符");
		LABELS.put("prohibited", "违禁品 / 违规内容");
		LABELS.put("abuse", "辱骂 / 骚扰");
		LABELS.put("other", "其他");
	}

	private ReportReason() {
	}

	/**
	 * 原因编码是否合法
	 * @param code 原因编码
	 * @return 合法返回 true
	 */
	public static boolean isValid(String code) {
		return code != null && !code.isEmpty() && LABELS.containsKey(code);
	}

	/**
	 * 编码 → 中文标签；未知编码返回「其他」
	 * @param code 原因编码
	 * @return 中文标签
	 */
	public static String labelOf(String code) {
		return LABELS.getOrDefault(code, LABELS.get("other"));
	}

	/**
	 * 合成 reason 列文本：有详情拼「标签：详情」，无详情只返回标签，总长截断到 500
	 * @param code 原因编码
	 * @param detail 补充说明（可空）
	 * @return 合成文本
	 */
	public static String compose(String code, String detail) {
		String label = labelOf(code);
		String text = label;
		if (detail != null && !detail.trim().isEmpty()) {
			text = label + FULLWIDTH_COLON + detail.trim();
		}
		return truncate(text);
	}

	/**
	 * 截断到 MAX_LEN，且不切断代理对
	 * <p>
	 * 直接 substring 按 UTF-16 char 计数，截断点落在 emoji 等代理对中间时会留下孤立的高位代理： 驱动无法把它编码进
	 * utf8mb4，写入报错；前端渲染则显示为乱码。退一位保证不切开。
	 * </p>
	 */
	private static String truncate(String text) {
		if (text.length() <= MAX_LEN) {
			return text;
		}
		int end = MAX_LEN;
		if (Character.isHighSurrogate(text.charAt(end - 1))) {
			end--;
		}
		return text.substring(0, end);
	}

	/**
	 * 拆出原因类型（合成文本冒号前的标签）；无冒号返回空串
	 * @param reason 合成文本
	 * @return 原因类型标签
	 */
	public static String splitType(String reason) {
		if (reason == null || reason.isEmpty()) {
			return "";
		}
		int idx = reason.indexOf(FULLWIDTH_COLON);
		return idx < 0 ? "" : reason.substring(0, idx);
	}

	/**
	 * 拆出补充说明（合成文本冒号后的内容）；无冒号时整串作为详情
	 * @param reason 合成文本
	 * @return 补充说明
	 */
	public static String splitDetail(String reason) {
		if (reason == null || reason.isEmpty()) {
			return "";
		}
		int idx = reason.indexOf(FULLWIDTH_COLON);
		return idx < 0 ? reason : reason.substring(idx + 1);
	}

}
