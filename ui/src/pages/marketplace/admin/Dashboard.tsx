import CountUp from '@/components/marketplace/CountUp';
import { adminOperationMetrics, adminOverview } from '@/services/marketplace/api';
import { PageContainer } from '@ant-design/pro-components';
import { useRequest } from '@umijs/max';
import { Card, Col, Empty, Row, Segmented, Skeleton, Statistic, Tag, Tooltip } from 'antd';
import React, { useEffect, useRef, useState } from 'react';
import * as echarts from 'echarts';

const ORANGE = '#FF6B35';
const MINT = '#4ECDC4';

/** reduced-motion 用户关闭图表动画 */
const chartAnimation = () =>
    !window.matchMedia('(prefers-reduced-motion: reduce)').matches;

/** 统一的口径提示标题（虚线下划线 + hover 说明），避免运营对不上数时靠猜 */
const HintTitle: React.FC<{ text: string; hint?: string }> = ({ text, hint }) =>
    hint ? (
        <Tooltip title={hint}>
            <span style={{ borderBottom: '1px dashed #d9d9d9', cursor: 'help' }}>{text}</span>
        </Tooltip>
    ) : (
        <>{text}</>
    );

/** 比率展示：后端分母为 0 时返回 null，这里显示"—"而不是误导性的 0% */
const pct = (v: unknown) => (v === null || v === undefined ? '—' : `${v}%`);

const MetricCard: React.FC<{
    title: string;
    value: React.ReactNode;
    hint?: string;
    sub?: React.ReactNode;
    color: string;
    delay?: number;
}> = ({ title, value, hint, sub, color, delay = 0 }) => (
    <Card className="twt-fade-up" hoverable style={{ animationDelay: `${delay}ms`, height: '100%' }}>
        <Statistic
            value={0}
            title={<HintTitle text={title} hint={hint} />}
            formatter={() => value}
            valueStyle={{ color, fontSize: 26 }}
        />
        {sub ? <div style={{ marginTop: 10, fontSize: 12, color: '#8c8c8c' }}>{sub}</div> : null}
    </Card>
);

/** 趋势：GMV 面积图（左轴） + 订单数/发布量柱状（右轴） */
const TrendChart: React.FC<{ data: any[] }> = ({ data }) => {
    const ref = useRef<HTMLDivElement>(null);

    useEffect(() => {
        if (!ref.current || !data.length) return;
        const chart = echarts.init(ref.current);
        chart.setOption({
            animation: chartAnimation(),
            grid: { left: 56, right: 48, top: 32, bottom: 28 },
            tooltip: { trigger: 'axis' },
            legend: { data: ['GMV', '成交量', '发布量'], top: 0, right: 0 },
            xAxis: { type: 'category', data: data.map((x) => x.date), boundaryGap: true },
            yAxis: [
                { type: 'value', name: 'GMV(¥)', splitLine: { lineStyle: { type: 'dashed', color: '#EEE' } } },
                { type: 'value', name: '笔/件', minInterval: 1, splitLine: { show: false } },
            ],
            series: [
                {
                    name: 'GMV',
                    type: 'line',
                    smooth: true,
                    data: data.map((x) => x.amount),
                    lineStyle: { color: ORANGE, width: 2 },
                    itemStyle: { color: ORANGE },
                    areaStyle: { color: ORANGE, opacity: 0.15 },
                    symbolSize: 6,
                },
                {
                    name: '成交量',
                    type: 'bar',
                    yAxisIndex: 1,
                    data: data.map((x) => x.orderCount),
                    itemStyle: { color: MINT, borderRadius: [4, 4, 0, 0], opacity: 0.85 },
                    barMaxWidth: 14,
                },
                {
                    name: '发布量',
                    type: 'bar',
                    yAxisIndex: 1,
                    data: data.map((x) => x.publishCount),
                    itemStyle: { color: '#9BB8FF', borderRadius: [4, 4, 0, 0], opacity: 0.85 },
                    barMaxWidth: 14,
                },
            ],
        });
        const onResize = () => chart.resize();
        window.addEventListener('resize', onResize);
        return () => {
            window.removeEventListener('resize', onResize);
            chart.dispose();
        };
    }, [data]);

    if (!data.length) {
        return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无趋势数据" style={{ padding: '48px 0' }} />;
    }
    return <div ref={ref} style={{ height: 280 }} />;
};

