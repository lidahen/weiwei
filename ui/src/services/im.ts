/**
 * IM 即时通讯 service（全局未读铃铛等轻量场景用）
 *
 * 与 services/marketplace/api.ts 中的 IM 接口保持同源（/im/*），
 * 独立成文件是为了让全局布局（app.tsx 铃铛）不依赖 marketplace 模块代码。
 * 后端 Controller: twelvet-im-server ImController
 * （/im/conversation/list、/im/message/list、/im/unread/count、/im/unread/read）。
 */
import { request } from '@umijs/max';

/** 我的会话列表（按最后消息时间倒序，含对方昵称/头像/最后一条消息摘要） */
export async function imConversations() {
    return request('/im/conversation/list', { method: 'GET' });
}

/** 会话消息列表（返回 [{ convId, messages }]，messages 按时间正序，最多 200 条） */
export async function imMessageList(peerId: number, productId?: number) {
    return request('/im/message/list', {
        method: 'GET',
        params: { peerId, productId },
    });
}

/**
 * 未读汇总（服务端口径：会话内 msg_id > 已读位点 im_unread.last_read_msg_id 的他人消息）
 * 返回 { total, convs: [{ convId, unread }] }（convs 仅含有未读的会话）
 */
export async function imUnreadCount() {
    return request('/im/unread/count', { method: 'GET' });
}

/**
 * 标记会话已读（服务端持久化到 im_unread.last_read_msg_id，跨设备/换 token 生效）
 * @param conversationId 会话ID；缺省=全部会话已读
 * @param messageId 读到哪条（可选，缺省推进到会话最后一条）
 * 返回 { convId?, total }：total 为标记后的剩余未读总数
 */
export async function imMarkRead(conversationId?: number, messageId?: number) {
    const params: Record<string, number> = {};
    if (conversationId) params.conversationId = conversationId;
    if (messageId) params.messageId = messageId;
    return request('/im/unread/read', { method: 'PUT', params });
}
