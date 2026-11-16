package com.twelvet.product.server.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.github.pagehelper.PageInfo;
import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.framework.jdbc.web.utils.PageUtils;
import com.twelvet.framework.utils.JacksonUtils;
import com.twelvet.im.server.service.IImService;
import com.twelvet.product.api.domain.Feed;
import com.twelvet.product.api.domain.Product;
import com.twelvet.product.server.domain.Report;
import com.twelvet.product.server.mapper.FeedMapper;
import com.twelvet.product.server.mapper.ProductMapper;
import com.twelvet.product.server.mapper.ReportMapper;
import com.twelvet.product.server.mapper.UserMapper;
import com.twelvet.product.server.service.IReportService;
import com.twelvet.product.server.service.ReportReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 举报 Service —— report 表业务唯一入口
 * <p>
 * bean 名显式声明为 {@code productReportServiceImpl}：本仓库 twelvet-ai-server 已有同名类
 * {@code com.twelvet.ai.server.service.impl.ReportServiceImpl}（社区日报）， FastApplication 扫全仓
 * com.twelvet 且未开 allow-bean-definition-overriding，两个裸 @Service 同名会在启动时抛
 * ConflictingBeanDefinitionException。与既有先例 {@code @Service("productFeedServiceImpl")}
 * 保持一致。
 * </p>
 *
 * @author twelvet
 */
@Service("productReportServiceImpl")
public class ReportServiceImpl implements IReportService {

	private static final Logger log = LoggerFactory.getLogger(ReportServiceImpl.class);

	private static final String TARGET_PRODUCT = "product";

	private static final String TARGET_FEED = "feed";

	private static final String TARGET_USER = "user";

	private static final String STATUS_PENDING = "pending";

	private static final String ACTION_DISMISS = "dismiss";

	private static final String ACTION_WARN = "warn";

	private static final String ACTION_REMOVE_PRODUCT = "remove_product";

	private static final String ACTION_REMOVE_FEED = "remove_feed";

	private static final String ACTION_BAN_USER = "ban_user";

	/** 可下架的商品状态：locked（已付款待发货）与 sold（已售）不可动，否则破坏 markSold 的状态机 */
	private static final Set<String> REMOVABLE_PRODUCT_STATUSES = Set.of("onsale", "auditing");

	private static final Set<String> VALID_ACTIONS = Set.of(ACTION_DISMISS, ACTION_WARN, ACTION_REMOVE_PRODUCT,
			ACTION_REMOVE_FEED, ACTION_BAN_USER);

	private static final int MAX_EVIDENCE_IMAGES = 3;

	private static final String NOTICE_TYPE = "platform";

	/** 运营/系统账号类型（sys_user.user_type）：举报封号不得作用于此类账号 */
	private static final String OPS_USER_TYPE = "00";

	private static final String MSG_TARGET_GONE = "举报对象不存在或已删除";

	private static final String NOTE_TARGET_GONE = "[对象已不存在，处置未执行]";

	/** 复用同一个 TypeReference（见 firstImage） */
	private static final TypeReference<List<String>> IMAGE_LIST_TYPE = new TypeReference<>() {
	};

	private final ReportMapper reportMapper;

	private final ProductMapper productMapper;

	private final FeedMapper feedMapper;

	private final UserMapper userMapper;

	private final IImService imService;

	public ReportServiceImpl(ReportMapper reportMapper, ProductMapper productMapper, FeedMapper feedMapper,
			UserMapper userMapper, IImService imService) {
		this.reportMapper = reportMapper;
		this.productMapper = productMapper;
		this.feedMapper = feedMapper;
		this.userMapper = userMapper;
		this.imService = imService;
	}

	/**
	 * 当前租户ID（未识别租户时为 null，XML 中 if 不命中，租户过滤不生效——零侵入回滚）
	 */
	private Long currentTid() {
		return TenantContext.get();
	}

