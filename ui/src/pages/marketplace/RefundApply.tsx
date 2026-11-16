import UploadImages from '@/components/marketplace/UploadImages';
import { orderDetail, refundApply, refundByOrder, refundReasons } from '@/services/marketplace/api';
import TWT from '@/setting';
import type { UploadFile } from 'antd/es/upload/interface';
import { history, useSearchParams } from '@umijs/max';
import { Alert, Button, Card, Input, InputNumber, Radio, Space, Spin, Typography, message } from 'antd';
import { PageContainer } from '@ant-design/pro-components';
import React, { useEffect, useMemo, useState } from 'react';
import { formatPrice } from './utils';

const { Text, Paragraph } = Typography;

/** umi request 可能返回 JsonResult 或已解包的 payload，两种都兼容 */
const unwrap = (res: any) => res?.data ?? res;

interface ReasonOption {
    code: string;
    label: string;
    types?: string[];
}

/** 从 UploadFile 中提取已上传的 OSS 相对路径（与 ReportDialog 同逻辑） */
const getImagePath = (f: UploadFile): string | undefined => {
    const { response } = f as any;
    if (response) {
        const data = response.data ?? response.imgUrl;
        if (typeof data === 'string') return data;
    }
    const url = f.url || f.thumbUrl;
    if (url) {
        const path = url.replace(TWT.static, '').split('?')[0];
        return path || undefined;
    }
    return undefined;
};

