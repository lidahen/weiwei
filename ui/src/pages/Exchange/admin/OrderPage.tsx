import { adminOrderForceCancel, adminOrderForceComplete, adminOrderPage } from '@/services/exchange/api';
import { DELIVERY_MODE_MAP, ORDER_STATUS_MAP, formatDateTime } from '../utils';
import type { ProColumns } from '@ant-design/pro-components';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import { Input, message, Modal, Space, Tag, Tooltip, Typography } from 'antd';
import React, { useRef, useState } from 'react';

/**
 * 交换管理 - 订单管理 / 纠纷裁决
 *
 * 后端 /admin/exchange/order 已按 disputed 置顶
 * （ExchangeOrderMapper.selectAdminPage：ORDER BY CASE WHEN status = 'disputed'）；
 * 仅 disputed 订单可裁决：按现状完成（disputed → completed）/
 * 取消并解锁物品（disputed → cancelled），裁决备注必填留档 order_log。
 *
 * 列表行返回的是 twelve_exchange_order 原始行（双物品/双方用户为 ID 引用，
 * 无联表昵称/标题），故按 ID 展示；订单详情接口仅参与方可查看
 * （ExchangeOrderServiceImpl#detail），管理端不放 C 端详情跳转。
 */
type RulingAction = 'complete' | 'cancel';

const RULING_META: Record<RulingAction, { title: string; okText: string; okColor: string; desc: string }> = {
    complete: {
        title: '裁决：按现状完成',
        okText: '确认按现状完成',
        okColor: '#722ED1',
        desc: '订单将按当前交付现状置为「已完成」，双方物品记为已换出（disputed → completed）。',
    },
    cancel: {
        title: '裁决：取消并解锁物品',
        okText: '确认取消并解锁',
        okColor: '#F87171',
        desc: '订单将取消，双方物品解锁重新上架可被发起意向（disputed → cancelled）。',
    },
};

