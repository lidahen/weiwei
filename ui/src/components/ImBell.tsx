/**
 * 全局 IM 未读铃铛（ProLayout header 右侧，app.tsx actionsRender 挂载）
 *
 * 背景：runner 模块的状态流转通知通过 IM 发 system 消息（im_message 表），
 * 用户在任意页面都能通过铃铛感知新消息，点击进入消息中心（/im/conversations）。
 *
 * 未读口径（服务端闭环，2026-09-06 起）：
 * - 每 60s 轮询 GET /im/unread/count（含单次进入页面立即拉取）+ GET /im/conversation/list（下拉数据源）；
 * - 未读数由后端按 im_unread.last_read_msg_id（已读位点）计算：会话内 msg_id 大于位点的他人消息，
 *   返回 { total, convs: [{convId, unread}] }，跨刷新/换设备/换 token 均一致；
 * - 点击下拉中的会话 → PUT /im/unread/read?conversationId=&messageId= 标记该会话已读并跳聊天页；
 *   点「查看全部消息」→ PUT /im/unread/read（全部已读）并跳消息中心；
 * - 已读位点持久化在 im_unread 表（sendMessage 时接收方递增、发送者清零），不再依赖 localStorage。
 */
import { imConversations, imMarkRead, imUnreadCount } from '@/services/im';
import { staticUrl } from '@/pages/marketplace/utils';
import { BellOutlined } from '@ant-design/icons';
import { history, useModel } from '@umijs/max';
import { Avatar, Badge, Button, Empty, List, Popover } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';

const POLL_INTERVAL = 60 * 1000;
const DROPDOWN_SIZE = 5; // 下拉展示最近 5 条会话
const MAX_UNREAD_BADGE = 99; // 角标上限

/** 会话摘要文案（与消息中心 ConversationList 的展示口径一致） */
function previewText(item: any): string {
    if (item.lastType === 'image') return '[图片]';
    if (item.lastType === 'bargain') return '[议价]';
    if (item.lastType === 'system') return '[系统通知]';
    if (item.lastType === 'product_card') return '[商品卡片]';
    return item.lastContent || '';
}

