package com.twelvet.trade.server.service.impl;

import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.product.server.service.IReportService;
import com.twelvet.trade.server.mapper.AdminMarketplaceMapper;
import com.twelvet.trade.server.service.IAdminMarketplaceService;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.framework.jdbc.web.utils.PageUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 运营管理Service业务层处理
 *
 * @author twelvet
 */
@Service
public class AdminMarketplaceServiceImpl implements IAdminMarketplaceService {

	private static final Logger log = LoggerFactory.getLogger(AdminMarketplaceServiceImpl.class);

	private static final Long DEFAULT_AUDITOR_ID = 103L;

	/** 运营指标允许的统计窗口，其它值一律回落为 30 天，避免 SQL 里的天数被任意传参 */
	private static final Set<Integer> ALLOWED_WINDOWS = Set.of(7, 30, 90);

	private static final int DEFAULT_WINDOW = 30;

	private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MM-dd");

	private static final DateTimeFormatter DAY_KEY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	/** 已付款订单状态：成交口径（排除未付款 unpaid 与超时关闭 closed） */
	private static final String[] PAID_STATUSES = { "unshipped", "shipped", "done" };

	private final AdminMarketplaceMapper adminMapper;

	private final IReportService reportService;

	public AdminMarketplaceServiceImpl(AdminMarketplaceMapper adminMapper, IReportService reportService) {
		this.adminMapper = adminMapper;
		this.reportService = reportService;
	}

	/**
	 * 当前租户ID（未识别租户时为 null，XML 中 if 不命中，租户过滤不生效——零侵入回滚）
	 */
	private Long currentTid() {
		return TenantContext.get();
	}

	/**
	 * 仪表盘概览
	 */
	@Override
	public Map<String, Object> selectDashboardOverview() {
		Long tid = currentTid();
		Map<String, Object> stats = adminMapper.selectDashboardStats(tid);
		Map<String, Object> result = new HashMap<>(stats);
		// 近 7 日真实 GMV / 成交 / 发布趋势
		result.put("trend", buildTrend(tid, 7));
		// 类目分布
		List<Map<String, Object>> categoryDist = adminMapper.selectCategoryDistribution(tid);
		result.put("categoryDist", categoryDist);
		return result;
	}

	/**
	 * 自营运营核心指标（阶段一验证口径）
	 */
	@Override
	public Map<String, Object> selectOperationMetrics(int days) {
		int window = ALLOWED_WINDOWS.contains(days) ? days : DEFAULT_WINDOW;
		Long tid = currentTid();

		Map<String, Object> raw = adminMapper.selectOperationMetrics(tid, window);
		if (raw == null) {
			raw = new HashMap<>();
		}

		long publishCount = toLong(raw.get("publishCount"));
		long publishToday = toLong(raw.get("publishToday"));
		long dealCount = toLong(raw.get("dealCount"));
		BigDecimal dealGmv = toDecimal(raw.get("dealGmv"));
		long doneCount = toLong(raw.get("doneCount"));
		long buyerCount = toLong(raw.get("buyerCount"));
		long repeatBuyerCount = toLong(raw.get("repeatBuyerCount"));
		long disputeCount = toLong(raw.get("disputeCount"));
		long overdueUnshipped = toLong(raw.get("overdueUnshippedCount"));
		long unpaidCount = toLong(raw.get("unpaidCount"));

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("days", window);

		// ① 发布量：衡量供给端是否在动（阶段一最核心的先行指标）
		result.put("publishCount", publishCount);
		result.put("publishToday", publishToday);
		result.put("publishPerDay", round1(publishCount / (double) window));

		// ② 成交量 / GMV
		result.put("dealCount", dealCount);
		result.put("dealGmv", dealGmv);
		result.put("dealPerDay", round1(dealCount / (double) window));

		// ③ 履约率 = 已完成 / 成交（分母为 0 时给 null，前端展示"—"而不是 0%）
		result.put("doneCount", doneCount);
		result.put("fulfillmentRate", rate(doneCount, dealCount));
		result.put("overdueUnshippedCount", overdueUnshipped);
		result.put("unpaidCount", unpaidCount);

		// ④ 复购率 = 窗口内成交 ≥2 笔的买家 / 成交买家数
		result.put("buyerCount", buyerCount);
		result.put("repeatBuyerCount", repeatBuyerCount);
		result.put("repurchaseRate", rate(repeatBuyerCount, buyerCount));

		// ⑤ 纠纷率 = 窗口内新建纠纷工单 / 成交订单数
		result.put("disputeCount", disputeCount);
		result.put("disputeRate", rate(disputeCount, dealCount));
		// 每百单纠纷数：比百分比更适合早期低量场景（0.5% 和 0% 用百分比看不出差别）
		result.put("disputePer100", dealCount > 0 ? round1(disputeCount * 100.0 / dealCount) : null);

		// 趋势（窗口内按日，缺口补零）
		result.put("trend", buildTrend(tid, window));

		// 口径说明：直接下发给前端做 tooltip，避免运营对不上数时靠猜
		result.put("definitions", buildDefinitions());
		return result;
	}

