import RefundTimeline from '@/components/marketplace/RefundTimeline';
import {
    orderDetail,
    refundCancel,
    refundDetail,
    refundMediate,
    refundReturn,
    refundSellerAgree,
    refundSellerConfirm,
    refundSellerReject,
} from '@/services/marketplace/api';
import { history, useModel, useSearchParams } from '@umijs/max';
import { PageContainer } from '@ant-design/pro-components';
import { Alert, Button, Card, Descriptions, Image, Input, Modal, Space, Spin, Tag, Typography, message } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { formatPrice, formatTime, staticUrl } from './utils';

const { Text, Paragraph } = Typography;
const { TextArea } = Input;

const unwrap = (res: any) => res?.data ?? res;

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

const REASON_LABELS: Record<string, string> = {
    not_as_described: '与描述不符',
    damaged: '商品损坏',
    wrong_item: '发错货',
    not_received: '未收到货',
    no_longer_needed: '不想要了',
    other: '其他',
};

const TYPE_LABELS: Record<string, string> = {
    refund_only: '仅退款',
    return_refund: '退货退款',
};

/** 评价图片 JSON 数组安全解析 */
function parseImages(raw: unknown): string[] {
    if (!raw) return [];
    try {
        const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw;
        return Array.isArray(parsed) ? parsed : [];
    } catch {
        return [];
    }
}

