package com.twelvet.im.server.service.impl;

import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.framework.core.tenant.TenantSupport;
import com.twelvet.im.api.domain.ImConversation;
import com.twelvet.im.server.mapper.ImConversationMapper;
import com.twelvet.im.server.mapper.ImMessageMapper;
import com.twelvet.im.server.mapper.ImNoticeMapper;
import com.twelvet.im.server.mapper.ImUnreadMapper;
import com.twelvet.im.server.mapper.ImUserMapper;
import com.twelvet.product.api.domain.FunnelEvent;
import com.twelvet.product.api.service.IFunnelEventService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 会话开始漏斗：只有新建会话才记 im_start，重复进入同一会话不重复计数。
 */
class ImServiceFunnelTest {

	private static final Long USER_ID = 7L;

	private static final Long PEER_ID = 9L;

	private static final Long PRODUCT_ID = 100L;

	private static final Long TENANT_ID = 42L;

	private final ImConversationMapper conversationMapper = mock(ImConversationMapper.class);

	private final IFunnelEventService funnelEventService = mock(IFunnelEventService.class);

	private final ImServiceImpl service = new ImServiceImpl(conversationMapper, mock(ImMessageMapper.class),
			mock(ImNoticeMapper.class), mock(ImUnreadMapper.class), mock(ImUserMapper.class), mock(TenantSupport.class),
			funnelEventService);

	@BeforeEach
	void setTenant() {
		TenantContext.set(TENANT_ID);
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	@Test
	@DisplayName("新建会话写 im_start（发起人、商品、租户）")
	void newConversation_recordsImStart() {
		when(conversationMapper.selectConversationByUsers(USER_ID, PEER_ID, TENANT_ID)).thenReturn(null);
		doAnswer(invocation -> {
			invocation.getArgument(0, ImConversation.class).setConvId(55L);
			return 1;
		}).when(conversationMapper).insertConversation(any(ImConversation.class));

		service.ensureConversation(USER_ID, PEER_ID, PRODUCT_ID);

		ArgumentCaptor<FunnelEvent> captor = ArgumentCaptor.forClass(FunnelEvent.class);
		verify(funnelEventService).record(captor.capture());
		FunnelEvent event = captor.getValue();
		assertEquals(FunnelEvent.TYPE_IM_START, event.getEventType());
		assertEquals("im_start:55", event.getEventKey());
		assertEquals(55L, event.getBizId() == null ? null : Long.valueOf(event.getBizId()));
		assertEquals(PRODUCT_ID, event.getProductId());
		assertEquals(USER_ID, event.getUserId());
		assertEquals(TENANT_ID, event.getTenantId());
	}

	@Test
	@DisplayName("已有会话不重复写 im_start")
	void existingConversation_noEvent() {
		ImConversation existing = new ImConversation();
		existing.setConvId(55L);
		when(conversationMapper.selectConversationByUsers(USER_ID, PEER_ID, TENANT_ID)).thenReturn(existing);

		service.ensureConversation(USER_ID, PEER_ID, PRODUCT_ID);

		verifyNoInteractions(funnelEventService);
	}

}