import { adminKycPage, adminKycReview } from '@/services/marketplace/api';
import { PageContainer, ProColumns, ProTable } from '@ant-design/pro-components';
import type { ActionType } from '@ant-design/pro-components';
import {
    Button,
    Descriptions,
    Drawer,
    Input,
    message,
    Modal,
    Popconfirm,
    Space,
    Tag,
} from 'antd';
import React, { useRef, useState } from 'react';
import { KYC_STATUS, moneyText, statusMeta } from './settlementShared';
import { kycActions, toReviewPayload } from './kycView';
import { formatTime } from '../utils';

const { TextArea } = Input;

const unwrap = (res: any) => res?.data ?? res;

const KYC_OPTIONS = [
    { label: '未实名', value: 'unverified' },
    { label: '审核中', value: 'pending' },
    { label: '已通过', value: 'verified' },
    { label: '已驳回', value: 'rejected' },
];

const Kyc: React.FC = () => {
    const actionRef = useRef<ActionType>();
    const [current, setCurrent] = useState<any>(null);
    const [drawerOpen, setDrawerOpen] = useState(false);
    const [rejectOpen, setRejectOpen] = useState(false);
    const [rejectNote, setRejectNote] = useState('');
    const [submitting, setSubmitting] = useState(false);

    const openDetail = (row: any) => {
        setCurrent(row);
        setDrawerOpen(true);
    };

    const review = async (row: any, result: string, note?: string) => {
        const payload = toReviewPayload(row, result, note);
        if (!payload) {
            message.warning('驳回时必须填写备注');
            return;
        }
        setSubmitting(true);
        try {
            await adminKycReview(payload);
            message.success(result === 'verified' ? '已通过实名审核' : '已驳回实名申请');
            setRejectOpen(false);
            setRejectNote('');
            setDrawerOpen(false);
            actionRef.current?.reload();
        } finally {
            setSubmitting(false);
        }
    };

    const columns: ProColumns<any>[] = [
        { title: '卖家ID', dataIndex: 'sellerId', width: 100, search: false },
        { title: '姓名', dataIndex: 'accountName', width: 120, search: false, render: (_, row) => row.accountName || '-' },
        { title: '证件号', dataIndex: 'idCardNo', width: 150, search: false, render: (_, row) => row.idCardNo || '-' },
        { title: '银行卡', dataIndex: 'bankCardNo', width: 150, search: false, render: (_, row) => row.bankCardNo || '-' },
        { title: '开户行', dataIndex: 'bankName', width: 140, search: false, render: (_, row) => row.bankName || '-' },
        {
            title: '状态',
            dataIndex: 'kycStatus',
            width: 100,
            valueType: 'select',
            valueEnum: Object.fromEntries(KYC_OPTIONS.map((item) => [item.value, { text: item.label }])),
            render: (_, row) => {
                const meta = statusMeta(KYC_STATUS, row.kycStatus);
                return <Tag color={meta.color}>{meta.text}</Tag>;
            },
        },
        {
            title: '提交时间',
            dataIndex: 'updateTime',
            width: 140,
            search: false,
            render: (_, row) => formatTime(row.updateTime || row.createTime),
        },
        {
            title: '操作',
            valueType: 'option',
            width: 120,
            search: false,
            render: (_, row) => (
                <Space>
                    <a onClick={() => openDetail(row)}>查看</a>
                    {kycActions(row.kycStatus).includes('verified') && (
                        <Popconfirm
                            title="确认通过该卖家的实名资料？"
                            onConfirm={() => review(row, 'verified')}
                            disabled={submitting}
                        >
                            <a>通过</a>
                        </Popconfirm>
                    )}
                </Space>
            ),
        },
    ];

    return (
        <PageContainer header={{ title: '实名审核', breadcrumb: {} }}>
            <ProTable
                actionRef={actionRef}
                rowKey="accountId"
                options={false}
                columns={columns}
                search={{ labelWidth: 'auto' }}
                request={async (params) => {
                    const res: any = await adminKycPage({
                        kycStatus: params.kycStatus,
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

            <Drawer
                title="实名资料"
                width={420}
                open={drawerOpen}
                onClose={() => setDrawerOpen(false)}
                footer={
                    current && kycActions(current.kycStatus).length > 0 ? (
                        <Space>
                            <Button loading={submitting} onClick={() => review(current, 'verified')}>
                                通过
                            </Button>
                            <Button danger loading={submitting} onClick={() => setRejectOpen(true)}>
                                驳回
                            </Button>
                        </Space>
                    ) : null
                }
            >
                {current && (
                    <Descriptions column={1} size="small">
                        <Descriptions.Item label="卖家ID">{current.sellerId}</Descriptions.Item>
                        <Descriptions.Item label="姓名">{current.accountName || '-'}</Descriptions.Item>
                        <Descriptions.Item label="证件号">{current.idCardNo || '-'}</Descriptions.Item>
                        <Descriptions.Item label="银行卡">{current.bankCardNo || '-'}</Descriptions.Item>
                        <Descriptions.Item label="开户行">{current.bankName || '-'}</Descriptions.Item>
                        <Descriptions.Item label="可提现余额">{moneyText(current.balance)}</Descriptions.Item>
                        <Descriptions.Item label="账户冻结">
                            {Number(current.frozen ?? 0) === 1 ? '已冻结' : '正常'}
                        </Descriptions.Item>
                        <Descriptions.Item label="提交时间">
                            {formatTime(current.updateTime || current.createTime)}
                        </Descriptions.Item>
                    </Descriptions>
                )}
            </Drawer>

            <Modal
                title="驳回实名申请"
                open={rejectOpen}
                okText="确认驳回"
                okButtonProps={{ danger: true, disabled: !rejectNote.trim(), loading: submitting }}
                onOk={() => review(current, 'rejected', rejectNote)}
                onCancel={() => setRejectOpen(false)}
                destroyOnClose
            >
                <TextArea
                    rows={4}
                    value={rejectNote}
                    onChange={(event) => setRejectNote(event.target.value)}
                    placeholder="请填写驳回原因（必填）"
                    maxLength={255}
                    showCount
                />
            </Modal>
        </PageContainer>
    );
};

export default Kyc;