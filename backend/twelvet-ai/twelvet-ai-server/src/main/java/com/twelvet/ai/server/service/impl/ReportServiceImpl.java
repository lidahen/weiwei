package com.twelvet.ai.server.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twelvet.ai.api.domain.DailyReport;
import com.twelvet.ai.server.client.AiServiceClient;
import com.twelvet.ai.server.mapper.DailyReportMapper;
import com.twelvet.ai.server.service.IReportService;
import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.im.server.service.IImService;
import com.twelvet.system.api.domain.SysTenant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 社区日报 Service 实现
 * <p>
 * 设计：docs/AI社区日报设计.md
 * <ul>
 * <li>聚合口径：数据日期 00:00 ~ 次日 00:00（report_date = 数据所属日期，定时任务取昨日）</li>
 * <li>LLM 文案经 Python ai-service（feature=daily-report 计入 ai_usage_daily）， 超时/异常/429
 * 一律走模板兜底（source=fallback），日报照常生成推送</li>
 * <li>幂等：uk_report(tenant_id, report_date)，重复生成 UPDATE 覆盖</li>
 * <li>IM 推送逐用户 try/catch，失败名单记日志，不影响日报存档</li>
 * </ul>
 */
@Service
public class ReportServiceImpl implements IReportService {

	private static final Logger log = LoggerFactory.getLogger(ReportServiceImpl.class);

	private static final String SOURCE_LLM = "llm";

	private static final String SOURCE_FALLBACK = "fallback";

	private static final int TOP_PRODUCT_LIMIT = 3;

	private static final int HOT_FEED_LIMIT = 2;

	private static final int SUMMARY_LEN = 80;

	private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("M月d日");

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private final DailyReportMapper reportMapper;

	private final AiServiceClient aiServiceClient;

	private final IImService imService;

	public ReportServiceImpl(DailyReportMapper reportMapper, AiServiceClient aiServiceClient, IImService imService) {
		this.reportMapper = reportMapper;
		this.aiServiceClient = aiServiceClient;
		this.imService = imService;
	}

	@Override
	public int generateAll() {
		List<SysTenant> tenants = reportMapper.selectEnabledTenants();
		int count = 0;
		for (SysTenant tenant : tenants) {
			try {
				DailyReport report = generate(tenant.getId(), false);
				if (report != null) {
					count++;
				}
			}
			catch (Exception e) {
				// 单租户失败不影响其他租户（设计 §6）
				log.error("[日报] 租户 {} 生成失败: {}", tenant.getId(), e.getMessage(), e);
			}
		}
		return count;
	}

	@Override
	public DailyReport generate(Long tenantId, boolean manual) {
		return generate(tenantId, LocalDate.now().minusDays(1), manual);
	}

	@Override
	public DailyReport generate(Long tenantId, LocalDate reportDate, boolean manual) {
		SysTenant tenant = reportMapper.selectEnabledTenants()
			.stream()
			.filter(t -> tenantId.equals(t.getId()))
			.findFirst()
			.orElse(null);
		if (tenant == null) {
			log.warn("[日报] 租户 {} 不存在或未启用，跳过", tenantId);
			return null;
		}
		// Quartz 线程无租户上下文（IM 写库/跨租户校验依赖）；Web 手动触发已有上下文则不动它
		boolean ownsContext = TenantContext.get() == null;
		if (ownsContext) {
			TenantContext.set(tenantId);
		}
		try {
			return doGenerate(tenant, reportDate, manual);
		}
		finally {
			if (ownsContext) {
				TenantContext.clear();
			}
		}
	}

	@Override
	public List<DailyReport> selectPage(Long tenantId) {
		return reportMapper.selectPage(tenantId);
	}

	@Override
	public DailyReport selectByIdAndTenant(Long id, Long tenantId) {
		return reportMapper.selectByIdAndTenant(id, tenantId);
	}

