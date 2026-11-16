import {
    productDelete,
    productMyPage,
    productOffshelf,
} from '@/services/marketplace/api';
import TWT from '@/setting';
import { PageContainer } from '@ant-design/pro-components';
import { history, useRequest } from '@umijs/max';
import {
    Button,
    Card,
    Col,
    Empty,
    Image,
    message,
    Modal,
    Row,
    Space,
    Spin,
    Tag,
    Tabs,
} from 'antd';
import React, { useState } from 'react';
import { useParams } from '@umijs/max';

const statusMap: Record<string, { text: string; color: string }> = {
    draft: { text: '草稿', color: 'default' },
    auditing: { text: '审核中', color: 'orange' },
    onsale: { text: '已上架', color: 'green' },
    rejected: { text: '已驳回', color: 'red' },
    offshelf: { text: '已下架', color: 'default' },
    sold: { text: '已售', color: 'purple' },
};

const tabList = [
    { key: 'all', label: '全部' },
    { key: 'auditing', label: '审核中' },
    { key: 'onsale', label: '已上架' },
    { key: 'rejected', label: '已驳回' },
    { key: 'offshelf', label: '已下架' },
];

const ProductMyList: React.FC = () => {
    // 兼容路由参数或默认全部
    const params = useParams<{ status?: string }>();
    const [tabKey, setTabKey] = useState<string>(params?.status || 'all');
    const [deleteTarget, setDeleteTarget] = useState<number | null>(null);

    const {
        data: resp,
        loading,
        refresh,
    } = useRequest(
        () => productMyPage({ status: tabKey === 'all' ? undefined : tabKey, page: 1, size: 100 }),
        { refreshDeps: [tabKey] },
    );

    const list = ((resp as any)?.data?.records || resp?.records || []) as any[];

    const handleOffshelf = async (productId: number) => {
        try {
            await productOffshelf(productId);
            message.success('已下架');
            refresh();
        } catch (e: any) {
            message.error(e?.message || '下架失败');
        }
    };

    const handleDelete = async () => {
        if (!deleteTarget) return;
        try {
            await productDelete(deleteTarget);
            message.success('已删除');
            setDeleteTarget(null);
            refresh();
        } catch (e: any) {
            message.error(e?.message || '删除失败');
        }
    };

    const actionButtons = (item: any) => {
        const btns: React.ReactNode[] = [];

        // 草稿/已驳回 → 编辑
        if (item.status === 'draft' || item.status === 'rejected') {
            btns.push(
                <Button
                    key="edit"
                    type="link"
                    onClick={() => history.push(`/product/publish/${item.productId}`)}
                >
                    编辑
                </Button>,
            );
        }

        // 已上架 → 下架
        if (item.status === 'onsale') {
            btns.push(
                <Button key="offshelf" type="link" danger onClick={() => handleOffshelf(item.productId)}>
                    下架
                </Button>,
            );
        }

        // 可删除的状态
        if (!['sold', 'onsale'].includes(item.status)) {
            btns.push(
                <Button
                    key="delete"
                    type="link"
                    danger
                    onClick={() => setDeleteTarget(item.productId)}
                >
                    删除
                </Button>,
            );
        }

        // 查看详情
        btns.push(
            <Button
                key="view"
                type="link"
                onClick={() => history.push(`/product/detail/${item.productId}`)}
            >
                查看
            </Button>,
        );

        return btns;
    };

    return (
        <PageContainer header={{ title: '我的商品', breadcrumb: {} }}>
            <Card>
                <Tabs
                    activeKey={tabKey}
                    onChange={(k) => setTabKey(k)}
                    items={tabList.map((t) => ({
                        key: t.key,
                        label: t.label,
                        children: loading ? (
                            <div style={{ textAlign: 'center', padding: 60 }}>
                                <Spin />
                            </div>
                        ) : list.length === 0 ? (
                            <Empty description="暂无商品" style={{ padding: 60 }}>
                                <Button
                                    type="primary"
                                    onClick={() => history.push('/product/publish')}
                                >
                                    去发布
                                </Button>
                            </Empty>
                        ) : (
                            <Row gutter={[16, 16]}>
                                {list.map((item: any) => {
                                    const images: string[] = item.images
                                        ? typeof item.images === 'string'
                                            ? JSON.parse(item.images)
                                            : item.images
                                        : [];
                                    const cover = item.cover || images?.[0] || '';
                                    const coverUrl = cover
                                        ? cover.startsWith('http')
                                            ? cover
                                            : `${TWT.static}${cover}`
                                        : '';
                                    return (
                                        <Col key={item.productId} xs={24} sm={12} md={8} lg={6}>
                                            <Card
                                                hoverable
                                                style={{ borderRadius: 12, overflow: 'hidden' }}
                                                bodyStyle={{ padding: 12 }}
                                                cover={
                                                    coverUrl ? (
                                                        <Image
                                                            src={coverUrl}
                                                            alt={item.title}
                                                            style={{ height: 180, objectFit: 'cover' }}
                                                            preview={false}
                                                            fallback="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
                                                        />
                                                    ) : (
                                                        <div
                                                            style={{
                                                                height: 180,
                                                                background: '#f5f5f5',
                                                                display: 'flex',
                                                                alignItems: 'center',
                                                                justifyContent: 'center',
                                                                color: '#ccc',
                                                            }}
                                                        >
                                                            暂无图片
                                                        </div>
                                                    )
                                                }
                                            >
                                                <div
                                                    style={{
                                                        fontSize: 14,
                                                        lineHeight: '20px',
                                                        height: 40,
                                                        overflow: 'hidden',
                                                        display: '-webkit-box',
                                                        WebkitLineClamp: 2,
                                                        WebkitBoxOrient: 'vertical',
                                                    }}
                                                >
                                                    {item.title}
                                                </div>
                                                <div
                                                    style={{
                                                        marginTop: 8,
                                                        display: 'flex',
                                                        justifyContent: 'space-between',
                                                        alignItems: 'center',
                                                    }}
                                                >
                                                    <span
                                                        style={{
                                                            color: '#FF6B35',
                                                            fontSize: 18,
                                                            fontWeight: 600,
                                                        }}
                                                    >
                                                        ¥{item.price}
                                                    </span>
                                                    <Tag color={statusMap[item.status]?.color}>
                                                        {statusMap[item.status]?.text || item.status}
                                                    </Tag>
                                                </div>
                                                <div style={{ marginTop: 4, fontSize: 12, color: '#999' }}>
                                                    浏览 {item.viewCount ?? 0} · 想要 {item.wantCount ?? 0}
                                                </div>
                                                <div
                                                    style={{
                                                        marginTop: 8,
                                                        borderTop: '1px solid #f0f0f0',
                                                        paddingTop: 8,
                                                    }}
                                                >
                                                    <Space size={0} wrap>
                                                        {actionButtons(item)}
                                                    </Space>
                                                </div>
                                            </Card>
                                        </Col>
                                    );
                                })}
                            </Row>
                        ),
                    }))}
                />
            </Card>

            <Modal
                title="确认删除"
                open={deleteTarget !== null}
                onCancel={() => setDeleteTarget(null)}
                onOk={handleDelete}
                okText="确认删除"
                okButtonProps={{ danger: true }}
            >
                <p>删除后不可恢复，确定要删除该商品吗？</p>
            </Modal>
        </PageContainer>
    );
};

export default ProductMyList;