const ImBell: React.FC = () => {
    const { initialState } = useModel('@@initialState');
    const myId = Number((initialState as any)?.currentUser?.user?.userId || 0);
    const [convs, setConvs] = useState<any[]>([]);
    const [total, setTotal] = useState(0); // 服务端未读总数
    const [unreadConvs, setUnreadConvs] = useState<Record<string, number>>({}); // convId -> 未读数
    const [open, setOpen] = useState(false);

    const refresh = useCallback(async () => {
        if (!myId) return;
        try {
            const [convResp, unreadResp] = await Promise.all([
                imConversations(),
                imUnreadCount(),
            ]);
            const list: any[] = ((convResp as any)?.data ?? convResp) || [];
            setConvs(list);

            const summary: any = (unreadResp as any)?.data ?? unreadResp ?? {};
            setTotal(Number(summary?.total || 0));
            const nextUnread: Record<string, number> = {};
            (summary?.convs || []).forEach((it: any) => {
                if (it?.convId) nextUnread[String(it.convId)] = Number(it.unread || 0);
            });
            setUnreadConvs(nextUnread);
        } catch (e) {
            // 铃铛是旁路功能：未登录/网络异常静默，不弹全局错误提示
        }
    }, [myId]);

    useEffect(() => {
        if (!myId) return undefined;
        refresh();
        const timer = setInterval(refresh, POLL_INTERVAL);
        return () => clearInterval(timer);
    }, [myId, refresh]);

    /** 点击某条会话：服务端标记该会话已读（读到当前最后一条消息）后进入聊天页 */
    const goChat = async (item: any) => {
        setOpen(false);
        history.push(
            `/im/chat?peerId=${item.peerId}${
                item.relatedProductId ? `&productId=${item.relatedProductId}` : ''
            }`,
        );
        try {
            const resp: any = await imMarkRead(item.convId, item.lastMsgId);
            const data: any = (resp as any)?.data ?? resp;
            if (data && data.total !== undefined) {
                setTotal(Number(data.total || 0));
            }
            const next = { ...unreadConvs };
            delete next[String(item.convId)];
            setUnreadConvs(next);
        } catch (e) {
            // 标记失败不影响跳转，下轮轮询会自动对齐
        }
    };

    /** 进入消息中心视为全部已读（服务端接口，按当前租户全部会话清零） */
    const goMessageCenter = async () => {
        setOpen(false);
        history.push('/im/conversations');
        try {
            const resp: any = await imMarkRead();
            const data: any = (resp as any)?.data ?? resp;
            setTotal(Number(data?.total || 0));
        } catch (e) {
            // 失败留给下轮轮询对齐
        }
        setUnreadConvs({});
    };

    if (!myId) return null;

    const unreadCount = Math.min(total, MAX_UNREAD_BADGE);

    const content = (
        <div style={{ width: 320 }}>
            <div
                style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    padding: '8px 12px',
                    fontWeight: 500,
                    borderBottom: '1px solid #f0f0f0',
                }}
            >
                <span>消息通知</span>
                <Button
                    type="link"
                    size="small"
                    style={{ padding: 0 }}
                    onClick={goMessageCenter}
                >
                    查看全部
                </Button>
            </div>
            {convs.length === 0 ? (
                <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description="暂无消息"
                    style={{ padding: 16 }}
                />
            ) : (
                <List
                    dataSource={convs.slice(0, DROPDOWN_SIZE)}
                    renderItem={(item: any) => {
                        const isUnread =
                            (unreadConvs[String(item.convId)] || 0) > 0;
                        return (
                            <List.Item
                                style={{
                                    cursor: 'pointer',
                                    padding: '8px 12px',
                                    borderRadius: 8,
                                    background: isUnread ? '#FAF8F2' : undefined,
                                }}
                                onClick={() => goChat(item)}
                            >
                                <List.Item.Meta
                                    avatar={
                                        <Badge dot={isUnread} offset={[-4, 4]}>
                                            <Avatar
                                                src={staticUrl(item.peerAvatar)}
                                                size={36}
                                            >
                                                {item.peerNickName}
                                            </Avatar>
                                        </Badge>
                                    }
                                    title={
                                        <div
                                            style={{
                                                display: 'flex',
                                                justifyContent: 'space-between',
                                                gap: 8,
                                            }}
                                        >
                                            <span
                                                style={{
                                                    fontWeight: isUnread ? 600 : 500,
                                                    overflow: 'hidden',
                                                    textOverflow: 'ellipsis',
                                                    whiteSpace: 'nowrap',
                                                }}
                                            >
                                                {item.peerNickName || '用户'}
                                            </span>
                                            <span
                                                style={{
                                                    color: '#999',
                                                    fontSize: 12,
                                                    flexShrink: 0,
                                                }}
                                            >
                                                {item.lastMsgTime}
                                            </span>
                                        </div>
                                    }
                                    description={
                                        <div
                                            style={{
                                                color: isUnread ? '#2D2D2A' : '#666',
                                                overflow: 'hidden',
                                                textOverflow: 'ellipsis',
                                                whiteSpace: 'nowrap',
                                            }}
                                        >
                                            {previewText(item) || '点击开始聊天'}
                                        </div>
                                    }
                                />
                            </List.Item>
                        );
                    }}
                />
            )}
        </div>
    );

    return (
        <Popover
            content={content}
            trigger="click"
            open={open}
            onOpenChange={(o) => {
                setOpen(o);
                if (o) refresh(); // 打开下拉时立即刷新一次
            }}
        >
            <div
                style={{
                    display: 'flex',
                    height: 26,
                    alignItems: 'center',
                    cursor: 'pointer',
                    padding: '0 6px',
                }}
            >
                <Badge count={unreadCount} size="small">
                    <BellOutlined style={{ fontSize: 18 }} />
                </Badge>
            </div>
        </Popover>
    );
};

export default ImBell;