	// ====== 提交 ======

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void submit(String targetType, Long targetId, Long reporterId, String reasonType, String reasonDetail,
			List<String> evidenceImages) {
		if (!TARGET_PRODUCT.equals(targetType) && !TARGET_FEED.equals(targetType) && !TARGET_USER.equals(targetType)) {
			throw new TWTException("举报对象类型不合法");
		}
		// 必须在查目标之前拦：user 分支走 userMapper.selectByIds(List.of(targetId))，
		// List.of(null) 抛 NPE，会落进全局 Exception 分支返回 500，把参数错误伪装成服务端故障
		if (targetId == null) {
			throw new TWTException("举报对象ID不合法");
		}
		if (!ReportReason.isValid(reasonType)) {
			throw new TWTException("举报原因不合法");
		}
		if (evidenceImages != null && evidenceImages.size() > MAX_EVIDENCE_IMAGES) {
			throw new TWTException("证据图最多 " + MAX_EVIDENCE_IMAGES + " 张");
		}

		Target target = loadTarget(targetType, targetId);
		if (!target.exists) {
			throw new TWTException(MSG_TARGET_GONE);
		}
		if (Objects.equals(target.ownerId, reporterId)) {
			throw new TWTException("不能举报自己发布的内容");
		}

		String reason = ReportReason.compose(reasonType, reasonDetail);
		String evidenceJson = (evidenceImages == null || evidenceImages.isEmpty()) ? null
				: JacksonUtils.toJson(evidenceImages);

		Report exist = reportMapper.selectByReporterAndTarget(reporterId, targetType, targetId);
		if (exist != null) {
			if (STATUS_PENDING.equals(exist.getStatus())) {
				throw new TWTException("你已举报过该内容，正在处理中");
			}
			// 已处理过：复活同一行，避免唯一键冲突，也让运营重新看到。
			// 带 status='done' 守卫：并发下运营可能刚 markHandled，此时复活会影响 0 行
			if (reportMapper.revive(exist.getReportId(), reason, evidenceJson) == 0) {
				throw new TWTException("你已举报过该内容，正在处理中");
			}
			return;
		}

		Report report = new Report();
		report.setReporterId(reporterId);
		report.setTargetType(targetType);
		report.setTargetId(targetId);
		report.setReason(reason);
		report.setEvidenceImages(evidenceJson);
		try {
			reportMapper.insert(report);
		}
		catch (DuplicateKeyException e) {
			// 并发双击/重试：两个请求都在 SELECT 阶段读到 null，一个 INSERT 成功，另一个撞
			// uk_reporter_target。不翻译的话会落进全局 Exception 分支返回 500，
			// 响应体是驱动的原始报错（含表名、键名、SQL 片段）。
			// 仓库既有先例：ExchangeReviewServiceImpl、RunnerReviewServiceImpl、LoginServiceImpl
			throw new TWTException("你已举报过该内容，正在处理中");
		}
	}

	// ====== 运营列表 ======

	@Override
	public TableDataInfo<Map<String, Object>> adminList(String status) {
		// PageHelper 靠 ThreadLocal 拦截「下一条」SQL，startPage 必须紧贴 selectList
		PageUtils.startPage();
		String statusFilter = "all".equals(status) ? null : status;
		List<Report> reports = reportMapper.selectList(statusFilter, currentTid());
		// 立刻取 total：下面会把 reports 重映射成新 list，Page 包装随之丢失
		long total = new PageInfo<>(reports).getTotal();

		List<Map<String, Object>> rows = buildRows(reports);
		return TableDataInfo.page(rows, total);
	}

	private List<Map<String, Object>> buildRows(List<Report> reports) {
		if (reports == null || reports.isEmpty()) {
			return new ArrayList<>();
		}
		Long tid = currentTid();

		// LinkedHashSet 保持首次出现顺序：既便于断言，也让 IN 查询参数稳定
		Set<Long> productIds = new LinkedHashSet<>();
		Set<Long> feedIds = new LinkedHashSet<>();
		Set<Long> userIds = new LinkedHashSet<>();
		for (Report r : reports) {
			if (r.getReporterId() != null) {
				userIds.add(r.getReporterId());
			}
			if (r.getHandlerId() != null) {
				userIds.add(r.getHandlerId());
			}
			if (TARGET_PRODUCT.equals(r.getTargetType())) {
				productIds.add(r.getTargetId());
			}
			else if (TARGET_FEED.equals(r.getTargetType())) {
				feedIds.add(r.getTargetId());
			}
			else if (TARGET_USER.equals(r.getTargetType())) {
				// 用户既是目标也是归属人，一次查询同时供摘要与昵称使用
				userIds.add(r.getTargetId());
			}
		}

		// 空集合不能调 Mapper：MyBatis 的 IN () 是 SQL 语法错误
		Map<Long, Product> productMap = productIds.isEmpty() ? Map.of()
				: productMapper.selectByIds(new ArrayList<>(productIds), tid)
					.stream()
					.collect(Collectors.toMap(Product::getProductId, p -> p, (a, b) -> a));
		Map<Long, Feed> feedMap = feedIds.isEmpty() ? Map.of()
				: feedMapper.selectByIds(new ArrayList<>(feedIds), tid)
					.stream()
					.collect(Collectors.toMap(Feed::getFeedId, f -> f, (a, b) -> a));
		Map<Long, Map<String, Object>> userMap = userIds.isEmpty() ? Map.of()
				: userMapper.selectByIds(new ArrayList<>(userIds))
					.stream()
					.collect(Collectors.toMap(m -> toLong(m.get("user_id")), m -> m, (a, b) -> a));

		List<Map<String, Object>> rows = new ArrayList<>(reports.size());
		for (Report r : reports) {
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("reportId", r.getReportId());
			row.put("targetType", r.getTargetType());
			row.put("targetId", r.getTargetId());
			row.put("status", r.getStatus());
			row.put("action", r.getAction());
			row.put("handleNote", r.getHandleNote());
			row.put("handleTime", r.getHandleTime());
			row.put("evidenceImages", r.getEvidenceImages());
			row.put("reasonType", ReportReason.splitType(r.getReason()));
			row.put("reasonDetail", ReportReason.splitDetail(r.getReason()));
			row.put("reporterId", r.getReporterId());
			row.put("reporterNickName", nickName(userMap, r.getReporterId()));
			row.put("handlerId", r.getHandlerId());
			row.put("handlerNickName", nickName(userMap, r.getHandlerId()));

			fillTarget(row, r, productMap, feedMap, userMap);
			rows.add(row);
		}
		return rows;
	}

