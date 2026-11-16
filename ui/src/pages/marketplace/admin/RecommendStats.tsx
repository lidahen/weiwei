import { adminRecommendStats } from '@/services/marketplace/api';
import { PageContainer } from '@ant-design/pro-components';
import { useRequest } from '@umijs/max';
import { Card, Col, Row, Select, Table, Tag } from 'antd';
import React, { useState } from 'react';
import { marketplaceTokens } from '../styles/tokens';

const bucketLabel = (bucket: unknown) =>
    Number(bucket) === 1 ? <Tag color="purple">B 实验组</Tag> : <Tag color="blue">A 默认</Tag>;

const ctr = (clicks: unknown, impressions: unknown) => {
    const imp = Number(impressions) || 0;
    if (!imp) return '-';
    return `${(((Number(clicks) || 0) / imp) * 100).toFixed(1)}%`;
};

/**
 * 推荐效果看板：曝光/点击/UV 按日按 A/B 组拆分 + 点击 Top 商品
 * 数据源：GET /admin/recommend/stats（admin:marketplace:* 权限）
 */
const RecommendStats: React.FC = () => {
    const [days, setDays] = useState<number>(7);

    const { data, loading } = useRequest(() => adminRecommendStats({ days }), { refreshDeps: [days] });
    const resp: any = data;
    const payload = resp?.data ?? resp ?? {};
    const daily: any[] = payload?.daily || [];
    const top: any[] = payload?.top || [];

    const dailyColumns = [
        {
            title: '日期',
            dataIndex: 'statDate',
            width: 120,
            render: (v: any) => String(v ?? '').slice(0, 10),
        },
        { title: '分组', dataIndex: 'bucket', width: 100, render: bucketLabel },
        { title: '曝光次数', dataIndex: 'impressions', width: 110 },
        { title: '曝光用户', dataIndex: 'uv', width: 110 },
        { title: '点击次数', dataIndex: 'clicks', width: 110 },
        { title: '点击率 CTR', key: 'ctr', width: 110, render: (_: any, r: any) => ctr(r.clicks, r.impressions) },
    ];

    const topColumns = [
        {
            title: '商品',
            dataIndex: 'title',
            ellipsis: true,
            render: (v: any, r: any) => (
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                    {r.cover && (
                        <img
                            src={r.cover.startsWith('http') ? r.cover : `${r.cover}`}
                            alt=""
                            style={{ width: 32, height: 32, borderRadius: 6, objectFit: 'cover' }}
                        />
                    )}
                    {v || `#${r.productId}`}
                </span>
            ),
        },
        { title: '曝光次数', dataIndex: 'impressions', width: 110 },
        { title: '点击次数', dataIndex: 'clicks', width: 110 },
        { title: '点击率 CTR', key: 'ctr', width: 110, render: (_: any, r: any) => ctr(r.clicks, r.impressions) },
    ];

    return (
        <PageContainer
            header={{ title: '推荐效果', subTitle: '曝光 / 点击 / CTR（点击 = 曝光后 30 分钟内浏览该商品）', breadcrumb: {} }}
            extra={[
                <Select
                    key="days"
                    value={days}
                    onChange={setDays}
                    style={{ width: 130 }}
                    options={[
                        { label: '近 7 天', value: 7 },
                        { label: '近 14 天', value: 14 },
                        { label: '近 30 天', value: 30 },
                    ]}
                />,
            ]}
        >
            <Card
                title="每日曝光与点击（按 A/B 组）"
                style={{ marginBottom: 16, borderRadius: marketplaceTokens.radius.md }}
            >
                <Table
                    rowKey={(r) => `${r.statDate}-${r.bucket}`}
                    columns={dailyColumns}
                    dataSource={daily}
                    loading={loading}
                    pagination={false}
                    size="middle"
                />
            </Card>
            <Row gutter={16}>
                <Col span={24}>
                    <Card title="点击 Top 10 商品" style={{ borderRadius: marketplaceTokens.radius.md }}>
                        <Table
                            rowKey={(r) => String(r.productId)}
                            columns={topColumns}
                            dataSource={top}
                            loading={loading}
                            pagination={false}
                            size="middle"
                        />
                    </Card>
                </Col>
            </Row>
        </PageContainer>
    );
};

export default RecommendStats;