/** 品类分布：横向条形图（在售商品按一级类目） */
const CategoryChart: React.FC<{ data: any[] }> = ({ data }) => {
    const ref = useRef<HTMLDivElement>(null);

    useEffect(() => {
        if (!ref.current || !data.length) return;
        const sorted = [...data].sort((a, b) => (b.value || 0) - (a.value || 0));
        const chart = echarts.init(ref.current);
        chart.setOption({
            animation: chartAnimation(),
            grid: { left: 90, right: 40, top: 8, bottom: 24 },
            tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
            xAxis: { type: 'value', minInterval: 1 },
            yAxis: { type: 'category', data: sorted.map((x) => x.name), inverse: true },
            series: [
                {
                    type: 'bar',
                    data: sorted.map((x) => x.value),
                    itemStyle: {
                        color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
                            { offset: 0, color: '#FF8559' },
                            { offset: 1, color: ORANGE },
                        ]),
                        borderRadius: [0, 6, 6, 0],
                    },
                    barMaxWidth: 18,
                    label: { show: true, position: 'right', color: '#6B6B6B' },
                },
            ],
        });
        const onResize = () => chart.resize();
        window.addEventListener('resize', onResize);
        return () => {
            window.removeEventListener('resize', onResize);
            chart.dispose();
        };
    }, [data]);

    if (!data.length) {
        return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无商品数据" style={{ padding: '48px 0' }} />;
    }
    return <div ref={ref} style={{ height: Math.max(220, data.length * 36) }} />;
};

