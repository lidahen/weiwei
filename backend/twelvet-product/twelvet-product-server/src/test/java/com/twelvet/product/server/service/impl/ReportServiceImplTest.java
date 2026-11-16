package com.twelvet.product.server.service.impl;

import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.im.server.service.IImService;
import com.twelvet.product.api.domain.Feed;
import com.twelvet.product.api.domain.Product;
import com.twelvet.product.server.domain.Report;
import com.twelvet.product.server.mapper.FeedMapper;
import com.twelvet.product.server.mapper.ProductMapper;
import com.twelvet.product.server.mapper.ReportMapper;
import com.twelvet.product.server.mapper.UserMapper;
import com.twelvet.product.server.service.IReportService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 举报提交 / 列表 / 处置。锁定的不变量：
 * <ol>
 * <li>不能举报自己的内容（自举报是灌水入口）</li>
 * <li>目标不存在 / 已删除 / 跨租户一律同一文案（不泄露存在性）</li>
 * <li>同人同对象：pending 拒绝；done 复活（清空处理字段）</li>
 * <li>reason 落库为「标签：详情」合成文本</li>
 * <li>处置只对 onsale/auditing 商品下架，locked/sold 走纠纷流程（保护已付款订单）</li>
 * <li>封号必须踢下线，否则最长 24h 不生效</li>
 * <li>通知失败不回滚处置</li>
 * </ol>
 *
 * @author twelvet
 */
class ReportServiceImplTest {

	private ReportMapper reportMapper;

	private ProductMapper productMapper;

	private FeedMapper feedMapper;

	private UserMapper userMapper;

	private IImService imService;

	private IReportService service;

