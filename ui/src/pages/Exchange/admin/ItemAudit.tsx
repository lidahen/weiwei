import { adminItemList, adminItemPass, adminItemReject } from '@/services/exchange/api';
import { CONDITION_MAP, ITEM_STATUS_MAP, formatDateTime, formatPrice } from '../utils';
import TWT from '@/setting';
import type { ProColumns } from '@ant-design/pro-components';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import { Image, Input, message, Modal, Space, Tag, Tooltip, Typography } from 'antd';
import React, { useRef, useState } from 'react';

/**
 * 交换管理 - 物品审核
 *
 * 全状态物品列表（后端 auditing 置顶，见 ExchangeItemMapper.selectAdminPage 的
 * ORDER BY CASE WHEN status = 'auditing'）+ 人工通过/驳回（驳回必填原因）。
 *
 * AI 风险标签：后端 /admin/exchange/item 返回的是 twelve_exchange_item 原始行
 * （未联 ai_moderation_log，也无读取该表的管理端接口），故按任务约定只展示状态；
 * 待后端联表返回 aiRiskLevel/aiAction 等字段后，可直接挂载
 * @/components/marketplace/AiAuditTag（商品审核 ProductAudit 同款）。
 */
const ItemAudit: React.FC = () => {
    const actionRef = useRef<any>();
    const [status, setStatus] = useState<string>('all');
    const [keyword, setKeyword] = useState<string>('');
    const [rejectVisible, setRejectVisible] = useState(false);
    const [currentRow, setCurrentRow] = useState<any>(null);
    const [reason, setReason] = useState('');

    const coverSrc = (cover?: string) =>
        cover ? (cover.startsWith('http') ? cover : `${TWT.static}${cover}`) : '';

    const columns: ProColumns<any>[] = [
        {
            title: 'ID',
            dataIndex: 'id',
            width: 80,
            fixed: 'left',
        },
        {
            title: '封面',
            dataIndex: 'cover',
            search: false,
            width: 80,
            render: (_, r) => (
                <Image
                    src={coverSrc(r.cover)}
                    width={60}
                    height={60}
                    style={{ borderRadius: 4, objectFit: 'cover' }}
                />
            ),
        },
        {
            title: '物品',
            dataIndex: 'title',
            ellipsis: true,
            width: 220,
            render: (_, r) => (
                <Space direction="vertical" size={0}>
                    <Typography.Text ellipsis style={{ maxWidth: 200 }}>
                        {r.title}
                    </Typography.Text>
                    <span style={{ color: '#999', fontSize: 12 }}>编号：{r.itemNo}</span>
                </Space>
            ),
        },
        {
            title: '类目',
            dataIndex: 'catL1',
            search: false,
            width: 130,
            render: (_, r) => (
                <Space direction="vertical" size={0}>
                    <span>{r.catL1 || '-'}</span>
                    <span style={{ color: '#999', fontSize: 12 }}>{r.catL2 || ''}</span>
                </Space>
            ),
        },
        {
            title: '成色',
            dataIndex: 'conditionLevel',
            search: false,
            width: 90,
            render: (_, r) => CONDITION_MAP[r.conditionLevel] || r.conditionLevel || '-',
        },
        {
            title: '自估值',
            dataIndex: 'estValue',
            search: false,
            width: 100,
            render: (_, r) => (
                <span style={{ color: '#FF6B35', fontWeight: 600 }}>{formatPrice(r.estValue)}</span>
            ),
        },
        {
            title: '主人',
            dataIndex: 'ownerName',
            width: 110,
            render: (_, r) => (
                <span>
                    {r.ownerName || `用户 ${r.ownerId}`}
                </span>
            ),
        },
        {
            title: '状态',
            dataIndex: 'status',
            width: 90,
            render: (_, r) => (
                <Tag color={ITEM_STATUS_MAP[r.status]?.color}>
                    {ITEM_STATUS_MAP[r.status]?.text || r.status}
                </Tag>
            ),
        },
        {
            title: '驳回原因',
            dataIndex: 'rejectReason',
            search: false,
            width: 150,
            ellipsis: true,
            render: (_, r) =>
                r.rejectReason ? (
                    <Tooltip title={r.rejectReason}>{r.rejectReason}</Tooltip>
                ) : (
                    <span style={{ color: '#c0c4cc' }}>—</span>
                ),
        },
        {
            title: '发布时间',
            dataIndex: 'publishTime',
            search: false,
            width: 150,
            render: (_, r) => formatDateTime(r.publishTime),
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
            width: 160,
            fixed: 'right',
            render: (_, row) => (
                <Space>
                    {row.status === 'auditing' && (
                        <>
                            <a
                                onClick={async () => {
                                    await adminItemPass(row.id);
                                    message.success('已审核通过');
                                    actionRef.current?.reload();
                                }}
                            >
                                通过
                            </a>
                            <a
                                style={{ color: '#F87171' }}
                                onClick={() => {
                                    setCurrentRow(row);
                                    setRejectVisible(true);
                                }}
                            >
                                驳回
                            </a>
                        </>
                    )}
                    <a target="_blank" href={`/exchange/detail/${row.id}`} rel="noreferrer">
                        查看
                    </a>
                </Space>
            ),
        },
    ];

    return (
        <PageContainer header={{ title: '物品审核', breadcrumb: {} }}>
            <Space style={{ marginBottom: 16 }}>
                <Input.Search
                    allowClear
                    placeholder="搜索标题 / 描述"
                    style={{ width: 280 }}
                    onSearch={(v) => setKeyword(v.trim())}
                />
            </Space>
            <ProTable
                actionRef={actionRef}
                rowKey="id"
                columns={columns}
                search={false}
                scroll={{ x: 1300 }}
                pagination={{ showSizeChanger: true, defaultPageSize: 20 }}
                request={async (p) => {
                    const r: any = await adminItemList({
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
                            { key: 'auditing', label: '待审核' },
                            { key: 'onswap', label: '换物中' },
                            { key: 'locked', label: '已锁定' },
                            { key: 'swapped', label: '已换出' },
                            { key: 'offshelf', label: '已下架' },
                            { key: 'rejected', label: '已驳回' },
                        ],
                    },
                }}
            />
            <Modal
                title={`驳回理由（${currentRow?.title || ''}）`}
                open={rejectVisible}
                onCancel={() => setRejectVisible(false)}
                onOk={async () => {
                    if (!reason.trim()) {
                        message.warning('请填写驳回原因');
                        return;
                    }
                    await adminItemReject(currentRow.id, reason.trim());
                    message.success('已驳回');
                    setRejectVisible(false);
                    setReason('');
                    actionRef.current?.reload();
                }}
            >
                <Input.TextArea
                    rows={4}
                    value={reason}
                    onChange={(e) => setReason(e.target.value)}
                    placeholder="如：描述涉嫌违规、图片不实、疑似违禁品等（将通知物品主人）"
                />
            </Modal>
        </PageContainer>
    );
};

export default ItemAudit;
