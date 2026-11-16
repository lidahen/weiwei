import { imMessages, imPeerInfo, imSend } from '@/services/marketplace/api';
import TWT from '@/setting';
import { IMG_FALLBACK, staticUrl } from './utils';
import { SendOutlined } from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import { history, useModel, useRequest, useSearchParams } from '@umijs/max';
import { Avatar, Button, Card, Empty, Input, Spin, message } from 'antd';
import { createStyles } from 'antd-style';
import React, { useEffect, useRef, useState } from 'react';
import { mq } from './styles/tokens';

// 聊天卡片高度（满屏页，本页故意不含底部 TabBar —— MobileTabBar 的
// HIDDEN_PREFIXES 含 /im/chat，故不扣 TabBar 高度，使用整屏高度）。
// 优先 100dvh（移动端地址栏收展时 100vh 会抖动），不支持回退 100vh。
// 移动端扣除拆解：ProLayout header(~56) + PageContainer 标题区(~46)
//   + 内容区上下 padding(12+20=32) ≈ 134，取整 150 留少量余量防溢出；
//   输入条/安全区在卡片内部，已由 inputBar 的 safe-area padding 处理，不在此扣。
const useStyles = createStyles(() => ({
    chatCard: {
        '& .ant-card-body': {
            padding: 0,
            display: 'flex',
            flexDirection: 'column',
            height: 'calc(100vh - 240px)',
            '@supports (height: 100dvh)': {
                height: 'calc(100dvh - 240px)',
            },
            [mq.mobile]: {
                height: 'calc(100vh - 150px)',
                '@supports (height: 100dvh)': {
                    height: 'calc(100dvh - 150px)',
                },
            },
        },
    },
    msgList: {
        flex: 1,
        overflowY: 'auto',
        padding: 16,
        background: '#FFF8F1',
        WebkitOverflowScrolling: 'touch',
    },
    msgRow: {
        display: 'flex',
        marginBottom: 12,
    },
    bubble: {
        maxWidth: '60%',
        padding: '8px 12px',
        borderRadius: 12,
        [mq.mobile]: {
            maxWidth: '78%',
        },
    },
    bubbleImg: {
        maxWidth: 200,
        borderRadius: 8,
        [mq.mobile]: {
            maxWidth: 'min(200px, 56vw)',
        },
    },
    inputBar: {
        padding: 12,
        borderTop: '1px solid #f0f0f0',
        display: 'flex',
        gap: 8,
        flexWrap: 'wrap',
        // iOS 全面屏底部安全区
        paddingBottom: 'calc(12px + env(safe-area-inset-bottom, 0px))',
    },
    sendBtn: {
        flexShrink: 0,
    },
}));

const Chat: React.FC = () => {
    const [params] = useSearchParams();
    const { styles, cx } = useStyles();
    const peerId = params.get('peerId');
    const { initialState } = useModel('@@initialState');
    const myId = Number((initialState as any)?.currentUser?.user?.userId);
    const myAvatarPath = (initialState as any)?.currentUser?.user?.avatar;
    const myAvatar = myAvatarPath ? `${TWT.static}${myAvatarPath}` : undefined;
    const productId = params.get('productId');
    const [text, setText] = useState('');
    const listRef = useRef<HTMLDivElement>(null);

    const { data: peerResp } = useRequest(() => imPeerInfo(peerId!), {
        refreshDeps: [peerId],
        ready: !!peerId,
    });
    const peer: any = (peerResp as any)?.data ?? (peerResp as any);
    const peerAvatar = staticUrl(peer?.avatar);

    const {
        data: msgResp,
        loading,
        refresh,
    } = useRequest(() => imMessages(peerId!, productId ? Number(productId) : undefined), {
        refreshDeps: [peerId, productId],
        ready: !!peerId,
        pollingInterval: 3000,
    });
    const arr: any = ((msgResp as any)?.data ?? (msgResp as any)) || [];
    const wrapper = (Array.isArray(arr) ? arr : [])[0] || {};
    const messages = wrapper.messages || [];

    useEffect(() => {
        // 平滑滚动到最新消息，避免轮询拉到新消息时视口硬跳
        listRef.current?.scrollTo({ top: listRef.current.scrollHeight, behavior: 'smooth' });
    }, [messages.length]);

    const [sending, setSending] = useState(false);
    const send = async () => {
        if (!text.trim() || !peerId || sending) return;
        // 敏感词前端简单提示
        if (/微信|wechat|加我/i.test(text)) {
            message.warning('请勿留下联系方式,在平台内交易更安全');
        }
        try {
            setSending(true);
            await imSend({
                peerId: Number(peerId),
                productId: productId ? Number(productId) : undefined,
                type: 'text',
                content: text,
            });
            setText('');
            refresh();
        } catch (e) {
            message.error('发送失败');
        } finally {
            setSending(false);
        }
    };

    if (!peerId) return <Empty description="缺少聊天对象" />;

    return (
        <PageContainer
            header={{
                title: peer
                    ? ((
                          <span>
                              <Avatar src={peerAvatar} size={32} /> {peer.nickName}
                          </span>
                      ) as any)
                    : '加载中',
                breadcrumb: {},
                onBack: () => history.push('/im/conversations'),
            }}
        >
            <Card className={styles.chatCard} bodyStyle={{ padding: 0 }}>
                <div ref={listRef} className={styles.msgList}>
                    {loading && messages.length === 0 ? (
                        <Spin />
                    ) : messages.length === 0 ? (
                        <Empty description="还没有消息,发个招呼吧" />
                    ) : (
                        messages.map((m: any) => {
                            const isMe =
                                !Number.isNaN(myId) && String(m.senderId) === String(myId);
                            return (
                                <div
                                    key={m.msgId}
                                    className={cx(styles.msgRow, 'twt-msg-in')}
                                    style={{
                                        justifyContent: isMe ? 'flex-end' : 'flex-start',
                                    }}
                                >
                                    {!isMe && (
                                        <Avatar
                                            src={peerAvatar}
                                            size={32}
                                            style={{ marginRight: 8 }}
                                        />
                                    )}
                                    <div
                                        className={styles.bubble}
                                        style={{
                                            background: isMe ? '#FF6B35' : '#fff',
                                            color: isMe ? '#fff' : '#333',
                                        }}
                                    >
                                        {m.type === 'image' ? (
                                            <img
                                                src={staticUrl(m.imageUrl)}
                                                onError={(e) => {
                                                    (e.target as HTMLImageElement).src =
                                                        IMG_FALLBACK;
                                                }}
                                                className={styles.bubbleImg}
                                            />
                                        ) : m.type === 'bargain' ? (
                                            <span>[议价] {m.content}</span>
                                        ) : (
                                            m.content
                                        )}
                                    </div>
                                    {isMe && (
                                        <Avatar
                                            src={myAvatar}
                                            size={32}
                                            style={{ marginLeft: 8 }}
                                        />
                                    )}
                                </div>
                            );
                        })
                    )}
                </div>
                <div className={styles.inputBar}>
                    <Input
                        placeholder="输入消息..."
                        value={text}
                        onChange={(e) => setText(e.target.value)}
                        onPressEnter={send}
                        disabled={sending}
                    />
                    <Button
                        type="primary"
                        icon={<SendOutlined />}
                        onClick={send}
                        loading={sending}
                        className={styles.sendBtn}
                    >
                        {sending ? '发送中' : '发送'}
                    </Button>
                </div>
            </Card>
        </PageContainer>
    );
};

export default Chat;
