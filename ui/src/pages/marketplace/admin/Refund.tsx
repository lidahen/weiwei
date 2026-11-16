import RefundTimeline from '@/components/marketplace/RefundTimeline';
import { adminRefundDetail, adminRefundJudge, adminRefundPage, adminRefundResolve } from '@/services/marketplace/api';
import { PageContainer, ProColumns, ProTable } from '@ant-design/pro-components';
import {
    Button,
    Descriptions,
    Drawer,
    Image,
    Input,
    InputNumber,
    Modal,
    Radio,
    Space,
    Tag,
    Typography,
    message,
} from 'antd';
import React, { useState } from 'react';
import { formatPrice, formatTime, staticUrl } from '../utils';
import { refundSla } from './refundSla';

const { Text } = Typography;
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

const TYPE_LABELS: Record<string, string> = {
    refund_only: '仅退款',
    return_refund: '退货退款',
};

const REASON_LABELS: Record<string, string> = {
    not_as_described: '与描述不符',
    damaged: '商品损坏',
    wrong_item: '发错货',
    not_received: '未收到货',
    no_longer_needed: '不想要了',
    other: '其他',
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

const STATUS_TABS = [
    { key: 'all', label: '全部' },
    { key: 'pending_seller', label: '待卖家处理' },
    { key: 'seller_agreed', label: '待买家寄回' },
    { key: 'buyer_returned', label: '待卖家收货' },
    { key: 'refunding', label: '退款中' },
    { key: 'mediation', label: '平台介入' },
    { key: 'overdue', label: '已超时' },
];

const Refund: React.FC = () => {
    const [status, setStatus] = useState('all');
    const [detail, setDetail] = useState<any>(null);
    const [drawerOpen, setDrawerOpen] = useState(false);
    const [detailLoading, setDetailLoading] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [reloadKey, setReloadKey] = useState(0);

    // 裁决
    const [judgeOpen, setJudgeOpen] = useState(false);
    const [decision, setDecision] = useState<'buyer' | 'seller' | 'partial'>('buyer');
    const [judgeAmount, setJudgeAmount] = useState<number | null>(null);
    const [judgeReason, setJudgeReason] = useState('');
    // 手工收口
    const [resolveOpen, setResolveOpen] = useState(false);
    const [resolveResult, setResolveResult] = useState<'success' | 'fail'>('success');
    const [resolveNote, setResolveNote] = useState('');

    const refund = detail?.refund;
    const order = detail?.order;

    const openDetail = async (refundId: number) => {
        setDrawerOpen(true);
        setDetailLoading(true);
        try {
            setDetail(unwrap(await adminRefundDetail(refundId)));
        } catch (e: any) {
            message.error(e?.response?.data?.msg || '加载失败，请稍后重试');
            setDrawerOpen(false);
        } finally {
            setDetailLoading(false);
        }
    };

    const refreshDetail = async () => {
        if (!refund?.refundId) return;
        setDetail(unwrap(await adminRefundDetail(refund.refundId)));
        setReloadKey((k) => k + 1);
    };

    const submitJudge = async () => {
        if (!refund?.refundId) return;
        if (decision === 'partial' && (judgeAmount === null || judgeAmount <= 0)) {
            message.warning('部分退款需要填写大于 0 的金额');
            return;
        }
        setSubmitting(true);
        try {
            await adminRefundJudge({
                refundId: refund.refundId,
                decision,
                amount: decision === 'partial' ? judgeAmount : undefined,
                reason: judgeReason.trim() || undefined,
            });
            message.success('裁决已提交');
            setJudgeOpen(false);
            await refreshDetail();
        } catch (e: any) {
            message.error(e?.response?.data?.msg || '操作失败，请稍后重试');
        } finally {
            setSubmitting(false);
        }
    };

    const submitResolve = async () => {
        if (!refund?.refundId) return;
        if (!resolveNote.trim()) {
            message.warning('请填写人工处理说明');
            return;
        }
        setSubmitting(true);
        try {
            await adminRefundResolve({
                refundId: refund.refundId,
                result: resolveResult,
                note: resolveNote.trim(),
            });
            message.success('收口已提交');
            setResolveOpen(false);
            await refreshDetail();
        } catch (e: any) {
            message.error(e?.response?.data?.msg || '操作失败，请稍后重试');
        } finally {
            setSubmitting(false);
        }
    };

    const columns: ProColumns<any>[] = [
        { title: '退款单号', dataIndex: 'refundId', width: 100, render: (_, row) => `#${row.refundId}` },
        {
            title: '订单号',
            dataIndex: 'orderId',
            width: 100,
            render: (_, row) => <a onClick={() => openDetail(row.refundId)}>{row.orderId}</a>,
        },
        {
            title: '类型',
            dataIndex: 'type',
            width: 90,
            render: (_, row) => TYPE_LABELS[row.type] || row.type,
        },
        {
            title: '退款原因',
            dataIndex: 'reasonCode',
            width: 110,
            render: (_, row) => REASON_LABELS[row.reasonCode] || row.reasonCode,
        },
        { title: '退款金额', dataIndex: 'amount', width: 110, render: (_, row) => formatPrice(row.amount) },
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
            title: 'SLA',
            key: 'sla',
            width: 110,
            render: (_, row) => {
                const sla = refundSla(row);
                return sla ? <Tag color={sla.color}>{sla.text}</Tag> : '-';
            },
        },
        {
            title: '操作',
            valueType: 'option',
            width: 90,
            render: (_, row) => <a onClick={() => openDetail(row.refundId)}>处理</a>,
        },
    ];

    const images = parseImages(refund?.evidenceImages);

    return (
        <PageContainer header={{ title: '退款管理', breadcrumb: {} }}>
            <ProTable
                key={reloadKey}
                rowKey="refundId"
                search={false}
                options={false}
                columns={columns}
                params={{ status }}
                toolbar={{
                    menu: {
                        type: 'tab',
                        activeKey: status,
                        items: STATUS_TABS.map((t) => ({ key: t.key, label: t.label })),
                        onChange: (key) => setStatus(String(key)),
                    },
                }}
                request={async (params) => {
                    const res: any = await adminRefundPage({
                        status: status === 'all' || status === 'overdue' ? undefined : status,
                        overdue: status === 'overdue' ? true : undefined,
                        current: params.current,
                        pageSize: params.pageSize,
                    });
                    const payload = unwrap(res);
                    return { data: payload?.records || [], total: payload?.total || 0, success: true };
                }}
                pagination={{ pageSize: 10, showSizeChanger: false }}
            />

            <Drawer
                title={refund ? `退款单 #${refund.refundId}` : '退款详情'}
                width={560}
                open={drawerOpen}
                onClose={() => setDrawerOpen(false)}
                loading={detailLoading}
                extra={
                    <Space>
                        {refund?.status === 'mediation' && (
                            <Button type="primary" onClick={() => setJudgeOpen(true)}>
                                裁决
                            </Button>
                        )}
                        {refund?.status === 'refunding' && (
                            <Button type="primary" danger onClick={() => setResolveOpen(true)}>
                                手工收口
                            </Button>
                        )}
                    </Space>
                }
            >
                {refund && (
                    <Space direction="vertical" size={16} style={{ width: '100%' }}>
                        <Descriptions column={1} size="small" bordered>
                            <Descriptions.Item label="状态">
                                <Tag color={STATUS_MAP[refund.status]?.color}>
                                    {STATUS_MAP[refund.status]?.text || refund.status}
                                </Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label="订单号">{order?.orderNo || refund.orderId}</Descriptions.Item>
                            <Descriptions.Item label="订单状态">{order?.status || '-'}</Descriptions.Item>
                            <Descriptions.Item label="类型">{TYPE_LABELS[refund.type] || refund.type}</Descriptions.Item>
                            <Descriptions.Item label="原因">
                                {REASON_LABELS[refund.reasonCode] || refund.reasonCode}
                            </Descriptions.Item>
                            <Descriptions.Item label="金额">{formatPrice(refund.amount)}</Descriptions.Item>
                            {refund.description && (
                                <Descriptions.Item label="问题说明">{refund.description}</Descriptions.Item>
                            )}
                            {refund.returnAddressSnapshot && (
                                <Descriptions.Item label="退货地址">
                                    {refund.returnAddressSnapshot}
                                </Descriptions.Item>
                            )}
                            {refund.returnCompany && (
                                <Descriptions.Item label="退货物流">
                                    {refund.returnCompany} {refund.returnNo}
                                </Descriptions.Item>
                            )}
                            <Descriptions.Item label="申请时间">
                                {formatTime(refund.createTime)}
                            </Descriptions.Item>
                        </Descriptions>

                        {images.length > 0 && (
                            <div>
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

                        <div>
                            <Text type="secondary">处理记录</Text>
                            <div style={{ marginTop: 8 }}>
                                <RefundTimeline logs={detail?.logs || []} />
                            </div>
                        </div>
                    </Space>
                )}
            </Drawer>

            <Modal
                title="裁决退款"
                open={judgeOpen}
                confirmLoading={submitting}
                onOk={submitJudge}
                onCancel={() => setJudgeOpen(false)}
                okText="提交裁决"
            >
                <Space direction="vertical" size={12} style={{ width: '100%' }}>
                    <Radio.Group
                        value={decision}
                        onChange={(e) => setDecision(e.target.value)}
                        options={[
                            { label: '支持买家（全额退款）', value: 'buyer' },
                            { label: '支持卖家（驳回）', value: 'seller' },
                            { label: '部分退款', value: 'partial' },
                        ]}
                    />
                    {decision === 'partial' && (
                        <InputNumber
                            addonBefore="¥"
                            min={0}
                            precision={2}
                            style={{ width: 200 }}
                            value={judgeAmount}
                            onChange={(v) => setJudgeAmount(v as number | null)}
                            placeholder="部分退款金额"
                        />
                    )}
                    <TextArea
                        rows={3}
                        maxLength={200}
                        value={judgeReason}
                        onChange={(e) => setJudgeReason(e.target.value)}
                        placeholder="裁决理由（双方可见）"
                    />
                </Space>
            </Modal>

            <Modal
                title="手工收口"
                open={resolveOpen}
                confirmLoading={submitting}
                onOk={submitResolve}
                onCancel={() => setResolveOpen(false)}
                okText="确认收口"
            >
                <Space direction="vertical" size={12} style={{ width: '100%' }}>
                    <Radio.Group
                        value={resolveResult}
                        onChange={(e) => setResolveResult(e.target.value)}
                        options={[
                            { label: '线下退款已完成', value: 'success' },
                            { label: '无法退款（关闭）', value: 'fail' },
                        ]}
                    />
                    <TextArea
                        rows={3}
                        maxLength={200}
                        value={resolveNote}
                        onChange={(e) => setResolveNote(e.target.value)}
                        placeholder="人工处理说明（必填，买卖双方可见）"
                    />
                </Space>
            </Modal>
        </PageContainer>
    );
};

export default Refund;