	@Override
	public byte[] loadReportAudio(Long id, Long tenantId) {
		DailyReport report = reportMapper.selectByIdAndTenant(id, tenantId);
		if (report == null) {
			throw new TWTException("日报不存在或无权查看");
		}
		// 缓存键含 update_time：日报被覆盖生成后旧音频自动失效。
		// 目录取 java.io.tmpdir（不依赖进程工作目录，且不污染 backend/data 的 SQL 初始化目录）
		String stamp = report.getUpdateTime() != null ? report.getUpdateTime().toString()
				: String.valueOf(report.getId());
		java.io.File dir = new java.io.File(System.getProperty("java.io.tmpdir"), "twt-report-audio");
		if (!dir.exists() && !dir.mkdirs()) {
			log.warn("[日报] 语音缓存目录创建失败: {}", dir.getAbsolutePath());
		}
		java.io.File cache = new java.io.File(dir, "report-" + report.getId() + "-" + stamp + ".mp3");
		if (cache.isFile() && cache.length() > 0) {
			try {
				return java.nio.file.Files.readAllBytes(cache.toPath());
			}
			catch (Exception e) {
				log.warn("[日报] 读取语音缓存失败: {}", e.getMessage());
			}
		}

		// 播报稿：问候 + 日期 + 剥掉 Markdown 的正文（TTS 上游限长，Java 侧先截到 900 字）
		String name = reportMapper.selectEnabledTenants()
			.stream()
			.filter(t -> tenantId.equals(t.getId()))
			.findFirst()
			.map(SysTenant::getTenantName)
			.orElse("");
		String greeting = "早上好！这里是" + (name.isBlank() ? "社区" : name) + "晨间广播。" + report.getReportDate().format(MD)
				+ "，来看看社区的新鲜事。";
		String text = greeting + plainSummary(report.getContent());
		if (text.length() > 900) {
			text = text.substring(0, 900);
		}

		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("X-Tenant-Id", String.valueOf(tenantId));
		ResponseEntity<byte[]> resp = aiServiceClient.postForBytes("/ai/chat/tts", Map.of("text", text), headers);
		if (AiServiceClient.isJsonResponse(resp)) {
			// TTS 未配置 / 上游失败：Python 返回 ApiResponse JSON
			throw new TWTException(AiServiceClient.jsonErrorMessage(resp));
		}
		byte[] audio = resp.getBody();
		if (audio == null || audio.length == 0) {
			throw new TWTException("语音合成失败，请稍后重试");
		}
		// 写缓存并清理同日报旧版本的缓存文件
		try {
			java.nio.file.Files.write(cache.toPath(), audio);
			java.io.File[] stale = dir
				.listFiles((d, fn) -> fn.startsWith("report-" + report.getId() + "-") && !fn.equals(cache.getName()));
			if (stale != null) {
				for (java.io.File f : stale) {
					f.delete();
				}
			}
		}
		catch (Exception e) {
			log.warn("[日报] 语音缓存写入失败: {}", e.getMessage());
		}
		return audio;
	}

