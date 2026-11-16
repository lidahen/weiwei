import { adminReconPage, adminReconResolve, adminReconUnfreeze } from '@/services/marketplace/api';
import { PageContainer, ProColumns, ProTable } from '@ant-design/pro-components';
import type { ActionType } from '@ant-design/pro-components';
import { DatePicker, Input, message, Modal, Space, Tag } from 'antd';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import React, { useRef, useState } from 'react';
import { RECON_STATUS, moneyText, statusMeta } from './settlementShared';
import { reconActions, reconTypeText, validateResolveNote, validateUnfreezeReason } from './reconView';

const { TextArea } = Input;

const unwrap = (res: any) => res?.data ?? res;

const STATUS_OPTIONS = [
    { label: '待处理', value: 'open' },
    { label: '已处理', value: 'resolved' },
];

const Recon: React.FC = () => {
    const actionRef = useRef<ActionType>();
    const [bizDate, setBizDate] = useState<Dayjs>(dayjs().subtract(1, 'day'));
    const [resolveTarget, setResolveTarget] = useState<any>(null);
    const [resolveNote, setResolveNote] = useState('');
    const [unfreezeTarget, setUnfreezeTarget] = useState<any>(null);
    const [unfreezeReason, setUnfreezeReason] = useState('');
    const [submitting, setSubmitting] = useState(false);

    const submitResolve = async () => {
        const error = validateResolveNote(resolveNote);
        if (error) {
            message.warning(error);
            return;
        }
        setSubmitting(true);
        try {
            await adminReconResolve({ diffId: resolveTarget.diffId, note: resolveNote.trim() });
            message.success('差异已处理');
            setResolveTarget(null);
            setResolveNote('');
            actionRef.current?.reload();
        } finally {
            setSubmitting(false);
        }
    };

    const submitUnfreeze = async () => {
        const error = validateUnfreezeReason(unfreezeReason);
        if (error) {
            message.warning(error);
            return;
        }
        setSubmitting(true);
        try {
            const res: any = await adminReconUnfreeze({
                sellerId: unfreezeTarget.sellerId,
                reason: unfreezeReason.trim(),
            });
            const unfrozen = unwrap(res);
            if (unfrozen === false) {
                message.warning('该卖家账户不存在，未解冻');
            } else {
                message.success('已解冻卖家账户');
            }
            setUnfreezeTarget(null);
            setUnfreezeReason('');
            actionRef.current?.reload();
        } finally {
            setSubmitting(false);
        }
    };

    const columns: ProColumns<any>[] = [
        {
            title: '业务日',
            dataIndex: 'bizDate',
            width: 110,
            search: false,
            render: (_, row) => String(row.bizDate ?? '').slice(0, 10) || '-',
        },
        {
            title: '类型',
            dataIndex: 'type',
            width: 200,
            search: false,
            render: (_, row) => reconTypeText(row.type),
        },
        { title: '订单ID', dataIndex: 'orderId', width: 110, search: false, render: (_, row) => row.orderId ?? '-' },
        {
            title: '结算单ID',
            dataIndex: 'settlementId',
            width: 110,
            search: false,
            render: (_, row) => row.settlementId ?? '-',
        },
        { title: '卖家ID', dataIndex: 'sellerId', width: 100, search: false, render: (_, row) => row.sellerId ?? '-' },
        {
            title: '渠道金额',
            dataIndex: 'channelAmount',
            width: 120,
            search: false,
            render: (_, row) => moneyText(row.channelAmount),
        },
        {
            title: '平台金额',
            dataIndex: 'platformAmount',
            width: 120,
            search: false,
            render: (_, row) => moneyText(row.platformAmount),
        },
        {
            title: '状态',
            dataIndex: 'status',
            width: 100,
            valueType: 'select',
            valueEnum: Object.fromEntries(STATUS_OPTIONS.map((item) => [item.value, { text: item.label }])),
            render: (_, row) => {
                const meta = statusMeta(RECON_STATUS, row.status);
                return <Tag color={meta.color}>{meta.text}</Tag>;
            },
        },
        {
            title: '操作',
            valueType: 'option',
            width: 140,
            search: false,
            render: (_, row) => {
                const actions = reconActions(row.status);
                return (
                    <Space>
                        {actions.includes('resolve') && <a onClick={() => setResolveTarget(row)}>处理</a>}
                        {actions.includes('unfreeze') && <a onClick={() => setUnfreezeTarget(row)}>解冻</a>}
                    </Space>
                );
            },
        },
    ];

    return (
        <PageContainer header={{ title: '结算对账', breadcrumb: {} }}>
            <ProTable
                actionRef={actionRef}
                rowKey="diffId"
                options={false}
                columns={columns}
                search={{ labelWidth: 'auto' }}
                params={{ bizDate: bizDate.format('YYYY-MM-DD') }}
                toolBarRender={() => [
                    <DatePicker
                        key="bizDate"
                        value={bizDate}
                        allowClear={false}
                        onChange={(value) => value && setBizDate(value)}
                        placeholder="业务日"
                    />,
                ]}
                request={async (params) => {
                    const res: any = await adminReconPage({
                        bizDate: bizDate.format('YYYY-MM-DD'),
                        status: (params as any).status,
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

            <Modal
                title="处理对账差异"
                open={!!resolveTarget}
                okText="确认处理"
                okButtonProps={{
                    disabled: !!validateResolveNote(resolveNote),
                    loading: submitting,
                }}
                onOk={submitResolve}
                onCancel={() => {
                    setResolveTarget(null);
                    setResolveNote('');
                }}
                destroyOnClose
            >
                {resolveTarget && (
                    <p>
                        {reconTypeText(resolveTarget.type)}，订单 {resolveTarget.orderId ?? '-'}，卖家{' '}
                        {resolveTarget.sellerId ?? '-'}
                    </p>
                )}
                <TextArea
                    rows={4}
                    value={resolveNote}
                    onChange={(event) => setResolveNote(event.target.value)}
                    placeholder="请填写处理说明（必填）"
                    maxLength={255}
                    showCount
                />
            </Modal>

            <Modal
                title="解冻卖家账户"
                open={!!unfreezeTarget}
                okText="确认解冻"
                okButtonProps={{
                    disabled: !!validateUnfreezeReason(unfreezeReason),
                    loading: submitting,
                }}
                onOk={submitUnfreeze}
                onCancel={() => {
                    setUnfreezeTarget(null);
                    setUnfreezeReason('');
                }}
                destroyOnClose
            >
                {unfreezeTarget && <p>卖家 {unfreezeTarget.sellerId ?? '-'} 的账户解冻</p>}
                <TextArea
                    rows={3}
                    value={unfreezeReason}
                    onChange={(event) => setUnfreezeReason(event.target.value)}
                    placeholder="请填写解冻原因（必填）"
                    maxLength={255}
                    showCount
                />
            </Modal>
        </PageContainer>
    );
};

export default Recon;