import { adminSettlementDetail, adminSettlementPage } from '@/services/marketplace/api';
import { PageContainer, ProColumns, ProTable } from '@ant-design/pro-components';
import { Descriptions, Drawer, Spin, Tag, Typography } from 'antd';
import React, { useState } from 'react';
import { SETTLEMENT_STATUS, moneyText, statusMeta } from './settlementShared';
import { settlementRow } from './settlementView';
import { formatTime } from '../utils';

const unwrap = (res: any) => res?.data ?? res;

const STATUS_OPTIONS = [
    { label: '担保中', value: 'holding' },
    { label: '待打款', value: 'pending_settle' },
    { label: '已结算', value: 'settled' },
    { label: '已提现', value: 'withdrawn' },
    { label: '已关闭', value: 'closed' },
    { label: '已扣回', value: 'clawback' },
];

const Settlement: React.FC = () => {
    const [detail, setDetail] = useState<any>(null);
    const [drawerOpen, setDrawerOpen] = useState(false);
    const [detailLoading, setDetailLoading] = useState(false);

    const openDetail = async (row: any) => {
        setDetail(row);
        setDrawerOpen(true);
        setDetailLoading(true);
        try {
            const res: any = await adminSettlementDetail(row.settlementId);
            setDetail(unwrap(res) ?? row);
        } finally {
            setDetailLoading(false);
        }
    };

    const columns: ProColumns<any>[] = [
        { title: '结算单ID', dataIndex: 'settlementId', width: 110, search: false },
        { title: '订单ID', dataIndex: 'orderId', width: 110, search: false },
        { title: '卖家ID', dataIndex: 'sellerId', width: 100, search: false },
        {
            title: '应结金额',
            dataIndex: 'netAmount',
            width: 120,
            search: false,
            render: (_, row) => settlementRow(row).netText,
        },
        {
            title: '佣金',
            dataIndex: 'commissionAmount',
            width: 100,
            search: false,
            render: (_, row) => settlementRow(row).commissionText,
        },
        {
            title: '状态',
            dataIndex: 'status',
            width: 100,
            valueType: 'select',
            valueEnum: Object.fromEntries(STATUS_OPTIONS.map((item) => [item.value, { text: item.label }])),
            render: (_, row) => {
                const meta = statusMeta(SETTLEMENT_STATUS, row.status);
                return <Tag color={meta.color}>{meta.text}</Tag>;
            },
        },
        {
            title: '完成时间',
            dataIndex: 'doneAt',
            width: 140,
            search: false,
            render: (_, row) => formatTime(row.doneAt),
        },
        {
            title: '售后期至',
            dataIndex: 'holdUntil',
            width: 140,
            search: false,
            render: (_, row) => settlementRow(row).holdUntilText,
        },
        {
            title: '渠道流水',
            dataIndex: 'channelTransNo',
            width: 180,
            search: false,
            render: (_, row) => row.channelTransNo || '-',
        },
        {
            title: '操作',
            valueType: 'option',
            width: 80,
            search: false,
            render: (_, row) => <a onClick={() => openDetail(row)}>查看</a>,
        },
    ];

    const detailRow = detail ? settlementRow(detail) : null;

    return (
        <PageContainer header={{ title: '结算单', breadcrumb: {} }}>
            <ProTable
                rowKey="settlementId"
                options={false}
                columns={columns}
                search={{ labelWidth: 'auto' }}
                request={async (params) => {
                    const res: any = await adminSettlementPage({
                        status: params.status,
                        current: params.current,
                        pageSize: params.pageSize,
                    });
                    const payload = unwrap(res);
                    return {
                        data: payload?.records || [],
                        total: payload?.total || 0,
                        success: true,
                    };
                }}
            />

            <Drawer title="结算单详情" width={480} open={drawerOpen} onClose={() => setDrawerOpen(false)}>
                {detailLoading ? (
                    <Spin />
                ) : detail && detailRow ? (
                    <Descriptions column={1} size="small">
                        <Descriptions.Item label="结算单ID">{detail.settlementId}</Descriptions.Item>
                        <Descriptions.Item label="订单ID">{detail.orderId}</Descriptions.Item>
                        <Descriptions.Item label="退款ID">{detail.refundId ?? '-'}</Descriptions.Item>
                        <Descriptions.Item label="卖家ID">{detail.sellerId}</Descriptions.Item>
                        <Descriptions.Item label="买家ID">{detail.buyerId}</Descriptions.Item>
                        <Descriptions.Item label="租户ID">{detail.tenantId}</Descriptions.Item>
                        <Descriptions.Item label="成交金额">{detailRow.grossText}</Descriptions.Item>
                        <Descriptions.Item label="佣金">{detailRow.commissionText}</Descriptions.Item>
                        <Descriptions.Item label="应结金额">{detailRow.netText}</Descriptions.Item>
                        <Descriptions.Item label="状态">
                            <Tag color={statusMeta(SETTLEMENT_STATUS, detail.status).color}>
                                {detailRow.statusText}
                            </Tag>
                        </Descriptions.Item>
                        <Descriptions.Item label="完成时间">{formatTime(detail.doneAt)}</Descriptions.Item>
                        <Descriptions.Item label="售后期至">{detailRow.holdUntilText}</Descriptions.Item>
                        <Descriptions.Item label="结算时间">{formatTime(detail.settledAt)}</Descriptions.Item>
                        <Descriptions.Item label="渠道">{detail.channel || '-'}</Descriptions.Item>
                        <Descriptions.Item label="渠道流水">{detail.channelTransNo || '-'}</Descriptions.Item>
                        <Descriptions.Item label="失败次数">
                            <Typography.Text type={Number(detail.failCount ?? 0) > 0 ? 'danger' : undefined}>
                                {detail.failCount ?? 0}
                            </Typography.Text>
                        </Descriptions.Item>
                        <Descriptions.Item label="创建时间">{formatTime(detail.createTime)}</Descriptions.Item>
                        <Descriptions.Item label="更新时间">{formatTime(detail.updateTime)}</Descriptions.Item>
                    </Descriptions>
                ) : null}
            </Drawer>
        </PageContainer>
    );
};

export default Settlement;