	private void fillTarget(Map<String, Object> row, Report r, Map<Long, Product> productMap, Map<Long, Feed> feedMap,
			Map<Long, Map<String, Object>> userMap) {
		Long targetId = r.getTargetId();
		if (TARGET_PRODUCT.equals(r.getTargetType())) {
			Product p = productMap.get(targetId);
			row.put("targetExists", p != null);
			if (p != null) {
				row.put("targetTitle", p.getTitle());
				row.put("targetCover", p.getCover());
				row.put("targetExtra", "¥" + p.getPrice() + " · " + p.getStatus());
				row.put("ownerId", p.getSellerId());
				row.put("ownerNickName", nickName(userMap, p.getSellerId()));
			}
			else {
				fillMissingTarget(row);
			}
			return;
		}
		if (TARGET_FEED.equals(r.getTargetType())) {
			Feed f = feedMap.get(targetId);
			// exists 与摘要必须由同一套规则决定（与 fromFeed 对齐）：软删动态 exists=false，
			// 但仍保留摘要，让运营知道当初处置的是哪条内容。两者若分叉，
			// 列表会显示「已删除」标签却带着完整内容，而处置路径认为对象根本不存在。
			boolean feedExists = f != null && !"deleted".equals(f.getStatus());
			row.put("targetExists", feedExists);
			if (f != null) {
				row.put("targetTitle", truncate(f.getContent(), 80));
				row.put("targetCover", firstImage(f.getImages()));
				row.put("targetExtra", f.getAuditStatus());
				row.put("ownerId", f.getUserId());
				row.put("ownerNickName", nickName(userMap, f.getUserId()));
			}
			else {
				fillMissingTarget(row);
			}
			return;
		}
		// user
		Map<String, Object> u = userMap.get(targetId);
		row.put("targetExists", u != null && "0".equals(String.valueOf(u.get("del_flag"))));
		if (u != null) {
			row.put("targetTitle", u.get("nick_name"));
			row.put("targetCover", u.get("avatar"));
			row.put("targetExtra", u.get("status"));
			row.put("ownerId", targetId);
			row.put("ownerNickName", u.get("nick_name"));
		}
		else {
			fillMissingTarget(row);
		}
	}

	private void fillMissingTarget(Map<String, Object> row) {
		row.put("targetTitle", null);
		row.put("targetCover", null);
		row.put("targetExtra", null);
		row.put("ownerId", null);
		row.put("ownerNickName", null);
	}

