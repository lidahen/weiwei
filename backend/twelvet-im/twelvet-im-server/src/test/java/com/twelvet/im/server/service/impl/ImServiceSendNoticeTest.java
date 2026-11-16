package com.twelvet.im.server.service.impl;

import com.twelvet.framework.core.tenant.TenantSupport;
import com.twelvet.im.api.domain.ImNotice;
import com.twelvet.product.api.service.IFunnelEventService;
import com.twelvet.im.server.mapper.ImConversationMapper;
import com.twelvet.im.server.mapper.ImMessageMapper;
import com.twelvet.im.server.mapper.ImNoticeMapper;
import com.twelvet.im.server.mapper.ImUnreadMapper;
import com.twelvet.im.server.mapper.ImUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 站内通知写入。
 * <p>
 * 关键约束：sendNotice 不得带 @Transactional —— 它在举报处置的事务内被调用， 若自身带事务，抛异常会把共享事务标
 * rollback-only，外层提交时抛 UnexpectedRollbackException， 反而让「通知失败不影响处理」变成「通知失败必回滚处理」（设计文档
 * §5.5）。
 * </p>
 *
 * @author twelvet
 */
class ImServiceSendNoticeTest {

	private ImNoticeMapper noticeMapper;

	private ImServiceImpl service;

	@BeforeEach
	void setUp() {
		noticeMapper = mock(ImNoticeMapper.class);
		service = new ImServiceImpl(mock(ImConversationMapper.class), mock(ImMessageMapper.class), noticeMapper,
				mock(ImUnreadMapper.class), mock(ImUserMapper.class), mock(TenantSupport.class),
				mock(IFunnelEventService.class));
	}

	@Test
	@DisplayName("写入一条 platform 通知")
	void sendNotice_writesRow() {
		when(noticeMapper.insertNotice(any())).thenReturn(1);

		service.sendNotice(7L, "platform", "举报已处理", "你对「机械键盘」的举报已处理");

		ArgumentCaptor<ImNotice> captor = ArgumentCaptor.forClass(ImNotice.class);
		verify(noticeMapper).insertNotice(captor.capture());
		ImNotice n = captor.getValue();
		assertEquals(7L, n.getUserId());
		assertEquals("platform", n.getType());
		assertEquals("举报已处理", n.getTitle());
		assertEquals("你对「机械键盘」的举报已处理", n.getContent());
		assertEquals(0, n.getIsRead());
	}

	@Test
	@DisplayName("userId 为 null 时不写库")
	void sendNotice_nullUser_skipped() {
		service.sendNotice(null, "platform", "t", "c");
		verify(noticeMapper, never()).insertNotice(any());
	}

	@Test
	@DisplayName("写入失败向外抛，由调用方决定是否吞掉")
	void sendNotice_dbError_propagates() {
		when(noticeMapper.insertNotice(any())).thenThrow(new RuntimeException("db down"));
		assertThrows(RuntimeException.class, () -> service.sendNotice(7L, "platform", "t", "c"));
	}

	@Test
	@DisplayName("sendNotice 方法不带 @Transactional")
	void sendNotice_notTransactional() throws NoSuchMethodException {
		var m = ImServiceImpl.class.getMethod("sendNotice", Long.class, String.class, String.class, String.class);
		assertNull(m.getAnnotation(Transactional.class), "sendNotice 必须无事务注解，否则通知异常会回滚举报处理");
	}

}
