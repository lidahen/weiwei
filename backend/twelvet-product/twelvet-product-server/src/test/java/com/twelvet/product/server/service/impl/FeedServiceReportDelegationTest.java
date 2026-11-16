package com.twelvet.product.server.service.impl;

import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.framework.core.tenant.TenantSupport;
import com.twelvet.product.server.mapper.FeedCommentMapper;
import com.twelvet.product.server.mapper.FeedLikeMapper;
import com.twelvet.product.server.mapper.FeedMapper;
import com.twelvet.product.server.mapper.UserRelationMapper;
import com.twelvet.product.server.service.IReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 动态举报改接 report 表。
 * <p>
 * 锁定两件事：旧路径 {@code POST /feed/report} 必须落到 {@link IReportService#submit}（不再只写日志），
 * 且校验异常要原样上抛（前端依赖 400 提示，不能被吞成 "reported"）。
 * </p>
 *
 * @author twelvet
 */
class FeedServiceReportDelegationTest {

	private FeedMapper feedMapper;

	private IReportService reportService;

	private FeedServiceImpl service;

	@BeforeEach
	void setUp() {
		feedMapper = mock(FeedMapper.class);
		reportService = mock(IReportService.class);
		service = new FeedServiceImpl(feedMapper, mock(FeedLikeMapper.class), mock(FeedCommentMapper.class),
				mock(UserRelationMapper.class), mock(TenantSupport.class), reportService);
	}

	@Test
	@DisplayName("动态举报委托 IReportService.submit，目标类型固定 feed")
	void report_delegatesToReportService() {
		String result = service.report(10L, 7L, "spam", "刷屏");

		assertEquals("reported", result);
		verify(reportService).submit(eq("feed"), eq(10L), eq(7L), eq("spam"), eq("刷屏"), isNull());
		// 旧的「只写日志」实现不会碰任何 feed 表
		verifyNoInteractions(feedMapper);
	}

	@Test
	@DisplayName("校验异常原样上抛，不吞成 reported")
	void report_propagatesValidationError() {
		doThrow(new TWTException("举报对象不存在或已删除")).when(reportService)
			.submit(anyString(), anyLong(), anyLong(), anyString(), any(), any());

		TWTException e = assertThrows(TWTException.class, () -> service.report(10L, 7L, "spam", null));
		assertEquals("举报对象不存在或已删除", e.getMessage());
		verify(feedMapper, never()).updateStatus(anyLong(), any(), any(), any());
	}

}