const RefundDetail: React.FC = () => {
    const [params] = useSearchParams();
    const refundId = params.get('refundId');
    const { initialState } = useModel('@@initialState');
    const myId = Number((initialState as any)?.currentUser?.user?.userId);

    const [loading, setLoading] = useState(true);
    const [data, setData] = useState<any>(null);
    const [order, setOrder] = useState<any>(null);
    const [submitting, setSubmitting] = useState(false);

    // 卖家同意（退货退款时需要填写退货地址）
    const [agreeOpen, setAgreeOpen] = useState(false);
    const [returnAddress, setReturnAddress] = useState('');
    // 卖家拒绝理由
    const [rejectOpen, setRejectOpen] = useState(false);
    const [rejectReason, setRejectReason] = useState('');
    // 买家填写退货物流
    const [returnOpen, setReturnOpen] = useState(false);
    const [returnCompany, setReturnCompany] = useState('');
    const [returnNo, setReturnNo] = useState('');

    const load = useCallback(async () => {
        if (!refundId) {
            setLoading(false);
            return;
        }
        setLoading(true);
        try {
            const res: any = unwrap(await refundDetail(refundId));
            setData(res);
            if (res?.refund?.orderId) {
                const orderRes: any = unwrap(await orderDetail(res.refund.orderId));
                setOrder(orderRes);
            }
        } catch (e: any) {
            message.error(e?.response?.data?.msg || '加载失败，请稍后重试');
        } finally {
            setLoading(false);
        }
    }, [refundId]);

    useEffect(() => {
        load();
    }, [load]);

    if (!refundId) {
        return (
            <PageContainer header={{ title: '退款详情', breadcrumb: {} }}>
                <Alert type="warning" showIcon message="缺少退款单参数" />
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

    const refund = data?.refund;
    if (!refund) {
        return (
            <PageContainer header={{ title: '退款详情', breadcrumb: {} }}>
                <Alert type="warning" showIcon message="退款不存在或无权查看" />
            </PageContainer>
        );
    }

    const status = STATUS_MAP[refund.status] || { text: refund.status, color: 'default' };
    const isBuyer = !!myId && Number(refund.buyerId) === myId;
    const isSeller = !!myId && Number(refund.sellerId) === myId;
    const isReturn = refund.type === 'return_refund';

    const run = async (fn: () => Promise<any>, successText: string, after?: () => void) => {
        setSubmitting(true);
        try {
            await fn();
            message.success(successText);
            after?.();
            await load();
        } catch (e: any) {
            message.error(e?.response?.data?.msg || '操作失败，请稍后重试');
        } finally {
            setSubmitting(false);
        }
    };

    const onCancel = () =>
        Modal.confirm({
            title: '撤销退款申请',
            content: '撤销后订单将恢复原状态，确定撤销吗？',
            onOk: () => run(() => refundCancel(refund.refundId), '已撤销'),
        });

    const onMediate = () =>
        Modal.confirm({
            title: '申请平台介入',
            content: '平台将重新审核这笔退款，确定申请介入吗？',
            onOk: () => run(() => refundMediate(refund.refundId), '已申请平台介入'),
        });

    const onConfirmReceipt = () =>
        Modal.confirm({
            title: '确认收到退货',
            content: '确认后将立即退款给买家，请确保已收到退货。',
            onOk: () => run(() => refundSellerConfirm(refund.refundId), '已确认收货并发起退款'),
        });

    const submitAgree = () => {
        if (isReturn && !returnAddress.trim()) {
            message.warning('请填写退货地址');
            return;
        }
        run(
            () => refundSellerAgree({ refundId: refund.refundId, returnAddress: returnAddress.trim() || undefined }),
            '已同意退款',
            () => setAgreeOpen(false),
        );
    };

    const submitReject = () => {
        if (!rejectReason.trim()) {
            message.warning('请填写拒绝理由');
            return;
        }
        run(
            () => refundSellerReject({ refundId: refund.refundId, reason: rejectReason.trim() }),
            '已拒绝退款',
            () => setRejectOpen(false),
        );
    };

    const submitReturn = () => {
        if (!returnCompany.trim() || !returnNo.trim()) {
            message.warning('请填写物流公司与单号');
            return;
        }
        run(
            () =>
                refundReturn({
                    refundId: refund.refundId,
                    company: returnCompany.trim(),
                    returnNo: returnNo.trim(),
                }),
            '退货物流已提交',
            () => setReturnOpen(false),
        );
    };

    const images = parseImages(refund.evidenceImages);

    return (
        <PageContainer header={{ title: '退款详情', breadcrumb: {} }}>
            <Card
                title={
                    <Space>
                        <span>退款单 #{refund.refundId}</span>
                        <Tag color={status.color}>{status.text}</Tag>
                    </Space>
                }
                extra={<a onClick={() => history.push(`/trade/order/detail?orderId=${refund.orderId}`)}>查看订单</a>}
                style={{ marginBottom: 16 }}
            >
                <Descriptions column={1} size="small">
                    <Descriptions.Item label="订单号">{order?.orderNo || '-'}</Descriptions.Item>
                    <Descriptions.Item label="退款类型">{TYPE_LABELS[refund.type] || refund.type}</Descriptions.Item>
                    <Descriptions.Item label="退款原因">
                        {REASON_LABELS[refund.reasonCode] || refund.reasonCode}
                    </Descriptions.Item>
                    <Descriptions.Item label="退款金额">{formatPrice(refund.amount)}</Descriptions.Item>
                    <Descriptions.Item label="申请时间">{formatTime(refund.createTime)}</Descriptions.Item>
                    {refund.description && <Descriptions.Item label="问题说明">{refund.description}</Descriptions.Item>}
                    {isReturn && refund.returnAddressSnapshot && (
                        <Descriptions.Item label="退货地址">{refund.returnAddressSnapshot}</Descriptions.Item>
                    )}
                    {refund.returnCompany && (
                        <Descriptions.Item label="退货物流">
                            {refund.returnCompany} {refund.returnNo}
                        </Descriptions.Item>
                    )}
                </Descriptions>

                {images.length > 0 && (
                    <div style={{ marginTop: 12 }}>
                        <Text type="secondary">凭证图片</Text>
                        <div style={{ marginTop: 8 }}>
                            <Image.PreviewGroup>
                                <Space>
                                    {images.map((path, index) => (
                                        <Image key={index} width={72} height={72} src={staticUrl(path)} />
                                    ))}
                                </Space>
                            </Image.PreviewGroup>
                        </div>
                    </div>
                )}

                <Paragraph type="secondary" style={{ marginTop: 12, marginBottom: 0 }}>
                    退款将原路退回至买家支付账户。
                </Paragraph>
            </Card>

            <Card title="处理记录" style={{ marginBottom: 16 }}>
                <RefundTimeline logs={data?.logs || []} />
            </Card>

            <Card title="操作">
                <Space wrap>
                    {isBuyer && refund.status === 'seller_rejected' && (
                        <Button type="primary" loading={submitting} onClick={onMediate}>
                            申请平台介入
                        </Button>
                    )}
                    {isBuyer && isReturn && refund.status === 'seller_agreed' && (
                        <Button type="primary" onClick={() => setReturnOpen(true)}>
                            填写退货单号
                        </Button>
                    )}
                    {isBuyer && ['pending_seller', 'seller_agreed', 'seller_rejected'].includes(refund.status) && (
                        <Button danger loading={submitting} onClick={onCancel}>
                            撤销申请
                        </Button>
                    )}

                    {isSeller && refund.status === 'pending_seller' && (
                        <>
                            <Button type="primary" onClick={() => setAgreeOpen(true)}>
                                同意退款
                            </Button>
                            <Button danger onClick={() => setRejectOpen(true)}>
                                拒绝退款
                            </Button>
                        </>
                    )}
                    {isSeller && refund.status === 'buyer_returned' && (
                        <Button type="primary" loading={submitting} onClick={onConfirmReceipt}>
                            确认收到退货
                        </Button>
                    )}

                    {!isBuyer && !isSeller && <Text type="secondary">仅退款双方可操作</Text>}
                </Space>
            </Card>

            <Modal
                title="同意退款"
                open={agreeOpen}
                confirmLoading={submitting}
                onOk={submitAgree}
                onCancel={() => setAgreeOpen(false)}
                okText="确认同意"
            >
                {isReturn ? (
                    <>
                        <Paragraph type="secondary">请填写退货地址，买家将按此地址寄回商品。</Paragraph>
                        <TextArea
                            rows={3}
                            maxLength={200}
                            value={returnAddress}
                            onChange={(e) => setReturnAddress(e.target.value)}
                            placeholder="退货地址（联系人 / 电话 / 详细地址）"
                        />
                    </>
                ) : (
                    <Paragraph>仅退款申请将直接退款给买家，确认同意吗？</Paragraph>
                )}
            </Modal>

            <Modal
                title="拒绝退款"
                open={rejectOpen}
                confirmLoading={submitting}
                onOk={submitReject}
                onCancel={() => setRejectOpen(false)}
                okText="确认拒绝"
                okButtonProps={{ danger: true }}
            >
                <TextArea
                    rows={3}
                    maxLength={200}
                    value={rejectReason}
                    onChange={(e) => setRejectReason(e.target.value)}
                    placeholder="请填写拒绝理由（买家可见）"
                />
            </Modal>

            <Modal
                title="填写退货单号"
                open={returnOpen}
                confirmLoading={submitting}
                onOk={submitReturn}
                onCancel={() => setReturnOpen(false)}
                okText="提交"
            >
                <Space direction="vertical" style={{ width: '100%' }}>
                    <Input
                        value={returnCompany}
                        onChange={(e) => setReturnCompany(e.target.value)}
                        placeholder="物流公司（如 顺丰）"
                        maxLength={50}
                    />
                    <Input
                        value={returnNo}
                        onChange={(e) => setReturnNo(e.target.value)}
                        placeholder="物流单号"
                        maxLength={50}
                    />
                </Space>
            </Modal>
        </PageContainer>
    );
};

export default RefundDetail;