	/**
	 * 口径说明（与前端 tooltip 一一对应）
	 */
	private Map<String, Object> buildDefinitions() {
		Map<String, Object> def = new LinkedHashMap<>();
		def.put("publish", "窗口内首次上架的商品数（草稿/待审不计）");
		def.put("deal", "窗口内已付款订单数，状态 " + String.join("/", PAID_STATUSES) + "（不含未付款与超时关闭）");
		def.put("fulfillment", "已完成订单数 ÷ 成交订单数");
		def.put("repurchase", "窗口内成交 ≥2 笔的买家数 ÷ 窗口内成交买家数");
		def.put("dispute", "窗口内新建纠纷工单数 ÷ 成交订单数");
		def.put("overdue", "已付款超过 72 小时仍未发货的订单数");
		return def;
	}

	/**
	 * 构建按日趋势，缺失日期补零（避免前端图表断线）
	 */
	private List<Map<String, Object>> buildTrend(Long tenantId, int days) {
		Map<String, Long> orderByDay = new HashMap<>();
		Map<String, BigDecimal> gmvByDay = new HashMap<>();
		Map<String, Long> publishByDay = new HashMap<>();

		for (Map<String, Object> row : safeList(adminMapper.selectDailyOrderTrend(tenantId, days))) {
			String key = String.valueOf(row.get("statDate"));
			orderByDay.put(key, toLong(row.get("orderCount")));
			gmvByDay.put(key, toDecimal(row.get("gmv")));
		}
		for (Map<String, Object> row : safeList(adminMapper.selectDailyPublishTrend(tenantId, days))) {
			publishByDay.put(String.valueOf(row.get("statDate")), toLong(row.get("publishCount")));
		}

		List<Map<String, Object>> trend = new ArrayList<>(days);
		LocalDate today = LocalDate.now();
		for (int i = days - 1; i >= 0; i--) {
			LocalDate day = today.minusDays(i);
			String key = day.format(DAY_KEY);
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("date", day.format(DAY_LABEL));
			item.put("amount", gmvByDay.getOrDefault(key, BigDecimal.ZERO));
			item.put("orderCount", orderByDay.getOrDefault(key, 0L));
			item.put("publishCount", publishByDay.getOrDefault(key, 0L));
			trend.add(item);
		}
		return trend;
	}

	private List<Map<String, Object>> safeList(List<Map<String, Object>> list) {
		return list == null ? Collections.emptyList() : list;
	}

	/**
	 * 比率（百分数，保留 1 位小数）；分母为 0 返回 null，前端显示"—"
	 */
	private Double rate(long numerator, long denominator) {
		if (denominator <= 0) {
			return null;
		}
		return round1(numerator * 100.0 / denominator);
	}