	private DailyReport doGenerate(SysTenant tenant, LocalDate reportDate, boolean manual) {
		LocalDateTime startTime = reportDate.atStartOfDay();
		LocalDateTime endTime = reportDate.plusDays(1).atStartOfDay();

		Map<String, Object> stats = reportMapper.selectAggStats(tenant.getId(), tenant.getTenantCode(), startTime,
				endTime);
		List<Map<String, Object>> topProducts = reportMapper.selectTopProducts(tenant.getId(), startTime, endTime,
				TOP_PRODUCT_LIMIT);
		List<Map<String, Object>> hotFeeds = reportMapper.selectHotFeeds(tenant.getId(), startTime, endTime,
				HOT_FEED_LIMIT);
		if (stats == null) {
			stats = new LinkedHashMap<>();
		}
		stats.put("topProducts", topProducts);
		stats.put("hotFeeds", hotFeeds);

		// 零活动租户：定时路径跳过生成省配额（设计 §2）；手动触发照常生成
		if (!manual && isZeroActivity(stats)) {
			log.info("[日报] 租户 {}({}) {} 零活动，跳过生成", tenant.getId(), tenant.getTenantCode(), reportDate);
			return null;
		}

		String statsJson = toJson(stats);

		String title;
		String content;
		String source;
		Map<String, Object> llmResult = callLlm(tenant, stats, reportDate);
		if (llmResult != null) {
			title = String.valueOf(llmResult.getOrDefault("title", ""));
			content = String.valueOf(llmResult.getOrDefault("content", ""));
			source = SOURCE_LLM;
		}
		else {
			String name = tenant.getTenantName() == null ? "" : tenant.getTenantName();
			title = name + "社区日报 · " + reportDate.format(MD);
			content = buildFallbackContent(stats, topProducts, hotFeeds, reportDate);
			source = SOURCE_FALLBACK;
		}

		DailyReport report = new DailyReport();
		report.setTenantId(tenant.getId());
		report.setReportDate(reportDate);
		report.setTitle(title);
		report.setContent(content);
		report.setStatsJson(statsJson);
		report.setSource(source);

		DailyReport existing = reportMapper.selectByTenantAndDate(tenant.getId(), reportDate);
		if (existing != null) {
			report.setId(existing.getId());
			reportMapper.updateByTenantAndDate(report);
		}
		else {
			reportMapper.insert(report);
		}

		pushToTenantUsers(tenant.getId(), title, content);
		log.info("[日报] 租户 {}({}) {} 生成完成 source={}", tenant.getId(), tenant.getTenantCode(), reportDate, source);
		return report;
	}

	/**
	 * 调 Python ai-service 生成 LLM 文案；任何失败（超时/异常/429 配额超限）返回 null → 模板兜底
	 */
	@SuppressWarnings("unchecked")
	private Map<String, Object> callLlm(SysTenant tenant, Map<String, Object> stats, LocalDate reportDate) {
		try {
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("stats", stats);
			body.put("tenantName", tenant.getTenantName());
			body.put("date", reportDate.toString());
			Map<String, String> headers = new LinkedHashMap<>();
			headers.put("X-Tenant-Id", String.valueOf(tenant.getId()));
			Map<String, Object> data = aiServiceClient.post("/ai/assist/daily-report", body, Map.class, headers);
			if (data != null && data.get("content") != null && !String.valueOf(data.get("content")).isBlank()) {
				return data;
			}
			log.warn("[日报] 租户 {} LLM 返回空内容，走模板兜底", tenant.getId());
		}
		catch (Exception e) {
			// 含 QuotaExceededException（429 配额超限）：日报照常生成（设计 §5/§6）
			log.warn("[日报] 租户 {} LLM 调用失败，走模板兜底: {}", tenant.getId(), e.getMessage());
		}
		return null;
	}

	/**
	 * 零活动判定：核心计数全为 0（浏览/热帖等明细列表天然为空）
	 */
	private boolean isZeroActivity(Map<String, Object> stats) {
		return num(stats.get("newProducts")) == 0 && num(stats.get("doneOrders")) == 0
				&& num(stats.get("newFeeds")) == 0 && num(stats.get("runnerDoneToday")) == 0
				&& num(stats.get("runnerNewReviews")) == 0;
	}

	private long num(Object v) {
		if (v instanceof Number n) {
			return n.longValue();
		}
		try {
			return v == null ? 0 : Long.parseLong(String.valueOf(v));
		}
		catch (NumberFormatException e) {
			return 0;
		}
	}

