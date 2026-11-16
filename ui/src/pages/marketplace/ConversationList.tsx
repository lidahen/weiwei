import { imConversations, imNotices } from '@/services/marketplace/api';
import { staticUrl } from './utils';
import { PageContainer } from '@ant-design/pro-components';
import { history, useRequest } from '@umijs/max';
import { Avatar, Badge, Empty, List, Spin, Tabs, Tag } from 'antd';
import React, { useState } from 'react';

const ConversationList: React.FC = () => {
    const [tab, setTab] = useState<'chat' | 'notice'>('chat');
    const { data: convResp, loading: cLoading } = useRequest(imConversations);
    const { data: noticeResp, loading: nLoading } = useRequest(imNotices);
    const convs: any[] = ((convResp as any)?.data ?? (convResp as any)) || [];
    const notices: any[] = ((noticeResp as any)?.data ?? (noticeResp as any)) || [];

    return (
        <PageContainer header={{ title: '消息中心', breadcrumb: {} }}>
            <Tabs
                activeKey={tab}
                onChange={(k) => setTab(k as any)}
                items={[
                    { key: 'chat', label: `聊天 ${convs.length > 0 ? `(${convs.length})` : ''}` },
                    {
                        key: 'notice',
                        label: `通知 ${notices.length > 0 ? `(${notices.length})` : ''}`,
                    },
                ]}
            />
            {tab === 'chat' &&
                (cLoading ? (
                    <Spin />
                ) : convs.length === 0 ? (
                    <Empty description="暂无消息,去逛逛商品和卖家聊一聊吧" />
                ) : (
                    <List
                        itemLayout="horizontal"
                        dataSource={convs}
                        renderItem={(item: any) => (
                            <List.Item
                                style={{ cursor: 'pointer', padding: '12px 12px', minHeight: 64, borderRadius: 12 }}
                                onClick={() =>
                                    history.push(
                                        `/im/chat?peerId=${item.peerId}${
                                            item.relatedProductId
                                                ? `&productId=${item.relatedProductId}`
                                                : ''
                                        }`,
                                    )
                                }
                            >
                                <List.Item.Meta
                                    avatar={
                                        <Badge count={item.unreadCount > 0 ? item.unreadCount : 0} size="small" offset={[-4, 4]}>
                                            <Avatar src={staticUrl(item.peerAvatar)} size={48} />
                                        </Badge>
                                    }
                                    title={
                                        <div
                                            style={{
                                                display: 'flex',
                                                justifyContent: 'space-between',
                                                alignItems: 'center',
                                                gap: 8,
                                            }}
                                        >
                                            <span
                                                style={{
                                                    fontWeight: 500,
                                                    flex: 1,
                                                    minWidth: 0,
                                                    overflow: 'hidden',
                                                    textOverflow: 'ellipsis',
                                                    whiteSpace: 'nowrap',
                                                }}
                                            >
                                                {item.peerNickName}
                                            </span>
                                            <span
                                                style={{
                                                    color: '#999',
                                                    fontSize: 12,
                                                    flexShrink: 0,
                                                    whiteSpace: 'nowrap',
                                                }}
                                            >
                                                {item.lastMsgTime}
                                            </span>
                                        </div>
                                    }
                                    description={
                                        <>
                                            <div
                                                style={{
                                                    color: '#666',
                                                    overflow: 'hidden',
                                                    textOverflow: 'ellipsis',
                                                    whiteSpace: 'nowrap',
                                                }}
                                            >
                                                {item.lastType === 'image'
                                                    ? '[图片]'
                                                    : item.lastType === 'bargain'
                                                    ? '[议价]'
                                                    : item.lastContent || '点击开始聊天'}
                                            </div>
                                            {item.productTitle && (
                                                <div style={{ marginTop: 4 }}>
                                                    <Tag
                                                        color="orange"
                                                        style={{ maxWidth: '100%', whiteSpace: 'normal', height: 'auto', lineHeight: '20px' }}
                                                    >
                                                        商品 ¥{item.productPrice} {item.productTitle}
                                                    </Tag>
                                                </div>
                                            )}
                                        </>
                                    }
                                />
                            </List.Item>
                        )}
                    />
                ))}
            {tab === 'notice' &&
                (nLoading ? (
                    <Spin />
                ) : notices.length === 0 ? (
                    <Empty description="暂无通知" />
                ) : (
                    <List
                        itemLayout="horizontal"
                        dataSource={notices}
                        renderItem={(n: any) => (
                            <List.Item style={{ padding: 12 }}>
                                <List.Item.Meta
                                    title={
                                        <>
                                            <Tag color="blue">
                                                {n.type === 'order'
                                                    ? '订单'
                                                    : n.type === 'platform'
                                                    ? '平台'
                                                    : '互动'}
                                            </Tag>{' '}
                                            {n.title}
                                        </>
                                    }
                                    description={n.content}
                                />
                                <span style={{ color: '#999', fontSize: 12, whiteSpace: 'nowrap', flexShrink: 0 }}>{n.createTime}</span>
                            </List.Item>
                        )}
                    />
                ))}
        </PageContainer>
    );
};

export default ConversationList;