	// ====== 处理 ======

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void adminHandle(Long reportId, String action, String note, Long handlerId) {
		Report report = reportMapper.selectById(reportId, currentTid());
		if (report == null) {
			throw new TWTException("举报不存在");
		}
		if (!VALID_ACTIONS.contains(action)) {
			throw new TWTException("处理动作不合法");
		}
		if (ACTION_REMOVE_PRODUCT.equals(action) && !TARGET_PRODUCT.equals(report.getTargetType())) {
			throw new TWTException("该处理动作不适用于此举报对象");
		}
		if (ACTION_REMOVE_FEED.equals(action) && !TARGET_FEED.equals(report.getTargetType())) {
			throw new TWTException("该处理动作不适用于此举报对象");
		}

		String baseNote = note == null ? "" : note;
		Target target = loadTargetByIds(report.getTargetType(), report.getTargetId());
		// 确定性的前置校验先做：这类拒绝不该白抢一次状态再回滚
		validateAction(action, target);

		// 先抢占状态再动业务数据：WHERE status='pending' 让并发双击只有一次成功。
		// 顺序反过来（先改数据后抢状态）时，输的那一方已经写过 product/sys_user 并持有行锁，
		// 只能靠事务回滚兜底 —— 一旦 applyAction 里出现不可回滚的副作用就会泄漏。
		int rows = reportMapper.markHandled(reportId, action, baseNote, handlerId);
		if (rows == 0) {
			throw new TWTException("该举报已被处理");
		}

		boolean applied = applyAction(action, report, target);
		String finalNote = baseNote;
		if (!applied) {
			// 处置没生效（对象已被删或状态已变）：如实记录，不让运营以为已经处理掉了
			finalNote = baseNote.isEmpty() ? NOTE_TARGET_GONE : baseNote + " " + NOTE_TARGET_GONE;
			reportMapper.updateHandleNote(reportId, finalNote);
		}

		Long ownerId = target.ownerId;
		String summary = target.title == null ? "该内容" : truncate(target.title, 40);
		String reasonLabel = ReportReason.splitType(report.getReason());
		notifyAfterCommit(report.getReporterId(), action, summary, reasonLabel, finalNote);
		if (ownerId != null && !ACTION_DISMISS.equals(action)) {
			notifyAfterCommit(ownerId, action, summary, reasonLabel, finalNote);
		}
		if (ACTION_BAN_USER.equals(action) && ownerId != null) {
			kickoutAfterCommit(ownerId);
		}
	}

	/**
	 * 处置动作的前置校验（确定性拒绝，与并发无关）
	 * <p>
	 * 与 applyAction 分开的理由：这些拒绝是「动作本身不允许」，应该在抢状态之前抛出， 否则一次注定失败的请求会先把举报置成 done
	 * 再靠回滚撤销，白做一次写入并多持一次行锁。
	 * </p>
	 */
	private void validateAction(String action, Target target) {
		switch (action) {
			case ACTION_REMOVE_PRODUCT -> {
				if (target.exists && !REMOVABLE_PRODUCT_STATUSES.contains(target.status)) {
					throw new TWTException("该商品存在进行中订单或已售，请走纠纷处理流程");
				}
			}
			case ACTION_BAN_USER -> {
				// 运营/系统账号（user_type=00）不走举报封号：封掉运营会让后台无人可用，
				// 且此类账号本应通过系统管理页处置
				if (target.exists && OPS_USER_TYPE.equals(target.ownerUserType)) {
					throw new TWTException("运营账号不可通过举报处置停用");
				}
			}
			default -> {
				// dismiss / warn / remove_feed 无确定性前置条件
			}
		}
	}

	/**
	 * 执行处置动作
	 * <p>
	 * 三条硬约束：
	 * <ol>
	 * <li>状态守卫必须下沉到 SQL，不能只在 Java 侧读完再无条件写（读-改之间有并发窗口，商品可能已被下单锁定）</li>
	 * <li>必须检查影响行数。0 行说明处置实际没生效（行已被删、租户不匹配、状态已变），此时若返回 true 就会给双方发出「已下架 / 已停用」的假通知</li>
	 * <li>不可回滚的副作用一律不做，全部留给 afterCommit（见 notifyAfterCommit / kickoutAfterCommit）</li>
	 * </ol>
	 * @return true 表示处置已生效；false 表示对象已不存在（调用方追加备注但不阻断）
	 */
	private boolean applyAction(String action, Report report, Target target) {
		switch (action) {
			case ACTION_REMOVE_PRODUCT -> {
				if (!target.exists) {
					return false;
				}
				// 前置状态校验已在 validateAction 做过；这里 0 行只可能是「读到 onsale 之后、
				// UPDATE 之前被下单锁定」的并发窗口 —— 按不可下架处理，绝不能报成功
				int rows = productMapper.updateStatusIfIn(report.getTargetId(), "offshelf",
						new ArrayList<>(REMOVABLE_PRODUCT_STATUSES), currentTid());
				if (rows == 0) {
					throw new TWTException("该商品存在进行中订单或已售，请走纠纷处理流程");
				}
				return true;
			}
			case ACTION_REMOVE_FEED -> {
				if (!target.exists) {
					return false;
				}
				// 0 行 = 动态已被并发删除，处置没有真正发生
				if (feedMapper.updateStatusIfNot(report.getTargetId(), "deleted", "rejected", "deleted",
						currentTid()) == 0) {
					throw new TWTException("处置未生效：该动态状态已变更，请刷新后重试");
				}
				return true;
			}
			case ACTION_BAN_USER -> {
				if (target.ownerId == null) {
					return false;
				}
				// 运营账号保护已在 validateAction 做过。
				// 0 行 = 账号已软删或租户不匹配，封号没有真正发生；
				// 不能记成功：否则会给双方发「账号已被停用」的假通知，而账号其实安然无恙
				if (userMapper.updateStatus(target.ownerId, 1, currentTid()) == 0) {
					throw new TWTException("处置未生效：该账号状态已变更，请刷新后重试");
				}
				return true;
			}
			default -> {
				// dismiss / warn 不改业务数据
				return true;
			}
		}
	}