const OrderPage: React.FC = () => {
    const actionRef = useRef<any>();
    const [status, setStatus] = useState<string>('all');
    const [keyword, setKeyword] = useState<string>('');
    const [rulingOpen, setRulingOpen] = useState(false);
    const [rulingAction, setRulingAction] = useState<RulingAction>('complete');
    const [currentRow, setCurrentRow] = useState<any>(null);
    const [remark, setRemark] = useState('');

    const openRuling = (row: any, action: RulingAction) => {
        setCurrentRow(row);
        setRulingAction(action);
        setRemark('');
        setRulingOpen(true);
    };

    const sideCol = (label: string, itemIdKey: string, ownerIdKey: string, titleKey: string, nameKey: string): ProColumns<any> => ({
        title: label,
        width: 150,
        search: false,
        ellipsis: true,
        render: (_, r) => (
            <Space direction="vertical" size={0}>
                <span>{r[titleKey] || `物品 #${r[itemIdKey]}`}</span>
                <span style={{ color: '#999', fontSize: 12 }}>{r[nameKey] || `用户 #${r[ownerIdKey]}`}</span>
            </Space>
        ),
    });

    const columns: ProColumns<any>[] = [
        {
            title: 'ID',
            dataIndex: 'id',
            width: 80,
            fixed: 'left',
        },
        {
            title: '订单号',
            dataIndex: 'orderNo',
            width: 190,
            ellipsis: true,
            render: (_, r) => (
                <Typography.Text copyable={{ text: r.orderNo }} ellipsis style={{ maxWidth: 170 }}>
                    {r.orderNo}
                </Typography.Text>
            ),
        },
        sideCol('甲方', 'itemAId', 'ownerAId', 'itemATitle', 'ownerAName'),
        sideCol('乙方', 'itemBId', 'ownerBId', 'itemBTitle', 'ownerBName'),
        {
            title: '交付方式',
            dataIndex: 'deliveryMode',
            search: false,
            width: 90,
            render: (_, r) => DELIVERY_MODE_MAP[r.deliveryMode] || r.deliveryMode || '-',
        },
        {
            title: '交付进度',
            width: 150,
            search: false,
            render: (_, r) => (
                <Space size={4}>
                    <Tag color={r.aDeliveredTime ? 'cyan' : 'default'}>A{r.aDeliveredTime ? '已交付' : '未交付'}</Tag>
                    <Tag color={r.bDeliveredTime ? 'cyan' : 'default'}>B{r.bDeliveredTime ? '已交付' : '未交付'}</Tag>
                </Space>
            ),
        },
        {
            title: '状态',
            dataIndex: 'status',
            width: 90,
            render: (_, r) => (
                <Tag color={ORDER_STATUS_MAP[r.status]?.color}>
                    {ORDER_STATUS_MAP[r.status]?.text || r.status}
                </Tag>
            ),
        },
        {
            title: '纠纷信息',
            width: 180,
            search: false,
            ellipsis: true,
            render: (_, r) =>
                r.disputeReason || r.disputeResult ? (
                    <Space direction="vertical" size={0}>
                        {r.disputeResult && <Tag color="purple">已裁决：{r.disputeResult}</Tag>}
                        {r.disputeReason && (
                            <Tooltip title={r.disputeReason}>
                                <Typography.Text ellipsis style={{ maxWidth: 170 }}>
                                    {r.disputeReason}
                                </Typography.Text>
                            </Tooltip>
                        )}
                    </Space>
                ) : (
                    <span style={{ color: '#c0c4cc' }}>—</span>
                ),
        },
        {
            title: '创建',
            dataIndex: 'createTime',
            search: false,
            width: 150,
            render: (_, r) => formatDateTime(r.createTime),
        },
        {
            title: '操作',
            valueType: 'option',
            width: 200,
            fixed: 'right',
            render: (_, row) =>
                row.status === 'disputed' ? (
                    <Space>
                        <a style={{ color: '#722ED1' }} onClick={() => openRuling(row, 'complete')}>
                            按现状完成
                        </a>
                        <a style={{ color: '#F87171' }} onClick={() => openRuling(row, 'cancel')}>
                            取消并解锁
                        </a>
                    </Space>
                ) : (
                    <span style={{ color: '#c0c4cc' }}>—</span>
                ),
        },
    ];

    const meta = RULING_META[rulingAction];

    return (
        <PageContainer header={{ title: '订单管理', breadcrumb: {} }}>
            <Space style={{ marginBottom: 16 }}>
                <Input.Search
                    allowClear
                    placeholder="搜索订单号"
                    style={{ width: 280 }}
                    onSearch={(v) => setKeyword(v.trim())}
                />
            </Space>
            <ProTable
                actionRef={actionRef}
                rowKey="id"
                columns={columns}
                search={false}
                scroll={{ x: 1400 }}
                pagination={{ showSizeChanger: true, defaultPageSize: 20 }}
                request={async (p) => {
                    const r: any = await adminOrderPage({
                        pageNum: p.current,
                        pageSize: p.pageSize,
                        status: status === 'all' ? undefined : status,
                        keyword: keyword || undefined,
                    });
                    const payload = r?.data ?? r;
                    return {
                        data: payload?.records || [],
                        success: true,
                        total: payload?.total || 0,
                    };
                }}
                params={{ status, keyword }}
                toolbar={{
                    menu: {
                        type: 'tab',
                        activeKey: status,
                        onChange: (k) => setStatus(k as string),
                        items: [
                            { key: 'all', label: '全部' },
                            { key: 'disputed', label: '纠纷中' },
                            { key: 'created', label: '待交付' },
                            { key: 'delivering', label: '交付中' },
                            { key: 'pending_confirm', label: '待确认' },
                            { key: 'completed', label: '已完成' },
                            { key: 'cancelled', label: '已取消' },
                        ],
                    },
                }}
            />
            <Modal
                title={meta?.title}
                open={rulingOpen}
                okText={meta?.okText}
                okButtonProps={{ danger: rulingAction === 'cancel' }}
                onCancel={() => setRulingOpen(false)}
                onOk={async () => {
                    if (!remark.trim()) {
                        message.warning('请填写裁决备注');
                        return;
                    }
                    if (rulingAction === 'complete') {
                        await adminOrderForceComplete(currentRow.id, remark.trim());
                    } else {
                        await adminOrderForceCancel(currentRow.id, remark.trim());
                    }
                    message.success('裁决已执行');
                    setRulingOpen(false);
                    actionRef.current?.reload();
                }}
            >
                <Space direction="vertical" size={8} style={{ width: '100%' }}>
                    <Typography.Text type="secondary">{meta?.desc}</Typography.Text>
                    <Typography.Text>
                        订单号：{currentRow?.orderNo}（#{currentRow?.id}）
                    </Typography.Text>
                    <Input.TextArea
                        rows={4}
                        value={remark}
                        onChange={(e) => setRemark(e.target.value)}
                        placeholder="裁决备注（必填，将通知双方并留档订单日志）"
                    />
                </Space>
            </Modal>
        </PageContainer>
    );
};

export default OrderPage;
