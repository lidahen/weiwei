import { sellerAccount, sellerKycApply, sellerWithdrawApply, sellerWithdrawPage } from '@/services/marketplace/api';
import { PageContainer, ProColumns, ProTable } from '@ant-design/pro-components';
import { useRequest } from '@umijs/max';
import { Alert, Button, Card, Descriptions, Form, Input, message, Space, Tag, Typography } from 'antd';
import React, { useState } from 'react';
import type { KycForm } from './admin/settlementShared';
import {
    KYC_STATUS,
    WITHDRAW_STATUS,
    moneyText,
    statusMeta,
    toAmount,
    validateWithdrawAmount,
} from './admin/settlementShared';
import { canSubmitKyc, toKycPayload } from './mySettlementView';
import { formatTime } from './utils';

const { Text, Title } = Typography;

const unwrap = (res: any) => res?.data ?? res;

const EMPTY_KYC: KycForm = { accountName: '', idCardNo: '', bankCardNo: '', bankName: '' };

const MySettlement: React.FC = () => {
    const [kycForm] = Form.useForm();
    const [kycSubmitting, setKycSubmitting] = useState(false);
    const [withdrawText, setWithdrawText] = useState('');
    const [withdrawSubmitting, setWithdrawSubmitting] = useState(false);
    const [reloadKey, setReloadKey] = useState(0);

    const { data, loading, refresh } = useRequest(sellerAccount);
    const account: any = unwrap(data);
    const kycStatus: string = account?.kycStatus ?? 'unverified';
    const balance = Number(account?.balance ?? 0);
    const frozen = Number(account?.frozen ?? 0) === 1;
    const kycMeta = statusMeta(KYC_STATUS, kycStatus);

    const kycValues = (Form.useWatch([], kycForm) as KycForm | undefined) ?? EMPTY_KYC;
    const kycReady = canSubmitKyc(kycValues);

    const handleKycSubmit = async () => {
        const payload = toKycPayload(kycValues);
        setKycSubmitting(true);
        try {
            await sellerKycApply(payload);
            message.success('实名资料已提交，等待审核');
            kycForm.resetFields();
            refresh();
            setReloadKey((key) => key + 1);
        } finally {
            setKycSubmitting(false);
        }
    };

    const handleWithdraw = async () => {
        const error = validateWithdrawAmount(withdrawText, balance);
        if (error) {
            message.warning(error);
            return;
        }
        if (frozen) {
            message.warning('账户已冻结，暂不可提现');
            return;
        }
        setWithdrawSubmitting(true);
        try {
            await sellerWithdrawApply(toAmount(withdrawText));
            message.success('提现申请已提交，等待审核');
            setWithdrawText('');
            refresh();
            setReloadKey((key) => key + 1);
        } finally {
            setWithdrawSubmitting(false);
        }
    };

    const columns: ProColumns<any>[] = [
        { title: '提现单号', dataIndex: 'withdrawNo', width: 180, render: (_, row) => row.withdrawNo || `#${row.withdrawId}` },
        { title: '金额', dataIndex: 'amount', width: 120, render: (_, row) => moneyText(row.amount) },
        {
            title: '状态',
            dataIndex: 'status',
            width: 100,
            render: (_, row) => {
                const meta = statusMeta(WITHDRAW_STATUS, row.status);
                return <Tag color={meta.color}>{meta.text}</Tag>;
            },
        },
        { title: '申请时间', dataIndex: 'createTime', width: 140, render: (_, row) => formatTime(row.createTime) },
    ];

    const canShowKycForm = kycStatus === 'unverified' || kycStatus === 'rejected';

    return (
        <PageContainer header={{ title: '卖家中心', breadcrumb: {} }}>
            <Card loading={loading} style={{ marginBottom: 16 }}>
                <Space align="center" style={{ marginBottom: 12 }}>
                    <Title level={5} style={{ margin: 0 }}>
                        实名与结算
                    </Title>
                    <Tag color={kycMeta.color}>{kycMeta.text}</Tag>
                </Space>

                {kycStatus === 'pending' && (
                    <Alert type="info" showIcon message="实名审核中，审核通过后可结算与提现" style={{ marginBottom: 12 }} />
                )}
                {kycStatus === 'rejected' && (
                    <Alert type="warning" showIcon message="实名审核未通过，请核对资料后重新提交" style={{ marginBottom: 12 }} />
                )}

                {canShowKycForm && (
                    <Form form={kycForm} layout="vertical" onFinish={handleKycSubmit} style={{ maxWidth: 480 }}>
                        <Form.Item label="实名姓名" name="accountName">
                            <Input placeholder="请输入真实姓名" maxLength={32} />
                        </Form.Item>
                        <Form.Item label="身份证号" name="idCardNo">
                            <Input placeholder="请输入身份证号" maxLength={18} />
                        </Form.Item>
                        <Form.Item label="银行卡号" name="bankCardNo">
                            <Input placeholder="请输入银行卡号" maxLength={19} />
                        </Form.Item>
                        <Form.Item label="开户行" name="bankName">
                            <Input placeholder="请输入开户银行" maxLength={64} />
                        </Form.Item>
                        <Form.Item>
                            <Button type="primary" htmlType="submit" loading={kycSubmitting} disabled={!kycReady}>
                                提交实名资料
                            </Button>
                            <Text type="secondary" style={{ marginLeft: 12 }}>
                                审核通过后可结算与提现
                            </Text>
                        </Form.Item>
                    </Form>
                )}

                {kycStatus === 'verified' && (
                    <>
                        <Descriptions column={1} size="small">
                            <Descriptions.Item label="实名姓名">{account?.accountName || '-'}</Descriptions.Item>
                            <Descriptions.Item label="身份证号">{account?.idCardNo || '-'}</Descriptions.Item>
                            <Descriptions.Item label="银行卡号">{account?.bankCardNo || '-'}</Descriptions.Item>
                            <Descriptions.Item label="开户行">{account?.bankName || '-'}</Descriptions.Item>
                            <Descriptions.Item label="可提现余额">{moneyText(balance)}</Descriptions.Item>
                        </Descriptions>
                        {frozen && (
                            <Alert
                                type="warning"
                                showIcon
                                message="账户已冻结，暂不可提现，请联系平台处理"
                                style={{ marginTop: 12 }}
                            />
                        )}
                    </>
                )}
            </Card>

            {kycStatus === 'verified' && (
                <>
                    <Card title="申请提现" style={{ marginBottom: 16 }}>
                        <Space.Compact style={{ width: 320 }}>
                            <Input
                                value={withdrawText}
                                onChange={(event) => setWithdrawText(event.target.value)}
                                placeholder={`最多可提现 ${moneyText(balance)}`}
                                disabled={frozen}
                                prefix="¥"
                            />
                            <Button
                                type="primary"
                                onClick={handleWithdraw}
                                loading={withdrawSubmitting}
                                disabled={frozen}
                            >
                                申请提现
                            </Button>
                        </Space.Compact>
                    </Card>

                    <Card title="我的提现记录">
                        <ProTable
                            key={reloadKey}
                            rowKey="withdrawId"
                            search={false}
                            options={false}
                            columns={columns}
                            request={async (params) => {
                                const res: any = await sellerWithdrawPage({
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
                    </Card>
                </>
            )}
        </PageContainer>
    );
};

export default MySettlement;