	/**
	 * 事务提交后执行：通知必须等处置落库完成，否则回滚时用户已收到错误消息
	 */
	private void notifyAfterCommit(Long userId, String action, String summary, String reasonLabel, String note) {
		runAfterCommit(() -> {
			try {
				imService.sendNotice(userId, NOTICE_TYPE, noticeTitle(action),
						noticeContent(action, summary, reasonLabel, note));
			}
			catch (Exception e) {
				// 通知失败不影响处置结果（事务已提交）
				log.warn("[举报] 通知用户 {} 失败: {}", userId, e.getMessage());
			}
		});
	}

	private void kickoutAfterCommit(Long ownerId) {
		runAfterCommit(() -> {
			try {
				// 登录接口只在登录时刻校验 status，已发放 token 有效期 86400s —— 不踢则封号最长 24h 不生效
				StpUtil.kickout(ownerId);
			}
			catch (Exception e) {
				log.warn("[举报] 踢下线用户 {} 失败: {}", ownerId, e.getMessage());
			}
		});
	}

	private void runAfterCommit(Runnable task) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					task.run();
				}
			});
		}
		else {
			task.run();
		}
	}

	private String noticeTitle(String action) {
		return switch (action) {
			case ACTION_DISMISS -> "举报已处理";
			case ACTION_WARN -> "收到警告";
			case ACTION_REMOVE_PRODUCT -> "商品被下架";
			case ACTION_REMOVE_FEED -> "动态被删除";
			case ACTION_BAN_USER -> "账号已被停用";
			default -> "举报已处理";
		};
	}

	private String noticeContent(String action, String summary, String reasonLabel, String note) {
		String reason = (reasonLabel == null || reasonLabel.isEmpty()) ? "违规" : reasonLabel;
		String suffix = (note == null || note.isEmpty()) ? "" : "（" + note + "）";
		return switch (action) {
			case ACTION_DISMISS -> "你对「" + summary + "」的举报经核实未违规，已驳回" + suffix;
			case ACTION_WARN -> "「" + summary + "」因" + reason + "被警告，请自查" + suffix;
			case ACTION_REMOVE_PRODUCT -> "商品「" + summary + "」因" + reason + "被下架" + suffix;
			case ACTION_REMOVE_FEED -> "你的动态因" + reason + "被删除" + suffix;
			case ACTION_BAN_USER -> "账号因违规被停用，如有疑问请联系客服" + suffix;
			default -> "你对「" + summary + "」的举报已处理" + suffix;
		};
	}

	// ====== 目标查询 ======

	/**
	 * 查被举报对象（提交侧）：按主键单查，含归属人与摘要
	 * <p>
	 * product 硬删（行没了即不存在）；feed 软删（status='deleted' 视为不存在）； user 软删（del_flag != '0'
	 * 视为不存在）且需显式比对租户 —— product/feed 的 selectById 自带租户过滤，user 表查询不带。
	 * </p>
	 */
	private Target loadTarget(String targetType, Long targetId) {
		return switch (targetType) {
			case TARGET_PRODUCT -> fromProduct(productMapper.selectById(targetId, currentTid()));
			case TARGET_FEED -> fromFeed(feedMapper.selectById(targetId, currentTid()));
			case TARGET_USER -> loadUserTarget(targetId, true);
			// 未知类型显式失败，不再 default 兜到 user 分支：那样会把脏数据静默当成用户处理
			default -> throw new TWTException("举报对象类型不合法");
		};
	}

	/**
	 * 查被举报对象（处置侧）：走单行主键查询，与提交侧共用同一批 SQL
	 * <p>
	 * 不能走批量 selectByIds：那是全列查询（product 含 description/images TEXT、feed 含 2000 字 content），
	 * 而这里只读 ownerId/title/status 三个标量，还会把主键直查变成单元素的 IN (?)。
	 * </p>
	 * <p>
	 * 用户目标不比对租户：处置侧 report 行本身已按举报人的 sys_user 租户过滤过（见 ReportMapper.xml），
	 * 再比对一次只会把「本租户用户举报的跨租户账号」误判为不存在。
	 * </p>
	 */
	private Target loadTargetByIds(String targetType, Long targetId) {
		return switch (targetType) {
			case TARGET_PRODUCT -> fromProduct(productMapper.selectById(targetId, currentTid()));
			case TARGET_FEED -> fromFeed(feedMapper.selectById(targetId, currentTid()));
			// 比对租户，与封号 SQL 的 `AND tenant_id = ?` 保持一致：
			// 两个口径若相反，会出现「判定存在但 UPDATE 影响 0 行」的静默假成功
			case TARGET_USER -> loadUserTarget(targetId, true);
			default -> throw new TWTException("举报对象类型不合法");
		};
	}

	private Target fromProduct(Product p) {
		Target target = new Target();
		if (p != null) {
			target.exists = true;
			target.ownerId = p.getSellerId();
			target.title = p.getTitle();
			target.status = p.getStatus();
		}
		return target;
	}

	private Target fromFeed(Feed f) {
		Target target = new Target();
		if (f != null && !"deleted".equals(f.getStatus())) {
			target.exists = true;
			target.ownerId = f.getUserId();
			target.title = f.getContent();
			target.status = f.getStatus();
		}
		return target;
	}

	private Target loadUserTarget(Long userId, boolean checkTenant) {
		Target target = new Target();
		List<Map<String, Object>> rows = userMapper.selectByIds(List.of(userId));
		if (rows == null || rows.isEmpty()) {
			return target;
		}
		Map<String, Object> row = rows.get(0);
		if (!"0".equals(String.valueOf(row.get("del_flag")))) {
			return target;
		}
		Long tid = currentTid();
		// tenant_id 已在 selectByIds 的列清单里，不必再单独查一次（原先每次举报多一次 DB 往返）
		if (checkTenant && tid != null && !tid.equals(toLong(row.get("tenant_id")))) {
			// 跨租户：统一「不存在」文案，不泄露存在性
			return target;
		}
		target.exists = true;
		target.ownerId = userId;
		target.title = String.valueOf(row.get("nick_name"));
		target.ownerUserType = row.get("user_type") == null ? null : String.valueOf(row.get("user_type"));
		return target;
	}

	// ====== 工具 ======

	private static <T> T first(List<T> list) {
		return (list == null || list.isEmpty()) ? null : list.get(0);
	}

	private static String nickName(Map<Long, Map<String, Object>> userMap, Long userId) {
		if (userId == null) {
			return null;
		}
		Map<String, Object> u = userMap.get(userId);
		return u == null ? null : String.valueOf(u.get("nick_name"));
	}

	private static Long toLong(Object v) {
		if (v == null) {
			return null;
		}
		return v instanceof Number n ? n.longValue() : Long.valueOf(String.valueOf(v));
	}

	private static String truncate(String s, int max) {
		if (s == null) {
			return null;
		}
		return s.length() <= max ? s : s.substring(0, max);
	}

	/** 动态 images 是 JSON 数组，取第一张作封面；解析失败返回 null */
	private static String firstImage(String imagesJson) {
		if (imagesJson == null || imagesJson.isEmpty()) {
			return null;
		}
		try {
			// 静态常量：匿名 TypeReference 每次调用都新建一个子类对象（Class + 泛型捕获），
			// 列表页最多调用 200 次，没必要重复分配
			List<String> list = JacksonUtils.readValue(imagesJson, IMAGE_LIST_TYPE);
			return (list == null || list.isEmpty()) ? null : list.get(0);
		}
		catch (Exception e) {
			return null;
		}
	}

	/** 被举报对象的存在性、归属与状态（内部值对象） */
	private static final class Target {

		private boolean exists;

		private Long ownerId;

		private String title;

		private String status;

		/** 仅 user 目标有值：sys_user.user_type（00 运营/系统账号） */
		private String ownerUserType;

	}

}
