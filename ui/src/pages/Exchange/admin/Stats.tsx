import { adminStat } from '@/services/exchange/api';
import { PageContainer } from '@ant-design/pro-components';
import { useRequest } from '@umijs/max';
import { Card, Col, Empty, Row, Spin, Statistic, Tag } from 'antd';
import React from 'react';

/**
 * 交换管理 - 数据看板（镜像 Runner/admin/Stats 的 Row/Col/Card/Statistic 布局）
 *
 * 数据来自 GET /admin/exchange/stat（ExchangeItemServiceImpl#stat 实际返回字段）：
 * totalItems / onswapCount / lockedCount / swappedCount / sumViewCount / totalOffers / pendingOffers /
 * totalOrders / completedOrders / disputedOrders / dealRate / disputeRate / hotCategories。
 * dealRate、disputeRate 为 0-1 比例，无订单时为 null（显示 --）。
 */
const toPercent = (v: any): string =>
    v === null || v === undefined ? '--' : `${Math.round(Number(v) * 100)}%`;

// 换物漏斗相邻两级转化率（分母为 0 时显示 --，保留 1 位小数）
const funnelRate = (next: any, prev: any): string => {
    const n = Number(next) || 0;
    const p = Number(prev) || 0;
    if (p <= 0) return '--';
    return `${Math.round((n / p) * 1000) / 10}%`;
};

const Stats: React.FC = () => {
    const { data, loading } = useRequest(adminStat);

    if (loading) return <Spin />;
    const d: any = (data as any)?.data ?? data ?? {};
    const hotCategories: { catL1: string; cnt: number }[] = Array.isArray(d.hotCategories)
        ? d.hotCategories
        : [];

    // 换物漏斗：在架物品 → 累计浏览 → 累计意向 → 建单 → 完成（docs/交换广场二期-智能匹配与信用设计.md §5）
    const funnelStages: { title: string; value: number; color: string }[] = [
        { title: '在架物品', value: d.onswapCount || 0, color: '#13C2C2' },
        { title: '累计浏览', value: d.sumViewCount || 0, color: '#2F54EB' },
        { title: '累计意向', value: d.totalOffers || 0, color: '#FAAD14' },
        { title: '建单', value: d.totalOrders || 0, color: '#722ED1' },
        { title: '完成', value: d.completedOrders || 0, color: '#52C41A' },
    ];
    const funnelRates: string[] = [
        funnelRate(d.sumViewCount, d.onswapCount),
        funnelRate(d.totalOffers, d.sumViewCount),
        funnelRate(d.totalOrders, d.totalOffers),
        funnelRate(d.completedOrders, d.totalOrders),
    ];

    return (
        <PageContainer header={{ title: '交换数据看板', subTitle: '关键指标', breadcrumb: {} }}>
            <Row gutter={16}>
                <Col span={6}>
                    <Card>
                        <Statistic
                            title="在架物品数"
                            value={d.onswapCount || 0}
                            valueStyle={{ color: '#13C2C2' }}
                        />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card>
                        <Statistic
                            title="待处理意向"
                            value={d.pendingOffers || 0}
                            valueStyle={{ color: '#FAAD14' }}
                        />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card>
                        <Statistic
                            title="成交订单数"
                            value={d.completedOrders || 0}
                            valueStyle={{ color: '#52C41A' }}
                        />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card>
                        <Statistic
                            title="纠纷订单数"
                            value={d.disputedOrders || 0}
                            valueStyle={{ color: '#FF4D4F' }}
                        />
                    </Card>
                </Col>
            </Row>
            <Row gutter={16} style={{ marginTop: 16 }}>
                <Col span={24}>
                    <Card title="换物漏斗">
                        <div
                            style={{
                                display: 'flex',
                                flexWrap: 'wrap',
                                alignItems: 'center',
                                rowGap: 16,
                            }}
                        >
                            {funnelStages.map((s, i) => (
                                <React.Fragment key={s.title}>
                                    {i > 0 && (
                                        <div style={{ textAlign: 'center', padding: '0 20px' }}>
                                            <div style={{ color: '#9A9A9A', fontSize: 16 }}>→</div>
                                            <div style={{ fontSize: 12, color: '#9A9A9A' }}>
                                                {funnelRates[i - 1]}
                                            </div>
                                        </div>
                                    )}
                                    <Statistic
                                        title={s.title}
                                        value={s.value}
                                        valueStyle={{ color: s.color }}
                                    />
                                </React.Fragment>
                            ))}
                        </div>
                    </Card>
                </Col>
            </Row>
            <Row gutter={16} style={{ marginTop: 16 }}>
                <Col span={12}>
                    <Card title="成交完成率">
                        <Statistic
                            value={toPercent(d.dealRate)}
                            suffix={
                                d.dealRate !== null && d.dealRate !== undefined ? '%' : undefined
                            }
                            valueStyle={{ color: '#722ED1' }}
                        />
                    </Card>
                </Col>
                <Col span={12}>
                    <Card title="纠纷率">
                        <Statistic
                            value={toPercent(d.disputeRate)}
                            suffix={
                                d.disputeRate !== null && d.disputeRate !== undefined
                                    ? '%'
                                    : undefined
                            }
                            valueStyle={{ color: '#FF6B35' }}
                        />
                    </Card>
                </Col>
            </Row>
            <Row gutter={16} style={{ marginTop: 16 }}>
                <Col span={12}>
                    <Card title="物品总览">
                        <Row gutter={8}>
                            <Col span={6}>
                                <Statistic title="物品总数" value={d.totalItems || 0} />
                            </Col>
                            <Col span={6}>
                                <Statistic title="锁定中" value={d.lockedCount || 0} />
                            </Col>
                            <Col span={6}>
                                <Statistic title="已换出" value={d.swappedCount || 0} />
                            </Col>
                            <Col span={6}>
                                <Statistic title="意向总量" value={d.totalOffers || 0} />
                            </Col>
                        </Row>
                    </Card>
                </Col>
                <Col span={12}>
                    <Card title="热门类目 TOP5">
                        {hotCategories.length > 0 ? (
                            hotCategories.map((c, i) => (
                                <Tag key={`${c.catL1}-${i}`} color="blue">
                                    {c.catL1 || '未分类'}（{c.cnt}）
                                </Tag>
                            ))
                        ) : (
                            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无数据" />
                        )}
                    </Card>
                </Col>
            </Row>
        </PageContainer>
    );
};

export default Stats;
