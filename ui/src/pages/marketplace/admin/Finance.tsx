import { adminFinanceOverview } from '@/services/marketplace/api';
import { PageContainer, ProColumns, ProTable } from '@ant-design/pro-components';
import { Card, Col, Empty, Row, Statistic, Tag, message } from 'antd';
import React, { useEffect, useState } from 'react';
import { formatPrice, formatTime } from '../utils';

const unwrap = (res: any) => res?.data ?? res;

const TYPE_LABELS: Record<string, { text: string; color: string }> = {
    pay: { text: '收入', color: 'green' },
    refund: { text: '退款', color: 'red' },
};

/** 后端 resultType=map 返回 snake_case 列名，前端统一归一为驼峰 */
const normalizeFlow = (row: any) => ({
    flowId: row.flowId ?? row.flow_id,
    orderId: row.orderId ?? row.order_id,
    type: row.type,
    amount: row.amount,
    channel: row.channel,
    thirdTransNo: row.thirdTransNo ?? row.third_trans_no,
    createTime: row.createTime ?? row.create_time,
});

const Finance: React.FC = () => {
    const [loading, setLoading] = useState(true);
    const [overview, setOverview] = useState<any>({});
    const [flows, setFlows] = useState<any[]>([]);

    useEffect(() => {
        let cancelled = false;
        adminFinanceOverview()
            .then((res: any) => {
                if (cancelled) return;
                const payload = unwrap(res);
                setOverview(payload || {});
                setFlows((payload?.recentFlows || []).map(normalizeFlow));
            })
            .catch(() => {
                if (!cancelled) message.error('加载失败，请稍后重试');
            })
            .finally(() => {
                if (!cancelled) setLoading(false);
            });
        return () => {
            cancelled = true;
        };
    }, []);

    const columns: ProColumns<any>[] = [
        { title: '流水号', dataIndex: 'flowId', width: 100 },
        { title: '订单号', dataIndex: 'orderId', width: 100 },
        {
            title: '类型',
            dataIndex: 'type',
            width: 90,
            render: (_, row) => (
                <Tag color={TYPE_LABELS[row.type]?.color}>{TYPE_LABELS[row.type]?.text || row.type}</Tag>
            ),
        },
        { title: '金额', dataIndex: 'amount', width: 120, render: (_, row) => formatPrice(row.amount) },
        { title: '渠道', dataIndex: 'channel', width: 100, render: (_, row) => row.channel || '-' },
        { title: '第三方流水号', dataIndex: 'thirdTransNo', width: 200, render: (_, row) => row.thirdTransNo || '-' },
        { title: '时间', dataIndex: 'createTime', width: 150, render: (_, row) => formatTime(row.createTime) },
    ];

    return (
        <PageContainer header={{ title: '资金对账', breadcrumb: {} }}>
            <Row gutter={16} style={{ marginBottom: 16 }}>
                <Col xs={12} md={6}>
                    <Card loading={loading}>
                        <Statistic title="总成交额" value={formatPrice(overview.totalGmv)} />
                    </Card>
                </Col>
                <Col xs={12} md={6}>
                    <Card loading={loading}>
                        <Statistic title="已支付 GMV" value={formatPrice(overview.paidGmv)} />
                    </Card>
                </Col>
                <Col xs={12} md={6}>
                    <Card loading={loading}>
                        <Statistic
                            title="已退款金额"
                            value={formatPrice(overview.refundedGmv)}
                            valueStyle={{ color: '#cf1322' }}
                        />
                    </Card>
                </Col>
                <Col xs={12} md={6}>
                    <Card loading={loading}>
                        <Statistic title="待处理提现" value={overview.pendingWithdraws ?? 0} suffix="笔" />
                    </Card>
                </Col>
            </Row>

            <Card title="最近资金流水">
                {!loading && flows.length === 0 ? (
                    <Empty description="暂无资金流水" />
                ) : (
                    <ProTable
                        rowKey="flowId"
                        search={false}
                        options={false}
                        columns={columns}
                        dataSource={flows}
                        loading={loading}
                        pagination={false}
                    />
                )}
            </Card>
        </PageContainer>
    );
};

export default Finance;