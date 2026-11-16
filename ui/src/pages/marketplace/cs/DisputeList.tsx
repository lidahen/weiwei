import { csDisputeClaim, csDisputePage, csDisputeStats } from '@/services/marketplace/api';
import type { ProColumns } from '@ant-design/pro-components';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import { history, useRequest } from '@umijs/max';
import { Card, Col, Row, Space, Statistic, Tabs, Tag, message } from 'antd';
import React, { useRef, useState } from 'react';

const statusMap: Record<string, { text: string; color: string }> = {
    pending: { text: '待处理', color: 'red' },
    in_progress: { text: '处理中', color: 'orange' },
    closed: { text: '已结案', color: 'green' },
};

const typeMap: Record<string, string> = {
    refund: '仅退款',
    return: '退货退款',
    no_received: '未收到货',
    fake: '假货',
    other: '其他',
};

const DisputeList: React.FC = () => {
    const actionRef = useRef<any>();
    const [status, setStatus] = useState('pending');
    const { data: statsResp } = useRequest(csDisputeStats);
    const stats: any = (statsResp as any)?.data ?? (statsResp as any) ?? {};

    const columns: ProColumns<any>[] = [
        {
            title: '工单号',
            dataIndex: 'disputeId',
            width: 100,
            render: (_, r) => `DS${String(r.disputeId).padStart(6, '0')}`,
        },
        { title: '订单号', dataIndex: 'orderNo', search: false, copyable: true },
        {
            title: '类型',
            dataIndex: 'type',
            search: false,
            render: (_, r) => <Tag>{typeMap[r.type] || r.type}</Tag>,
        },
        { title: '买家', dataIndex: 'buyerNickName', search: false },
        { title: '卖家', dataIndex: 'sellerNickName', search: false },
        {
            title: '金额',
            dataIndex: 'amount',
            search: false,
            render: (_, r) => (
                <span style={{ color: '#FF6B35', fontWeight: 600 }}>¥{r.amount}</span>
            ),
        },
        {
            title: '优先级',
            dataIndex: 'priority',
            search: false,
            render: (_, r) => (
                <Tag
                    color={
                        r.priority === 'high' ? 'red' : r.priority === 'mid' ? 'orange' : 'default'
                    }
                >
                    {r.priority}
                </Tag>
            ),
        },
        {
            title: '状态',
            dataIndex: 'status',
            search: false,
            render: (_, r) => (
                <Tag color={statusMap[r.status]?.color}>{statusMap[r.status]?.text}</Tag>
            ),
        },
        { title: '创建时间', dataIndex: 'createTime', search: false, valueType: 'dateTime' },
        {
            title: '操作',
            valueType: 'option',
            render: (_, row) => (
                <Space>
                    {row.status === 'pending' && (
                        <a
                            onClick={async () => {
                                await csDisputeClaim(row.disputeId);
                                message.success('已领取');
                                actionRef.current?.reload();
                            }}
                        >
                            领取
                        </a>
                    )}
                    <a onClick={() => history.push(`/cs/marketplace/disputes/${row.disputeId}`)}>
                        详情
                    </a>
                </Space>
            ),
        },
    ];

    return (
        <PageContainer header={{ title: '客服工作台', subTitle: '纠纷工单一览', breadcrumb: {} }}>
            <Row gutter={16} style={{ marginBottom: 16 }}>
                <Col span={8}>
                    <Card>
                        <Statistic
                            title="待处理"
                            value={stats.pending || 0}
                            valueStyle={{ color: '#F87171' }}
                        />
                    </Card>
                </Col>
                <Col span={8}>
                    <Card>
                        <Statistic
                            title="处理中"
                            value={stats.inProgress || 0}
                            valueStyle={{ color: '#FF6B35' }}
                        />
                    </Card>
                </Col>
                <Col span={8}>
                    <Card>
                        <Statistic
                            title="已结案"
                            value={stats.closed || 0}
                            valueStyle={{ color: '#2E7D5B' }}
                        />
                    </Card>
                </Col>
            </Row>
            <Tabs
                activeKey={status}
                onChange={setStatus}
                items={[
                    { key: 'pending', label: '待处理' },
                    { key: 'in_progress', label: '处理中' },
                    { key: 'closed', label: '已结案' },
                ]}
            />
            <ProTable
                actionRef={actionRef}
                rowKey="disputeId"
                columns={columns}
                search={false}
                request={async (params) => {
                    const r: any = await csDisputePage({
                        status,
                        // 后端 PageUtils 读取 current/pageSize
                        current: params?.current,
                        pageSize: params?.pageSize,
                    });
                    return {
                        data: r?.records || r?.data?.records || [],
                        success: true,
                        total: r?.total || r?.data?.total || 0,
                    };
                }}
                params={{ status }}
            />
        </PageContainer>
    );
};
export default DisputeList;