	private Double round1(double value) {
		return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
	}

	private long toLong(Object value) {
		if (value == null) {
			return 0L;
		}
		if (value instanceof Number number) {
			return number.longValue();
		}
		try {
			return Long.parseLong(String.valueOf(value));
		}
		catch (NumberFormatException ex) {
			return 0L;
		}
	}

	private BigDecimal toDecimal(Object value) {
		if (value == null) {
			return BigDecimal.ZERO;
		}
		if (value instanceof BigDecimal decimal) {
			return decimal;
		}
		if (value instanceof Number number) {
			return BigDecimal.valueOf(number.doubleValue());
		}
		try {
			return new BigDecimal(String.valueOf(value));
		}
		catch (NumberFormatException ex) {
			return BigDecimal.ZERO;
		}
	}

	/**
	 * 商品审核列表（联表最新一条 AI 初审结果，支持 aiRisk 筛选）
	 */
	@Override
	public TableDataInfo<Map<String, Object>> selectProductAuditList(String status, String aiRisk) {
		// 分页：PageUtils 读取请求参数 current / pageSize（ProTable 默认传参）
		PageUtils.startPage();
		List<Map<String, Object>> list = adminMapper.selectProductAuditList(status, aiRisk, currentTid());
		return PageUtils.getDataTable(list);
	}

	/**
	 * 商品审核通过
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public String approveProduct(Long productId) {
		adminMapper.approveProduct(productId, currentTid());
		adminMapper.insertAuditPassLog(productId, DEFAULT_AUDITOR_ID);
		return "OK";
	}

	/**
	 * 商品审核驳回
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public String rejectProduct(Long productId, String reason) {
		if (reason == null || reason.trim().isEmpty()) {
			reason = "信息不全";
		}
		adminMapper.rejectProduct(productId, reason, currentTid());
		adminMapper.insertAuditRejectLog(productId, DEFAULT_AUDITOR_ID, reason);
		return "OK";
	}

	/**
	 * 举报列表
	 * <p>
	 * 分页在 IReportService.adminList 内完成 —— PageHelper 靠 ThreadLocal 拦截「下一条」SQL， 这里若先
	 * startPage 再跨 Service 调 Mapper 之外的查询，拦截位置会错乱。
	 * </p>
	 */
	@Override
	public TableDataInfo<Map<String, Object>> selectReportList(String status) {
		return reportService.adminList(status);
	}

	/**
	 * 处理举报
	 * <p>
	 * 处置动作、状态守卫、通知与踢会话都在 IReportService 内完成；这里不加事务 —— 事务边界在 adminHandle
	 * 上，套一层只会让「已提交」的判断失真。
	 * </p>
	 */
	@Override
	public String handleReport(Long reportId, String action, String note, Long handlerId) {
		reportService.adminHandle(reportId, action, note, handlerId);
		return "已处理";
	}

	/**
	 * 用户列表
	 */
	@Override
	public TableDataInfo<Map<String, Object>> selectUserList(String keyword) {
		PageUtils.startPage();
		List<Map<String, Object>> list = adminMapper.selectUserList(keyword, currentTid());
		return PageUtils.getDataTable(list);
	}

	/**
	 * 封禁/解封用户
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public String updateUserStatus(Long userId, Integer status) {
		adminMapper.updateUserStatus(userId, status, currentTid());
		return "OK";
	}

	/**
	 * 财务概览
	 */
	@Override
	public Map<String, Object> selectFinanceOverview() {
		Long tid = currentTid();
		Map<String, Object> stats = adminMapper.selectFinanceStats(tid);
		Map<String, Object> result = new HashMap<>(stats);
		List<Map<String, Object>> recentFlows = adminMapper.selectRecentPayFlows(20, tid);
		result.put("recentFlows", recentFlows);
		return result;
	}

}
