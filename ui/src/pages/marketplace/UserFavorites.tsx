import EmptyState from '@/components/marketplace/EmptyState';
import ProductCard from '@/components/marketplace/ProductCard';
import { favoritePage, favoriteRemove } from '@/services/marketplace/api';
import { PageContainer } from '@ant-design/pro-components';
import { history, useRequest } from '@umijs/max';
import { Col, message, Popconfirm, Row, Spin, Button } from 'antd';
import React from 'react';
import { useIsMobile } from '@/hooks/useIsMobile';

const UserFavorites: React.FC = () => {
    const isMobile = useIsMobile();
    const { data, loading, refresh } = useRequest(() => favoritePage({ current: 1, pageSize: 60 }));
    const resp: any = data;
    const list: any[] = resp?.records || resp?.data?.records || [];

    const handleRemove = async (productId: number) => {
        try {
            await favoriteRemove(productId);
            message.success('已取消收藏');
            refresh();
        } catch (e) {
            message.error('操作失败');
        }
    };

    return (
        <PageContainer header={{ title: '我的收藏', breadcrumb: {} }}>
            {loading ? (
                <div style={{ textAlign: 'center', padding: isMobile ? 40 : 60 }}>
                    <Spin />
                </div>
            ) : list.length === 0 ? (
                <EmptyState
                    text="还没有收藏任何商品"
                    ctaText="去逛逛"
                    onCtaClick={() => history.push('/home')}
                />
            ) : (
                // 移动端单列铺满：两列卡片在 390px 会挤爆（参考 UserAddress 改造）
                <Row gutter={isMobile ? [12, 12] : [16, 16]}>
                    {list.map((p) => (
                        <Col key={p.productId} xs={24} sm={12} md={8} lg={6} xl={4}>
                            <div style={{ position: 'relative' }}>
                                <ProductCard
                                    image={p.cover}
                                    title={p.title}
                                    price={p.price}
                                    originalPrice={p.originalPrice}
                                    condition={p.condition}
                                    sellerName={p.sellerNickName}
                                    city={p.city}
                                    onClick={() => history.push(`/product/detail/${p.productId}`)}
                                />
                                <Popconfirm
                                    title="取消收藏?"
                                    onConfirm={() => handleRemove(p.productId)}
                                >
                                    {/* 触摸目标 ≥44px：原来红色 Tag 点击区过小，移动端放大为按钮 */}
                                    <Button
                                        danger
                                        size="small"
                                        style={{
                                            position: 'absolute',
                                            top: 8,
                                            right: 8,
                                            zIndex: 1,
                                            height: isMobile ? 40 : 24,
                                            padding: isMobile ? '0 12px' : '0 8px',
                                        }}
                                    >
                                        取消
                                    </Button>
                                </Popconfirm>
                            </div>
                        </Col>
                    ))}
                </Row>
            )}
        </PageContainer>
    );
};

export default UserFavorites;
