import UploadImages from '@/components/marketplace/UploadImages';
import { adminWithdrawPage, adminWithdrawPay, adminWithdrawReview } from '@/services/marketplace/api';
import TWT from '@/setting';
import { PageContainer, ProColumns, ProTable } from '@ant-design/pro-components';
import type { ActionType } from '@ant-design/pro-components';
import type { UploadFile } from 'antd/es/upload/interface';
import { Input, message, Modal, Space, Tag } from 'antd';
import React, { useRef, useState } from 'react';
import { WITHDRAW_STATUS, moneyText, statusMeta } from './settlementShared';
import { validateReason, validateVoucher, voucherFromFile, withdrawActions } from './withdrawView';
import { formatTime } from '../utils';

const { TextArea } = Input;

const unwrap = (res: any) => res?.data ?? res;

const STATUS_OPTIONS = [
    { label: '待审核', value: 'pending' },
    { label: '待打款', value: 'approved' },
    { label: '已打款', value: 'paid' },
    { label: '已驳回', value: 'rejected' },
];

const Withdraw: React.FC = () => {
    const actionRef = useRef<ActionType>();
    const [reviewTarget, setReviewTarget] = useState<any>(null);
    const [reviewMode, setReviewMode] = useState<'approve' | 'reject'>('approve');
    const [reason, setReason] = useState('');
    const [payTarget, setPayTarget] = useState<any>(null);
    const [fileList, setFileList] = useState<UploadFile[]>([]);
    const [submitting, setSubmitting] = useState(false);

    const openReview = (row: any, mode: 'approve' | 'reject') => {
        setReviewTarget(row);
        setReviewMode(mode);
        setReason('');
    };

    const submitReview = async () => {
        const payload = {
            withdrawId: reviewTarget.withdrawId,
            action: reviewMode,
            reason: reviewMode === 'reject' ? reason.trim() : undefined,
        };
        setSubmitting(true);
        try {
            await adminWithdrawReview(payload);
            message.success(reviewMode === 'reject' ? '已驳回提现申请' : '已通过提现申请');
            setReviewTarget(null);
            actionRef.current?.reload();
        } finally {
            setSubmitting(false);
        }
    };

    const submitPay = async () => {
        const voucherUrl = voucherFromFile(fileList[0], TWT.static);
        const error = validateVoucher(voucherUrl);
        if (error) {
            message.warning(error);
            return;
        }
        setSubmitting(true);
        try {
            await adminWithdrawPay({ withdrawId: payTarget.withdrawId, voucherUrl: voucherUrl as string });
            message.success('已登记打款');
            setPayTarget(null);
            setFileList([]);
            actionRef.current?.reload();
        } finally {
            setSubmitting(false);
        }
    };

    const columns: ProColumns<any>[] = [
        {
            title: '提现单号',
            dataIndex: 'withdrawNo',
            width: 200,
            search: false,
            render: (_, row) => row.withdrawNo || `#${row.withdrawId}`,
        },
        { title: '卖家ID', dataIndex: 'sellerId', width: 100, search: false },
        { title: '金额', dataIndex: 'amount', width: 120, search: false, render: (_, row) => moneyText(row.amount) },
        {
            title: '状态',
            dataIndex: 'status',
            width: 100,
            valueType: 'select',
            valueEnum: Object.fromEntries(STATUS_OPTIONS.map((item) => [item.value, { text: item.label }])),
            render: (_, row) => {
                const meta = statusMeta(WITHDRAW_STATUS, row.status);
                return <Tag color={meta.color}>{meta.text}</Tag>;
            },
        },
        {
            title: '申请时间',
            dataIndex: 'createTime',
            width: 140,
            search: false,
            render: (_, row) => formatTime(row.createTime),
        },
        {
            title: '操作',
            valueType: 'option',
            width: 140,
            search: false,
            render: (_, row) => {
                const actions = withdrawActions(row.status);
                return (
                    <Space>
                        {actions.includes('approve') && <a onClick={() => openReview(row, 'approve')}>通过</a>}
                        {actions.includes('reject') && <a onClick={() => openReview(row, 'reject')}>驳回</a>}
                        {actions.includes('pay') && <a onClick={() => setPayTarget(row)}>打款</a>}
                    </Space>
                );
            },
        },
    ];

    const rejectMissing = reviewMode === 'reject' && !reason.trim();

    return (
        <PageContainer header={{ title: '提现审核', breadcrumb: {} }}>
            <ProTable
                actionRef={actionRef}
                rowKey="withdrawId"
                options={false}
                columns={columns}
                search={{ labelWidth: 'auto' }}
                request={async (params) => {
                    const res: any = await adminWithdrawPage({
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

            <Modal
                title={reviewMode === 'reject' ? '驳回提现申请' : '通过提现申请'}
                open={!!reviewTarget}
                okText={reviewMode === 'reject' ? '确认驳回' : '确认通过'}
                okButtonProps={{ danger: reviewMode === 'reject', disabled: rejectMissing, loading: submitting }}
                onOk={submitReview}
                onCancel={() => setReviewTarget(null)}
                destroyOnClose
            >
                {reviewTarget && (
                    <p>
                        提现单 {reviewTarget.withdrawNo || `#${reviewTarget.withdrawId}`}，金额{' '}
                        {moneyText(reviewTarget.amount)}
                    </p>
                )}
                <TextArea
                    rows={3}
                    value={reason}
                    onChange={(event) => setReason(event.target.value)}
                    placeholder={reviewMode === 'reject' ? '请填写驳回原因（必填）' : '备注（选填）'}
                    maxLength={255}
                    showCount
                />
            </Modal>

            <Modal
                title="登记打款凭证"
                open={!!payTarget}
                okText="确认已打款"
                okButtonProps={{ disabled: !voucherFromFile(fileList[0], TWT.static), loading: submitting }}
                onOk={submitPay}
                onCancel={() => {
                    setPayTarget(null);
                    setFileList([]);
                }}
                destroyOnClose
            >
                {payTarget && (
                    <p>
                        提现单 {payTarget.withdrawNo || `#${payTarget.withdrawId}`}，金额 {moneyText(payTarget.amount)}
                    </p>
                )}
                <UploadImages value={fileList} onChange={setFileList} max={1} />
            </Modal>
        </PageContainer>
    );
};

export default Withdraw;