const Dashboard: React.FC = () => {
    const [days, setDays] = useState(30);
    const { data, loading } = useRequest(adminOverview);
    // 窗口切换即重新拉取运营指标（refreshDeps 依赖 days）
    const { data: metricResp, loading: metricLoading } = useRequest(() => adminOperationMetrics(days), {
        refreshDeps: [days],
    });

    const d: any = (data as any)?.data || data || {};
    const m: any = (metricResp as any)?.data || metricResp || {};
    const def: Record<string, string> = m.definitions || {};
    const trend: any[] = m.trend || [];
    const win = m.days || days;

    // 加载骨架：与内容同构（4 统计卡 + 2 图表卡），避免布局跳动
    if (loading) {
        return (
            <PageContainer header={{ title: '运营仪表盘', subTitle: '关键指标一览', breadcrumb: {} }}>
                <Row gutter={16}>
                    {Array.from({ length: 4 }).map((_, i) => (
                        <Col span={6} key={i}>
                            <Card>
                                <Skeleton active paragraph={{ rows: 1 }} title={{ width: '40%' }} />
                            </Card>
                        </Col>
                    ))}
                </Row>
                <Row gutter={16} style={{ marginTop: 16 }}>
                    <Col span={8}>
                        <Card>
                            <Skeleton active paragraph={{ rows: 5 }} />
                        </Card>
                    </Col>
                    <Col span={16}>
                        <Card>
                            <Skeleton active paragraph={{ rows: 8 }} />
                        </Card>
                    </Col>
                </Row>
            </PageContainer>
        );
    }

    return (
        <PageContainer header={{ title: '运营仪表盘', subTitle: '关键指标一览', breadcrumb: {} }}>
            <Row gutter={16}>
                <Col span={6}>
                    <Card className="twt-fade-up" hoverable>
                        <Statistic
                            title="总用户数"
                            formatter={() => <CountUp value={d.totalUsers || 0} />}
                            valueStyle={{ color: ORANGE }}
                        />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card className="twt-fade-up" hoverable style={{ animationDelay: '60ms' }}>
                        <Statistic
                            title="在售商品"
                            formatter={() => <CountUp value={d.onsaleProducts || 0} />}
                            valueStyle={{ color: '#2E7D5B' }}
                        />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card className="twt-fade-up" hoverable style={{ animationDelay: '120ms' }}>
                        <Statistic
                            title="总订单数"
                            formatter={() => <CountUp value={d.totalOrders || 0} />}
                            valueStyle={{ color: '#3B82F6' }}
                        />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card className="twt-fade-up" hoverable style={{ animationDelay: '180ms' }}>
                        <Statistic
                            title="累计 GMV"
                            formatter={() => <CountUp value={d.gmv || 0} precision={2} prefix="¥" />}
                            valueStyle={{ color: ORANGE }}
                        />
                    </Card>
                </Col>
            </Row>

            {/* 自营运营核心指标：阶段一「一个小区能不能转起来」的五个验证口径 */}
            <Card
                title="自营运营指标"
                className="twt-fade-up"
                style={{ marginTop: 16, animationDelay: '210ms' }}
                extra={
                    <Segmented
                        value={days}
                        onChange={(v) => setDays(Number(v))}
                        options={[
                            { label: '7 天', value: 7 },
                            { label: '30 天', value: 30 },
                            { label: '90 天', value: 90 },
                        ]}
                    />
                }
            >
                <Row gutter={16}>
                    <Col span={5}>
                        <MetricCard
                            title={`发布量（近 ${win} 天）`}
                            value={<CountUp value={m.publishCount || 0} suffix=" 件" />}
                            hint={def.publish}
                            sub={`今日 ${m.publishToday || 0} 件 · 日均 ${m.publishPerDay ?? 0} 件`}
                            color={ORANGE}
                        />
                    </Col>
                    <Col span={5}>
                        <MetricCard
                            title={`成交量（近 ${win} 天）`}
                            value={<CountUp value={m.dealCount || 0} suffix=" 单" />}
                            hint={def.deal}
                            sub={`GMV ¥${Number(m.dealGmv || 0).toLocaleString('zh-CN')} · 日均 ${m.dealPerDay ?? 0} 单`}
                            color={MINT}
                        />
                    </Col>
                    <Col span={5}>
                        <MetricCard
                            title="履约率"
                            value={pct(m.fulfillmentRate)}
                            hint={def.fulfillment}
                            sub={
                                <>
                                    已完成 {m.doneCount || 0} 单
                                    {m.overdueUnshippedCount > 0 ? (
                                        <>
                                            {' · '}
                                            <Tooltip title={def.overdue}>
                                                <Tag color="red" style={{ marginInlineEnd: 0 }}>
                                                    超时未发货 {m.overdueUnshippedCount}
                                                </Tag>
                                            </Tooltip>
                                        </>
                                    ) : (
                                        ''
                                    )}
                                </>
                            }
                            color="#2E7D5B"
                        />
                    </Col>
                    <Col span={5}>
                        <MetricCard
                            title="复购率"
                            value={pct(m.repurchaseRate)}
                            hint={def.repurchase}
                            sub={`成交买家 ${m.buyerCount || 0} 人 · 复购 ${m.repeatBuyerCount || 0} 人`}
                            color="#3B82F6"
                        />
                    </Col>
                    <Col span={4}>
                        <MetricCard
                            title="纠纷率"
                            value={pct(m.disputeRate)}
                            hint={def.dispute}
                            sub={`纠纷 ${m.disputeCount || 0} 单 · 每百单 ${m.disputePer100 ?? '—'}`}
                            color={m.disputeCount > 0 ? '#CF1322' : '#8C8C8C'}
                        />
                    </Col>
                </Row>
            </Card>

            <Row gutter={16} style={{ marginTop: 16 }}>
                <Col span={8}>
                    <Card title="待处理事项" className="twt-fade-up" style={{ animationDelay: '240ms' }}>
                        <Statistic
                            title="待审核商品"
                            formatter={() => <CountUp value={d.pendingAudits || 0} />}
                            suffix={<Tag color="orange">待处理</Tag>}
                        />
                        <Statistic
                            title="待处理举报"
                            formatter={() => <CountUp value={d.pendingReports || 0} />}
                            suffix={<Tag color="red">待处理</Tag>}
                            style={{ marginTop: 16 }}
                        />
                        <Statistic
                            title="待仲裁纠纷"
                            formatter={() => <CountUp value={d.pendingDisputes || 0} />}
                            suffix={<Tag color="gold">待仲裁</Tag>}
                            style={{ marginTop: 16 }}
                        />
                        <Statistic
                            title="待付款订单"
                            formatter={() => <CountUp value={m.unpaidCount || 0} />}
                            suffix={<Tag>未成交</Tag>}
                            style={{ marginTop: 16 }}
                        />
                    </Card>
                </Col>
                <Col span={16}>
                    <Card
                        title={`近 ${win} 日趋势`}
                        className="twt-fade-up"
                        style={{ animationDelay: '300ms' }}
                        extra={
                            metricLoading ? <Tag color="processing">加载中</Tag> : <Tag color="success">真实数据</Tag>
                        }
                    >
                        <TrendChart data={trend} />
                    </Card>
                </Col>
            </Row>
            <Card title="品类分布" className="twt-fade-up" style={{ marginTop: 16, animationDelay: '360ms' }}>
                <CategoryChart data={d.categoryDist || []} />
            </Card>
        </PageContainer>
    );
};

export default Dashboard;
