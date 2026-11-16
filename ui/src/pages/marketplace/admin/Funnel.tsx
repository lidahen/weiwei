import { adminFunnelStats } from '@/services/marketplace/api';
import { PageContainer } from '@ant-design/pro-components';
import { Alert, Card, Col, Row, Select, Space, Statistic, Table, Tag, message } from 'antd';
import React, { useEffect, useState } from 'react';
import {
    EVENT_LABELS,
    conversionOf,
    formatConversion,
    funnelRows,
    pickTenant,
    summaryCount,
    unwrap,
} from './funnelView';

const DAY_OPTIONS = [
    { value: 7, label: '近 7 天' },
    { value: 14, label: '近 14 天' },
    { value: 30, label: '近 30 天' },
];

/**
 * 社区漏斗看板 v0：核心事件计数 + 转化率，按社区（租户）切片。
 * tenantId 为空时后端只返回社区选项，前端自动选中第一个。
 */
const Funnel: React.FC = () => {
    const [days, setDays] = useState(7);
    const [tenantId, setTenantId] = useState<number | undefined>();
    const [data, setData] = useState<any>(null);
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        let alive = true;
        setLoading(true);
        adminFunnelStats({ days, tenantId })
            .then((res) => {
                if (!alive) return;
                const payload = unwrap(res);
                setData(payload);
                const next = pickTenant(payload, tenantId);
                if (next && next !== tenantId) setTenantId(next);
            })
            .catch(() => {
                if (alive) message.error('加载失败，请稍后重试');
            })
            .finally(() => {
                if (alive) setLoading(false);
            });
        return () => {
            alive = false;
        };
    }, [days, tenantId]);

    const tenants: any[] = data?.tenants || [];
    const rows = funnelRows(data);
    const noCommunity = !loading && tenants.length === 0;

    const columns: any[] = [
        {
            title: '环节',
            dataIndex: 'key',
            render: (_: any, row: any) => EVENT_LABELS[row.key] || row.key,
        },
        { title: '次数', dataIndex: 'count' },
        { title: '用户数', dataIndex: 'uv' },
        {
            title: '转化率',
            dataIndex: 'conversion',
            render: (_: any, row: any) => formatConversion(row.conversion),
        },
    ];

    return (
        <PageContainer header={{ title: '社区漏斗', breadcrumb: {} }}>
            <Space style={{ marginBottom: 16 }} wrap>
                <Select
                    style={{ width: 200 }}
                    placeholder="选择社区"
                    value={tenantId}
                    onChange={(value) => setTenantId(value)}
                    options={tenants.map((tenant) => ({
                        value: tenant.tenantId,
                        label: tenant.tenantName || `社区 ${tenant.tenantId}`,
                    }))}
                />
                <Select
                    style={{ width: 140 }}
                    value={days}
                    onChange={(value) => setDays(value)}
                    options={DAY_OPTIONS}
                />
                <Tag color="blue">北极星：单社区周完成交易数</Tag>
            </Space>

            {noCommunity ? (
                <Alert type="info" showIcon message="暂无社区数据" description="漏斗事件从本期上线开始计数，有交易后即可看到。" />
            ) : (
                <>
                    <Row gutter={16} style={{ marginBottom: 16 }}>
                        <Col span={8}>
                            <Card>
                                <Statistic title="完成交易数" value={summaryCount(data, 'confirm_receipt')} />
                            </Card>
                        </Col>
                        <Col span={8}>
                            <Card>
                                <Statistic
                                    title="详情 → 会话转化"
                                    value={formatConversion(conversionOf(data, 'im_start'))}
                                />
                            </Card>
                        </Col>
                        <Col span={8}>
                            <Card>
                                <Statistic
                                    title="支付 → 确认完成率"
                                    value={formatConversion(conversionOf(data, 'confirm_receipt'))}
                                />
                            </Card>
                        </Col>
                    </Row>

                    <Table
                        rowKey={(row: any) => row.key}
                        loading={loading}
                        dataSource={rows}
                        columns={columns}
                        pagination={false}
                        size="middle"
                    />
                </>
            )}
        </PageContainer>
    );
};

export default Funnel;