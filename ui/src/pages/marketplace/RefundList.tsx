import { refundPage, refundSellerPage } from '@/services/marketplace/api';
import { PageContainer, ProColumns, ProTable } from '@ant-design/pro-components';
import { history } from '@umijs/max';
import { Segmented, Tabs, Tag } from 'antd';
import React, { useState } from 'react';
import { formatPrice, formatTime } from './utils';

const STATUS_MAP: Record<string, { text: string; color: string }> = {
    pending_seller: { text: '待卖家处理', color: 'gold' },
    seller_agreed: { text: '卖家已同意', color: 'blue' },
    buyer_returned: { text: '买家已寄回', color: 'cyan' },
    refunding: { text: '退款中', color: 'gold' },
    refund_success: { text: '退款成功', color: 'green' },
    seller_rejected: { text: '卖家已拒绝', color: 'red' },
    mediation: { text: '平台介入中', color: 'purple' },
    closed: { text: '已关闭', color: 'default' },
};

const TYPE_LABELS: Record<string, string> = {
    refund_only: '仅退款',
    return_refund: '退货退款',
};

/** 处理中 / 已完成两个分组（后端 selectList 只支持单状态精确匹配，分组在前端做） */
const ACTIVE_STATUSES = ['pending_seller', 'seller_agreed', 'buyer_returned', 'refunding', 'mediation'];
const FINISHED_STATUSES = ['refund_success', 'closed', 'seller_rejected'];

const unwrap = (res: any) => res?.data ?? res;

const RefundList: React.FC = () => {
    const [role, setRole] = useState<'buyer' | 'seller'>('buyer');
    const [tab, setTab] = useState<'all' | 'active' | 'finished'>('all');

    const columns: ProColumns<any>[] = [
        {
            title: '退款单号',
            dataIndex: 'refundId',
            width: 100,
            render: (_, row) => `#${row.refundId}`,
        },
        {
            title: '订单号',
            dataIndex: 'orderId',
            width: 100,
            render: (_, row) => <a onClick={() => history.push(`/trade/order/detail?orderId=${row.orderId}`)}>{row.orderId}</a>,
        },
        {
            title: '类型',
            dataIndex: 'type',
            width: 90,
            render: (_, row) => TYPE_LABELS[row.type] || row.type,
        },
        {
            title: '退款金额',
            dataIndex: 'amount',
            width: 110,
            render: (_, row) => formatPrice(row.amount),
        },
        {
            title: '状态',
            dataIndex: 'status',
            width: 110,
            render: (_, row) => (
                <Tag color={STATUS_MAP[row.status]?.color}>{STATUS_MAP[row.status]?.text || row.status}</Tag>
            ),
        },
        {
            title: '申请时间',
            dataIndex: 'createTime',
            width: 140,
            render: (_, row) => formatTime(row.createTime),
        },
        {
            title: '操作',
            valueType: 'option',
            width: 90,
            render: (_, row) => (
                <a onClick={() => history.push(`/trade/refund/detail?refundId=${row.refundId}`)}>查看详情</a>
            ),
        },
    ];

    return (
        <PageContainer header={{ title: '我的退款', breadcrumb: {} }}>
            <Segmented
                style={{ marginBottom: 12 }}
                value={role}
                onChange={(v) => setRole(v as 'buyer' | 'seller')}
                options={[
                    { label: '我买到的', value: 'buyer' },
                    { label: '我卖出的', value: 'seller' },
                ]}
            />
            <Tabs
                activeKey={tab}
                onChange={(key) => setTab(key as 'all' | 'active' | 'finished')}
                items={[
                    { key: 'all', label: '全部' },
                    { key: 'active', label: '处理中' },
                    { key: 'finished', label: '已完成' },
                ]}
            />
            <ProTable
                key={`${role}-${tab}`}
                rowKey="refundId"
                search={false}
                options={false}
                columns={columns}
                params={{ role, tab }}
                request={async (params) => {
                    const fetch = role === 'seller' ? refundSellerPage : refundPage;
                    const res: any = await fetch({
                        role,
                        current: params.current,
                        pageSize: params.pageSize,
                    });
                    const payload = unwrap(res);
                    const records: any[] = payload?.records || [];
                    const filtered =
                        tab === 'active'
                            ? records.filter((row) => ACTIVE_STATUSES.includes(row.status))
                            : tab === 'finished'
                              ? records.filter((row) => FINISHED_STATUSES.includes(row.status))
                              : records;
                    return { data: filtered, total: payload?.total || 0, success: true };
                }}
                pagination={{ pageSize: 10, showSizeChanger: false }}
            />
        </PageContainer>
    );
};

export default RefundList;