	@BeforeEach
	void setUp() {
		reportMapper = mock(ReportMapper.class);
		productMapper = mock(ProductMapper.class);
		feedMapper = mock(FeedMapper.class);
		userMapper = mock(UserMapper.class);
		imService = mock(IImService.class);
		service = new ReportServiceImpl(reportMapper, productMapper, feedMapper, userMapper, imService);
		// PageUtils.startPage() 经 ServletUtils.getRequest().get() 读 current/pageSize，
		// 无请求上下文时抛 NoSuchElementException —— adminList 用例必须补一个空参请求
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(mock(HttpServletRequest.class)));
	}

	@AfterEach
	void tearDown() {
		TenantContext.clear();
		RequestContextHolder.resetRequestAttributes();
	}

	private Product product(Long id, Long sellerId, String status) {
		Product p = new Product();
		p.setProductId(id);
		p.setSellerId(sellerId);
		p.setTitle("九成新机械键盘");
		p.setStatus(status);
		p.setPrice(new BigDecimal("120.00"));
		return p;
	}

	private Feed feed(Long id, Long userId, String status) {
		Feed f = new Feed();
		f.setFeedId(id);
		f.setUserId(userId);
		f.setContent("今天出个闲置");
		f.setStatus(status);
		return f;
	}

	private Report reportOf(Long id, String type, Long targetId, String status) {
		Report r = new Report();
		r.setReportId(id);
		r.setReporterId(7L);
		r.setTargetType(type);
		r.setTargetId(targetId);
		r.setStatus(status);
		r.setReason("欺诈 / 骗钱：卖家收钱不发货");
		return r;
	}

	private static Map<String, Object> user(Long id, String nick) {
		return user(id, nick, "02");
	}

	private static Map<String, Object> user(Long id, String nick, String userType) {
		return user(id, nick, userType, 1L);
	}

	/**
	 * 用户行（tenant_id 已并入 selectByIds 列清单，租户比对不再单独查库）
	 */
	private static Map<String, Object> user(Long id, String nick, String userType, Long tenantId) {
		return Map.of("user_id", id, "nick_name", nick, "avatar", "/a.png", "status", 0, "del_flag", "0", "user_type",
				userType, "tenant_id", tenantId);
	}

	// ==================== submit ====================

	@Test
	@DisplayName("正常提交商品举报：落库且 reason 为合成文本")
	void submit_product_ok() {
		TenantContext.set(1L);
		when(productMapper.selectById(42L, 1L)).thenReturn(product(42L, 99L, "onsale"));
		when(reportMapper.selectByReporterAndTarget(7L, "product", 42L)).thenReturn(null);

		service.submit("product", 42L, 7L, "fraud", "卖家收钱不发货", List.of("/a.jpg"));

		ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
		verify(reportMapper).insert(captor.capture());
		Report r = captor.getValue();
		assertEquals(7L, r.getReporterId());
		assertEquals("product", r.getTargetType());
		assertEquals(42L, r.getTargetId());
		assertEquals("欺诈 / 骗钱：卖家收钱不发货", r.getReason());
		assertEquals("[\"/a.jpg\"]", r.getEvidenceImages());
	}

	@Test
	@DisplayName("举报自己的商品被拒")
	void submit_self_rejected() {
		TenantContext.set(1L);
		when(productMapper.selectById(42L, 1L)).thenReturn(product(42L, 7L, "onsale"));

		assertThrows(TWTException.class, () -> service.submit("product", 42L, 7L, "fraud", null, null));
		verify(reportMapper, never()).insert(any());
	}

	@Test
	@DisplayName("商品不存在 → 统一文案")
	void submit_productMissing_rejected() {
		TenantContext.set(1L);
		when(productMapper.selectById(42L, 1L)).thenReturn(null);

		TWTException e = assertThrows(TWTException.class,
				() -> service.submit("product", 42L, 7L, "fraud", null, null));
		assertEquals("举报对象不存在或已删除", e.getMessage());
		verify(reportMapper, never()).insert(any());
	}

	@Test
	@DisplayName("软删的动态视为不存在（Review Focus #5 的提交侧）")
	void submit_feedSoftDeleted_rejected() {
		TenantContext.set(1L);
		when(feedMapper.selectById(10L, 1L)).thenReturn(feed(10L, 99L, "deleted"));

		assertThrows(TWTException.class, () -> service.submit("feed", 10L, 7L, "spam", null, null));
		verify(reportMapper, never()).insert(any());
	}

	@Test
	@DisplayName("跨租户举报用户被拒（不泄露存在性）")
	void submit_crossTenantUser_rejected() {
		TenantContext.set(1L);
		// 用户属租户 2，举报人在租户 1 → 跨租户，统一「不存在」文案
		when(userMapper.selectByIds(List.of(55L))).thenReturn(List.of(user(55L, "alice", "02", 2L)));

		assertThrows(TWTException.class, () -> service.submit("user", 55L, 7L, "abuse", null, null));
		verify(reportMapper, never()).insert(any());
	}

	@Test
	@DisplayName("已有 pending 举报 → 拒绝")
	void submit_duplicatePending_rejected() {
		TenantContext.set(1L);
		when(productMapper.selectById(42L, 1L)).thenReturn(product(42L, 99L, "onsale"));
		Report exist = new Report();
		exist.setStatus("pending");
		when(reportMapper.selectByReporterAndTarget(7L, "product", 42L)).thenReturn(exist);

		TWTException e = assertThrows(TWTException.class,
				() -> service.submit("product", 42L, 7L, "fraud", null, null));
		assertEquals("你已举报过该内容，正在处理中", e.getMessage());
		verify(reportMapper, never()).insert(any());
	}

	@Test
	@DisplayName("已有 done 举报 → 复活而非新增")
	void submit_duplicateDone_revived() {
		TenantContext.set(1L);
		when(productMapper.selectById(42L, 1L)).thenReturn(product(42L, 99L, "onsale"));
		Report exist = new Report();
		exist.setReportId(5L);
		exist.setStatus("done");
		when(reportMapper.selectByReporterAndTarget(7L, "product", 42L)).thenReturn(exist);
		when(reportMapper.revive(anyLong(), anyString(), any())).thenReturn(1);

		service.submit("product", 42L, 7L, "spam", "又来灌水", null);

		verify(reportMapper).revive(eq(5L), eq("垃圾广告 / 引流：又来灌水"), any());
		verify(reportMapper, never()).insert(any());
	}

	@Test
	@DisplayName("复活时运营刚处理完（revive 影响 0 行）→ 按「正在处理中」拒绝")
	void submit_duplicateDone_reviveRace_rejected() {
		TenantContext.set(1L);
		when(productMapper.selectById(42L, 1L)).thenReturn(product(42L, 99L, "onsale"));
		Report exist = new Report();
		exist.setReportId(5L);
		exist.setStatus("done");
		when(reportMapper.selectByReporterAndTarget(7L, "product", 42L)).thenReturn(exist);
		// revive 的 WHERE status='done' 不命中：并发下运营刚把它处理掉，或已被别人复活
		when(reportMapper.revive(anyLong(), anyString(), any())).thenReturn(0);

		TWTException e = assertThrows(TWTException.class,
				() -> service.submit("product", 42L, 7L, "spam", "又来灌水", null));

		assertEquals("你已举报过该内容，正在处理中", e.getMessage());
	}

	@Test
	@DisplayName("并发双击撞唯一键 → 翻译成可读提示，不是 500 泄露 SQL")
	void submit_duplicateKey_translated() {
		TenantContext.set(1L);
		when(productMapper.selectById(42L, 1L)).thenReturn(product(42L, 99L, "onsale"));
		// SELECT 阶段两个请求都读到 null（并发窗口），另一个请求随后 INSERT 成功
		when(reportMapper.selectByReporterAndTarget(7L, "product", 42L)).thenReturn(null);
		when(reportMapper.insert(any())).thenThrow(
				new org.springframework.dao.DuplicateKeyException("Duplicate entry for key 'uk_reporter_target'"));

		TWTException e = assertThrows(TWTException.class,
				() -> service.submit("product", 42L, 7L, "fraud", null, null));

		assertEquals("你已举报过该内容，正在处理中", e.getMessage());
	}

	@Test
	@DisplayName("非法 targetType / reasonType 被拒")
	void submit_invalidArgs_rejected() {
		assertThrows(TWTException.class, () -> service.submit("im_msg", 1L, 7L, "spam", null, null));
		assertThrows(TWTException.class, () -> service.submit("product", 1L, 7L, "bogus", null, null));
	}

	@Test
	@DisplayName("targetId 为 null → 业务校验异常，不是 NPE")
	void submit_nullTargetId_rejected() {
		// user 分支走 userMapper.selectByIds(List.of(targetId))，List.of(null) 抛 NPE，
		// 会落进全局 Exception 分支返回 500，把参数错误伪装成服务端故障
		TWTException e = assertThrows(TWTException.class, () -> service.submit("user", null, 7L, "abuse", null, null));
		assertEquals("举报对象ID不合法", e.getMessage());
		verify(reportMapper, never()).insert(any());
		verifyNoInteractions(userMapper);
	}

	@Test
	@DisplayName("租户开关关闭时跳过租户比对")
	void submit_tenantOff_skipsTenantCheck() {
		TenantContext.clear();
		when(userMapper.selectByIds(List.of(55L))).thenReturn(List.of(user(55L, "alice", "02", 2L)));
		when(reportMapper.selectByReporterAndTarget(7L, "user", 55L)).thenReturn(null);

		assertDoesNotThrow(() -> service.submit("user", 55L, 7L, "abuse", null, null));
		verify(reportMapper).insert(any());
	}

	// ==================== adminList ====================

	@Test
	@DisplayName("列表：三类对象批量取摘要，不逐条查（N+1）")
	void adminList_batchesByTargetType() {
		TenantContext.set(1L);
		Report r1 = reportOf(1L, "product", 42L, "pending");
		Report r2 = reportOf(2L, "product", 43L, "pending");
		Report r3 = reportOf(3L, "feed", 10L, "pending");
		Report r4 = reportOf(4L, "user", 55L, "pending");
		when(reportMapper.selectList("pending", 1L)).thenReturn(List.of(r1, r2, r3, r4));
		when(productMapper.selectByIds(anyList(), eq(1L)))
			.thenReturn(List.of(product(42L, 99L, "onsale"), product(43L, 99L, "offshelf")));
		when(feedMapper.selectByIds(anyList(), eq(1L))).thenReturn(List.of(feed(10L, 88L, "normal")));
		when(userMapper.selectByIds(anyList())).thenReturn(List.of(user(7L, "bob"), user(55L, "alice")));

		TableDataInfo<Map<String, Object>> result = service.adminList("pending");
		List<Map<String, Object>> rows = result.getRecords();

		assertEquals(4, rows.size());
		// product 摘要
		assertEquals("九成新机械键盘", rows.get(0).get("targetTitle"));
		assertTrue((Boolean) rows.get(0).get("targetExists"));
		assertEquals(99L, rows.get(0).get("ownerId"));
		// 已下架商品仍显示（selectByIds 不过滤 status）
		assertTrue((Boolean) rows.get(1).get("targetExists"));
		// feed 摘要
		assertEquals("今天出个闲置", rows.get(2).get("targetTitle"));
		assertEquals(88L, rows.get(2).get("ownerId"));
		// user 摘要
		assertEquals("alice", rows.get(3).get("targetTitle"));
		assertEquals(55L, rows.get(3).get("ownerId"));
		// 举报人昵称
		assertEquals("bob", rows.get(0).get("reporterNickName"));
		// 三类目标各查一次；用户表只查一次（举报人/处理人/用户目标合并）
		verify(productMapper, times(1)).selectByIds(any(), any());
		verify(feedMapper, times(1)).selectByIds(any(), any());
		ArgumentCaptor<List<Long>> userCaptor = ArgumentCaptor.forClass(List.class);
		verify(userMapper, times(1)).selectByIds(userCaptor.capture());
		assertTrue(userCaptor.getValue().containsAll(List.of(7L, 55L)));
	}

	@Test
	@DisplayName("列表：软删动态 targetExists=false 且标题保留（Review Focus #5）")
	void adminList_softDeletedFeed_markedDeleted() {
		TenantContext.set(1L);
		when(reportMapper.selectList("pending", 1L)).thenReturn(List.of(reportOf(3L, "feed", 10L, "pending")));
		when(feedMapper.selectByIds(anyList(), eq(1L))).thenReturn(List.of(feed(10L, 88L, "deleted")));

		List<Map<String, Object>> rows = service.adminList("pending").getRecords();

		assertFalse((Boolean) rows.get(0).get("targetExists"));
		assertEquals("今天出个闲置", rows.get(0).get("targetTitle"));
	}

	@Test
	@DisplayName("列表：目标已被硬删，摘要为空但行仍返回")
	void adminList_missingProduct_keepsRow() {
		TenantContext.set(1L);
		when(reportMapper.selectList("pending", 1L)).thenReturn(List.of(reportOf(1L, "product", 42L, "pending")));
		when(productMapper.selectByIds(anyList(), eq(1L))).thenReturn(List.of());

		List<Map<String, Object>> rows = service.adminList("pending").getRecords();

		assertEquals(1, rows.size());
		assertFalse((Boolean) rows.get(0).get("targetExists"));
		assertNull(rows.get(0).get("ownerId"));
	}

	@Test
	@DisplayName("列表：分页 total 取自原始 Page，不因重映射丢失")
	void adminList_keepsTotal() {
		TenantContext.set(1L);
		when(reportMapper.selectList("pending", 1L)).thenReturn(List.of(reportOf(1L, "product", 42L, "pending")));
		when(productMapper.selectByIds(anyList(), eq(1L))).thenReturn(List.of(product(42L, 99L, "onsale")));

		TableDataInfo<Map<String, Object>> result = service.adminList("pending");

		assertEquals(1, result.getRecords().size());
		assertEquals(1L, result.getTotal());
	}

	@Test
	@DisplayName("列表：status=all 透传 null")
	void adminList_allPassesNull() {
		TenantContext.set(1L);
		when(reportMapper.selectList(null, 1L)).thenReturn(List.of());

		List<Map<String, Object>> rows = service.adminList("all").getRecords();

		assertEquals(0, rows.size());
		verify(reportMapper).selectList(isNull(), eq(1L));
	}

	// ==================== adminHandle ====================

	@Test
	@DisplayName("处理已处理的举报 → 抛异常且不执行任何处置（Review Focus #2）")
	void handle_alreadyHandled_rejected() {
		TenantContext.set(1L);
		when(reportMapper.selectById(1L, 1L)).thenReturn(reportOf(1L, "product", 42L, "done"));
		when(reportMapper.markHandled(anyLong(), any(), any(), anyLong())).thenReturn(0);

		TWTException e = assertThrows(TWTException.class, () -> service.adminHandle(1L, "remove_product", "备注", 100L));
		assertEquals("该举报已被处理", e.getMessage());
		verify(productMapper, never()).updateStatusIfIn(anyLong(), any(), anyList(), any());
	}

	@Test
	@DisplayName("动作与对象类型不匹配 → 抛异常")
	void handle_actionTypeMismatch_rejected() {
		TenantContext.set(1L);
		when(reportMapper.selectById(1L, 1L)).thenReturn(reportOf(1L, "feed", 10L, "pending"));

		assertThrows(TWTException.class, () -> service.adminHandle(1L, "remove_product", null, 100L));
	}

	@Test
	@DisplayName("下架 locked 商品被拒（Review Focus #1：保护已付款订单）")
	void handle_removeProductLocked_rejected() {
		TenantContext.set(1L);
		when(reportMapper.selectById(1L, 1L)).thenReturn(reportOf(1L, "product", 42L, "pending"));
		when(productMapper.selectById(42L, 1L)).thenReturn(product(42L, 99L, "locked"));

		TWTException e = assertThrows(TWTException.class, () -> service.adminHandle(1L, "remove_product", null, 100L));
		assertEquals("该商品存在进行中订单或已售，请走纠纷处理流程", e.getMessage());
		verify(productMapper, never()).updateStatusIfIn(anyLong(), any(), anyList(), any());
	}

	@Test
	@DisplayName("下架 sold 商品同样被拒")
	void handle_removeProductSold_rejected() {
		TenantContext.set(1L);
		when(reportMapper.selectById(1L, 1L)).thenReturn(reportOf(1L, "product", 42L, "pending"));
		when(productMapper.selectById(42L, 1L)).thenReturn(product(42L, 99L, "sold"));

		assertThrows(TWTException.class, () -> service.adminHandle(1L, "remove_product", null, 100L));
		verify(productMapper, never()).updateStatusIfIn(anyLong(), any(), anyList(), any());
	}

	@Test
	@DisplayName("下架在售商品：改 offshelf + 双方通知")
	void handle_removeProductOk() {
		TenantContext.set(1L);
		when(reportMapper.selectById(1L, 1L)).thenReturn(reportOf(1L, "product", 42L, "pending"));
		when(productMapper.selectById(42L, 1L)).thenReturn(product(42L, 99L, "onsale"));
		when(reportMapper.markHandled(1L, "remove_product", "图片不符", 100L)).thenReturn(1);
		when(productMapper.updateStatusIfIn(eq(42L), eq("offshelf"), anyList(), eq(1L))).thenReturn(1);

		service.adminHandle(1L, "remove_product", "图片不符", 100L);

		// 状态守卫下沉到 SQL：可下架状态作为 WHERE 条件传给 Mapper
		ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
		verify(productMapper).updateStatusIfIn(eq(42L), eq("offshelf"), captor.capture(), eq(1L));
		assertEquals(Set.copyOf(List.of("onsale", "auditing")), Set.copyOf(captor.getValue()));
		verify(imService).sendNotice(eq(99L), eq("platform"), anyString(), contains("下架"));
		verify(imService).sendNotice(eq(7L), eq("platform"), anyString(), anyString());
	}

	@Test
	@DisplayName("读到时是 onsale，但 UPDATE 前已被下单锁定 → 按不可下架拒绝，不能报成功")
	void handle_removeProductToctou_rejected() {
		TenantContext.set(1L);
		when(reportMapper.selectById(1L, 1L)).thenReturn(reportOf(1L, "product", 42L, "pending"));
		when(productMapper.selectById(42L, 1L)).thenReturn(product(42L, 99L, "onsale"));
		when(reportMapper.markHandled(1L, "remove_product", "备注", 100L)).thenReturn(1);
		// 0 行 = WHERE status IN (...) 不命中，说明状态在读写之间变了
		when(productMapper.updateStatusIfIn(eq(42L), eq("offshelf"), anyList(), eq(1L))).thenReturn(0);

		TWTException e = assertThrows(TWTException.class, () -> service.adminHandle(1L, "remove_product", "备注", 100L));

		assertEquals("该商品存在进行中订单或已售，请走纠纷处理流程", e.getMessage());
		verify(imService, never()).sendNotice(any(), any(), any(), any());
	}

	@Test
	@DisplayName("删除动态：status=deleted + audit_status=rejected")
	void handle_removeFeedOk() {
		TenantContext.set(1L);
		when(reportMapper.selectById(1L, 1L)).thenReturn(reportOf(1L, "feed", 10L, "pending"));
		when(feedMapper.selectById(10L, 1L)).thenReturn(feed(10L, 88L, "normal"));
		when(reportMapper.markHandled(1L, "remove_feed", "违规", 100L)).thenReturn(1);
		when(feedMapper.updateStatusIfNot(eq(10L), eq("deleted"), eq("rejected"), eq("deleted"), eq(1L))).thenReturn(1);

		service.adminHandle(1L, "remove_feed", "违规", 100L);

		verify(imService).sendNotice(eq(88L), eq("platform"), anyString(), anyString());
	}

	@Test
	@DisplayName("封号：改 status=1 且踢下线（Review Focus #3）")
	void handle_banUserKicksOut() {
		TenantContext.set(1L);
		when(reportMapper.selectById(1L, 1L)).thenReturn(reportOf(1L, "user", 55L, "pending"));
		when(userMapper.selectByIds(List.of(55L))).thenReturn(List.of(user(55L, "alice")));
		when(reportMapper.markHandled(1L, "ban_user", "骚扰", 100L)).thenReturn(1);
		when(userMapper.updateStatus(eq(55L), eq(1), eq(1L))).thenReturn(1);

		try (var stp = mockStatic(cn.dev33.satoken.stp.StpUtil.class)) {
			service.adminHandle(1L, "ban_user", "骚扰", 100L);
			stp.verify(() -> cn.dev33.satoken.stp.StpUtil.kickout(55L));
		}

		verify(imService).sendNotice(eq(55L), eq("platform"), anyString(), contains("停用"));
	}

	@Test
	@DisplayName("封号 UPDATE 影响 0 行（已软删/租户不匹配）→ 必须报错，不能发假通知")
	void handle_banUserZeroRows_rejected() {
		TenantContext.set(1L);
		when(reportMapper.selectById(1L, 1L)).thenReturn(reportOf(1L, "user", 55L, "pending"));
		when(userMapper.selectByIds(List.of(55L))).thenReturn(List.of(user(55L, "alice")));
		when(reportMapper.markHandled(1L, "ban_user", "骚扰", 100L)).thenReturn(1);
		// 0 行 = 账号已软删或租户不匹配，封号实际没有发生
		when(userMapper.updateStatus(eq(55L), eq(1), eq(1L))).thenReturn(0);

		TWTException e = assertThrows(TWTException.class, () -> service.adminHandle(1L, "ban_user", "骚扰", 100L));

		assertEquals("处置未生效：该账号状态已变更，请刷新后重试", e.getMessage());
		verify(imService, never()).sendNotice(any(), any(), any(), any());
	}

	@Test
	@DisplayName("封号不得作用于运营账号（设计 §5.4 前置条件）")
	void handle_banOpsAccount_rejected() {
		TenantContext.set(1L);
		when(reportMapper.selectById(1L, 1L)).thenReturn(reportOf(1L, "user", 55L, "pending"));
		// user_type=00 系统/运营账号，02 普通 C 端用户
		when(userMapper.selectByIds(List.of(55L))).thenReturn(List.of(user(55L, "ops-admin", "00")));

		TWTException e = assertThrows(TWTException.class, () -> service.adminHandle(1L, "ban_user", "骚扰", 100L));

		assertEquals("运营账号不可通过举报处置停用", e.getMessage());
		verify(userMapper, never()).updateStatus(anyLong(), any(), any());
	}

	@Test
	@DisplayName("驳回：不改任何业务数据，只通知举报人")
	void handle_dismissOk() {
		TenantContext.set(1L);
		when(reportMapper.selectById(1L, 1L)).thenReturn(reportOf(1L, "product", 42L, "pending"));
		when(reportMapper.markHandled(1L, "dismiss", "经核实未违规", 100L)).thenReturn(1);

		service.adminHandle(1L, "dismiss", "经核实未违规", 100L);

		verify(productMapper, never()).updateStatusIfIn(anyLong(), any(), anyList(), any());
		verify(imService).sendNotice(eq(7L), eq("platform"), anyString(), contains("未违规"));
	}

	@Test
	@DisplayName("警告：不改业务数据，通知双方")
	void handle_warnOk() {
		TenantContext.set(1L);
		when(reportMapper.selectById(1L, 1L)).thenReturn(reportOf(1L, "product", 42L, "pending"));
		when(productMapper.selectById(42L, 1L)).thenReturn(product(42L, 99L, "onsale"));
		when(reportMapper.markHandled(1L, "warn", "注意言行", 100L)).thenReturn(1);

		service.adminHandle(1L, "warn", "注意言行", 100L);

		verify(imService).sendNotice(eq(99L), eq("platform"), anyString(), anyString());
		verify(imService).sendNotice(eq(7L), eq("platform"), anyString(), anyString());
	}

	@Test
	@DisplayName("通知抛异常不影响处理结果（Review Focus #4）")
	void handle_noticeFailureDoesNotRollback() {
		TenantContext.set(1L);
		when(reportMapper.selectById(1L, 1L)).thenReturn(reportOf(1L, "product", 42L, "pending"));
		when(productMapper.selectById(42L, 1L)).thenReturn(product(42L, 99L, "onsale"));
		when(reportMapper.markHandled(1L, "remove_product", "备注", 100L)).thenReturn(1);
		when(productMapper.updateStatusIfIn(eq(42L), eq("offshelf"), anyList(), eq(1L))).thenReturn(1);
		doThrow(new RuntimeException("db down")).when(imService).sendNotice(any(), any(), any(), any());

		assertDoesNotThrow(() -> service.adminHandle(1L, "remove_product", "备注", 100L));
		verify(reportMapper).markHandled(1L, "remove_product", "备注", 100L);
	}

	@Test
	@DisplayName("处置目标已被删除：照常置 done，备注追加说明")
	void handle_targetGone_stillHandled() {
		TenantContext.set(1L);
		when(reportMapper.selectById(1L, 1L)).thenReturn(reportOf(1L, "product", 42L, "pending"));
		when(productMapper.selectById(42L, 1L)).thenReturn(null);
		when(reportMapper.markHandled(1L, "remove_product", "备注", 100L)).thenReturn(1);

		assertDoesNotThrow(() -> service.adminHandle(1L, "remove_product", "备注", 100L));
		verify(productMapper, never()).updateStatusIfIn(anyLong(), any(), anyList(), any());
		// 追加说明走窄更新，不动 status/action
		verify(reportMapper).updateHandleNote(eq(1L), contains("对象已不存在"));
	}

}
