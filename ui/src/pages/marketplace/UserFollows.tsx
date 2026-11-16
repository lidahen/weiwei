import EmptyState from '@/components/marketplace/EmptyState';
import { userFollow, userFollowsList, userUnfollow } from '@/services/marketplace/api';
import { formatTime, staticUrl } from './utils';
import { PageContainer } from '@ant-design/pro-components';
import { history, useRequest } from '@umijs/max';
import { Avatar, Button, List, Spin, Tabs, message } from 'antd';
import React, { useState } from 'react';
import { useIsMobile } from '@/hooks/useIsMobile';

const UserFollows: React.FC = () => {
    const isMobile = useIsMobile();
    const [tab, setTab] = useState<'follows' | 'fans'>('follows');
    const { data, loading, refresh } = useRequest(() => userFollowsList(tab), {
        refreshDeps: [tab],
    });
    const resp: any = data;
    const list: any[] = resp?.data ?? resp ?? [];

    const handleToggle = async (item: any) => {
        try {
            // 后端 Mapper 返回 snake_case Map（user_id/nick_name/avatar/create_time）
            if (item.followed) {
                await userUnfollow(item.user_id);
                message.success('已取消关注');
            } else {
                await userFollow(item.user_id);
                message.success('关注成功');
            }
            refresh();
        } catch (e) {
            message.error('操作失败');
        }
    };

    return (
        <PageContainer header={{ title: '我的人脉', breadcrumb: {} }}>
            <Tabs
                activeKey={tab}
                onChange={(k) => setTab(k as any)}
                items={[
                    { key: 'follows', label: '我关注的' },
                    { key: 'fans', label: '我的粉丝' },
                ]}
            />
            {loading ? (
                <div style={{ textAlign: 'center', padding: isMobile ? 40 : 60 }}>
                    <Spin />
                </div>
            ) : list.length === 0 ? (
                <EmptyState
                    text={
                        tab === 'follows'
                            ? '还没有关注任何人,去逛逛吧'
                            : '还没有粉丝,多发好物吸引大家'
                    }
                    ctaText={tab === 'follows' ? '去发现' : '发布动态'}
                    onCtaClick={() => history.push(tab === 'follows' ? '/home' : '/feed/publish')}
                />
            ) : (
                <List
                    dataSource={list}
                    renderItem={(item: any) => (
                        <List.Item
                            className="twt-fade-up"
                            actions={[
                                <Button
                                    key="toggle"
                                    type={item.followed ? 'default' : 'primary'}
                                    className="twt-press"
                                    // 触摸目标：移动端放大按钮高度，接近 44px 标准
                                    size={isMobile ? 'large' : 'middle'}
                                    style={isMobile ? { height: 44, minWidth: 80 } : undefined}
                                    onClick={() => handleToggle(item)}
                                >
                                    {item.followed ? '已关注' : '关注'}
                                </Button>,
                            ]}
                        >
                            <List.Item.Meta
                                avatar={
                                    <Avatar
                                        src={staticUrl(item.avatar)}
                                        size={isMobile ? 40 : 48}
                                        style={{ cursor: 'pointer' }}
                                        onClick={() => history.push(`/user/profile/${item.user_id}`)}
                                    />
                                }
                                title={
                                    <a
                                        onClick={() => history.push(`/user/profile/${item.user_id}`)}
                                        // 移动端昵称超长加 ellipsis 防溢出
                                        style={
                                            isMobile
                                                ? {
                                                      display: 'inline-block',
                                                      maxWidth: 160,
                                                      overflow: 'hidden',
                                                      textOverflow: 'ellipsis',
                                                      whiteSpace: 'nowrap',
                                                      verticalAlign: 'bottom',
                                                  }
                                                : undefined
                                        }
                                    >
                                        {item.nick_name || '用户'}
                                    </a>
                                }
                                description={`关注时间: ${formatTime(item.create_time)}`}
                            />
                        </List.Item>
                    )}
                />
            )}
        </PageContainer>
    );
};

export default UserFollows;