	/**
	 * 模板兜底文案：纯数字罗列，结构与 LLM 输出一致（设计 §6）
	 */
	private String buildFallbackContent(Map<String, Object> stats, List<Map<String, Object>> topProducts,
			List<Map<String, Object>> hotFeeds, LocalDate date) {
		StringBuilder sb = new StringBuilder();
		sb.append("## 一句话总览\n");
		sb.append(
				String.format("%s：新上架 %d 件、成交 %d 单、新帖 %d 条、互助完成 %d 单。", date.format(MD), num(stats.get("newProducts")),
						num(stats.get("doneOrders")), num(stats.get("newFeeds")), num(stats.get("runnerDoneToday"))));
		sb.append("\n\n## 二手好物\n");
		if (topProducts.isEmpty()) {
			sb.append("今天很安静，快去发布闲置吧。\n");
		}
		else {
			for (Map<String, Object> p : topProducts) {
				sb.append("- ").append(p.get("title")).append("（").append(num(p.get("views"))).append(" 次浏览）\n");
			}
		}
		sb.append("\n## 邻里互助\n");
		if (num(stats.get("runnerDoneToday")) == 0 && num(stats.get("runnerNewReviews")) == 0) {
			sb.append("今天没有互助任务，大家有需要随时发布。\n");
		}
		else {
			sb.append(String.format("完成互助 %d 单，新增好评 %d 条（均分 %s），累计已完成 %d 单。", num(stats.get("runnerDoneToday")),
					num(stats.get("runnerNewReviews")), String.valueOf(stats.get("runnerAvgRating")),
					num(stats.get("runnerDoneTotal"))));
			sb.append("\n");
		}
		sb.append("\n## 社区动态\n");
		if (hotFeeds.isEmpty()) {
			sb.append("今天没有热帖，去广场发一条让大家认识你吧。\n");
		}
		else {
			for (Map<String, Object> f : hotFeeds) {
				sb.append("- ").append(f.get("summary")).append("（").append(num(f.get("likes"))).append(" 赞）\n");
			}
		}
		return sb.toString();
	}

	/**
	 * IM 推送：复用 IImService.sendMessage（system 通道，未读计数自动生效）。 发送者取本租户第一个在职用户；逐用户
	 * try/catch，失败名单记日志。
	 */
	private void pushToTenantUsers(Long tenantId, String title, String content) {
		Long senderId = reportMapper.selectSenderUserId(tenantId);
		if (senderId == null) {
			log.warn("[日报] 租户 {} 无在职用户，跳过 IM 推送", tenantId);
			return;
		}
		String summary = title + "\n" + plainSummary(content) + "\n— 打开「AI 应用 → 社区日报」查看全文，还有 AI 主播语音播报哦";
		List<Long> userIds = reportMapper.selectPushUserIds(tenantId);
		List<Long> failed = new ArrayList<>();
		for (Long userId : userIds) {
			try {
				imService.sendMessage(senderId, userId, null, "system", summary);
			}
			catch (Exception e) {
				failed.add(userId);
				log.warn("[日报] 租户 {} IM 推送失败 userId={}: {}", tenantId, userId, e.getMessage());
			}
		}
		log.info("[日报] 租户 {} IM 推送完成：成功 {} 人，失败 {} 人{}", tenantId, userIds.size() - failed.size(), failed.size(),
				failed.isEmpty() ? "" : " 失败名单=" + failed);
	}

	/**
	 * 摘要取正文前 80 字：剥掉 Markdown 标记与小节标题
	 */
	private String plainSummary(String content) {
		String plain = content == null ? ""
				: content.replaceAll("(?m)^#{1,6}\\s*", "").replaceAll("[*_`>-]", "").replaceAll("\\s+", " ").trim();
		return plain.length() > SUMMARY_LEN ? plain.substring(0, SUMMARY_LEN) + "…" : plain;
	}

	private String toJson(Map<String, Object> stats) {
		try {
			return OBJECT_MAPPER.writeValueAsString(stats);
		}
		catch (Exception e) {
			// stats_json 兜底：保底空对象，不影响日报存档
			log.warn("[日报] 指标序列化失败: {}", e.getMessage());
			return "{}";
		}
	}

}