const RefundApply: React.FC = () => {
    const [params] = useSearchParams();
    const orderId = params.get('orderId');

    const [loading, setLoading] = useState(true);
    const [submitting, setSubmitting] = useState(false);
    const [check, setCheck] = useState<any>(null);
    const [order, setOrder] = useState<any>(null);
    const [reasons, setReasons] = useState<ReasonOption[]>([]);

    const [type, setType] = useState<'refund_only' | 'return_refund'>('refund_only');
    const [reasonCode, setReasonCode] = useState<string>();
    const [amount, setAmount] = useState<number | null>(null);
    const [description, setDescription] = useState('');
    const [fileList, setFileList] = useState<UploadFile[]>([]);

    useEffect(() => {
        if (!orderId) {
            setLoading(false);
            return;
        }
        let cancelled = false;
        setLoading(true);
        Promise.all([
            refundByOrder(orderId).then(unwrap),
            orderDetail(orderId).then(unwrap),
            refundReasons().then(unwrap),
        ])
            .then(([checkRes, orderRes, reasonRes]) => {
                if (cancelled) return;
                setCheck(checkRes);
                setOrder(orderRes);
                setReasons(Array.isArray(reasonRes) ? reasonRes : []);
                const allowed =
                    Array.isArray(checkRes?.allowedTypes) && checkRes.allowedTypes.length
                        ? checkRes.allowedTypes
                        : ['refund_only', 'return_refund'];
                setType((prev) => (allowed.includes(prev) ? prev : (allowed[0] as 'refund_only' | 'return_refund')));
                if (orderRes?.totalAmount !== undefined && orderRes?.totalAmount !== null) {
                    setAmount(Number(orderRes.totalAmount));
                }
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
    }, [orderId]);

    const paidAmount = useMemo(() => {
        const n = Number(order?.totalAmount);
        return Number.isFinite(n) ? n : null;
    }, [order]);

    // 原因按类型过滤：如「未收到货」只适用于仅退款
    const filteredReasons = useMemo(
        () => reasons.filter((r) => !r.types || r.types.includes(type)),
        [reasons, type],
    );

    const overLimit = amount !== null && paidAmount !== null && amount > paidAmount;
    const allowedTypes: string[] =
        Array.isArray(check?.allowedTypes) && check.allowedTypes.length
            ? check.allowedTypes
            : ['refund_only', 'return_refund'];
    const canApply = check?.canApply !== false;
    const canSubmit =
        canApply && !!reasonCode && amount !== null && amount > 0 && !overLimit && !submitting;

    const onTypeChange = (value: 'refund_only' | 'return_refund') => {
        setType(value);
        if (reasonCode && !reasons.some((r) => r.code === reasonCode && (!r.types || r.types.includes(value)))) {
            setReasonCode(undefined);
        }
    };

    const handleSubmit = async () => {
        setSubmitting(true);
        try {
            const evidenceImages = fileList.filter((f) => f.status === 'done').map(getImagePath).filter(Boolean) as string[];
            const res: any = await refundApply({
                orderId: Number(orderId),
                type,
                reasonCode,
                amount,
                description: description.trim() || undefined,
                evidenceImages: evidenceImages.length ? evidenceImages : undefined,
            });
            const refundId = unwrap(res);
            message.success('退款申请已提交');
            history.push(refundId ? `/trade/refund/detail?refundId=${refundId}` : '/user/refunds');
        } catch (e: any) {
            message.error(e?.response?.data?.msg || '提交失败，请稍后重试');
            setSubmitting(false);
        }
    };

    if (!orderId) {
        return (
            <PageContainer header={{ title: '申请退款', breadcrumb: {} }}>
                <Alert type="warning" showIcon message="缺少订单参数，无法申请退款" />
            </PageContainer>
        );
    }

    if (loading) {
        return (
            <div style={{ textAlign: 'center', padding: 100 }}>
                <Spin />
            </div>
        );
    }

    return (
        <PageContainer header={{ title: '申请退款', breadcrumb: {} }}>
            {!canApply && (
                <Alert
                    type="warning"
                    showIcon
                    style={{ marginBottom: 16 }}
                    message={check?.denyReason || '该订单当前不支持申请退款'}
                />
            )}
            {check?.activeRefundId && (
                <Paragraph>
                    该订单已有处理中的退款申请，
                    <a onClick={() => history.push(`/trade/refund/detail?refundId=${check.activeRefundId}`)}>
                        查看退款进度
                    </a>
                </Paragraph>
            )}

            <Card title="订单信息" style={{ marginBottom: 16 }}>
                <Space direction="vertical" size={4}>
                    <Text>订单号：{order?.orderNo || '-'}</Text>
                    <Text>实付金额：{formatPrice(paidAmount)}</Text>
                </Space>
            </Card>

            <Card title="退款申请">
                <Space direction="vertical" size={16} style={{ width: '100%' }}>
                    <div>
                        <Text strong>退款类型</Text>
                        <div style={{ marginTop: 8 }}>
                            <Radio.Group
                                optionType="button"
                                value={type}
                                onChange={(e) => onTypeChange(e.target.value)}
                                options={[
                                    { label: '仅退款', value: 'refund_only' },
                                    { label: '退货退款', value: 'return_refund' },
                                ].filter((o) => allowedTypes.includes(o.value))}
                            />
                        </div>
                    </div>

                    <div>
                        <Text strong>退款原因</Text>
                        <div style={{ marginTop: 8 }}>
                            <Radio.Group
                                value={reasonCode}
                                onChange={(e) => setReasonCode(e.target.value)}
                                options={filteredReasons.map((r) => ({ label: r.label, value: r.code }))}
                            />
                        </div>
                    </div>

                    <div>
                        <Text strong>退款金额</Text>
                        <div style={{ marginTop: 8 }}>
                            <InputNumber
                                value={amount}
                                min={0}
                                precision={2}
                                style={{ width: 200 }}
                                onChange={(v) => setAmount(v as number | null)}
                                addonBefore="¥"
                            />
                            {overLimit && (
                                <Text type="danger" style={{ marginLeft: 12 }}>
                                    退款金额不能超过实付金额
                                </Text>
                            )}
                        </div>
                    </div>

                    <div>
                        <Text strong>问题说明</Text>
                        <Input.TextArea
                            style={{ marginTop: 8 }}
                            value={description}
                            onChange={(e) => setDescription(e.target.value)}
                            maxLength={500}
                            showCount
                            autoSize={{ minRows: 3, maxRows: 6 }}
                            placeholder="补充问题描述（选填）"
                        />
                    </div>

                    <div>
                        <Text strong>凭证图片（最多 3 张）</Text>
                        <div style={{ marginTop: 8 }}>
                            <UploadImages value={fileList} onChange={setFileList} max={3} />
                        </div>
                    </div>

                    {canApply && (
                        <Button type="primary" disabled={!canSubmit} loading={submitting} onClick={handleSubmit}>
                            提交申请
                        </Button>
                    )}
                </Space>
            </Card>
        </PageContainer>
    );
};

export default RefundApply;