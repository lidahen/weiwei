import {
    adminProductAuditPage,
    adminProductPass,
    adminProductReject,
} from '@/services/marketplace/api';
import AiAuditTag from '@/components/marketplace/AiAuditTag';
import TWT from '@/setting';
import type { ProColumns } from '@ant-design/pro-components';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import { Image, Input, message, Modal, Space, Tag } from 'antd';
import React, { useRef, useState } from 'react';

const statusMap: Record<string, { text: string; color: string }> = {
    auditing: { text: '待审核', color: 'orange' },
    onsale: { text: '已上架', color: 'green' },
    rejected: { text: '已驳回', color: 'red' },
    offshelf: { text: '已下架', color: 'default' },
};

const ProductAudit: React.FC = () => {
    const actionRef = useRef<any>();
    const [rejectVisible, setRejectVisible] = useState(false);
    const [currentRow, setCurrentRow] = useState<any>(null);
    const [reason, setReason] = useState('');

    const columns: ProColumns<any>[] = [
        { title: 'ID', dataIndex: 'productId', width: 80 },
        {
            title: '封面',
            dataIndex: 'cover',
            search: false,
            width: 80,
            render: (_, r) => (
                <Image
                    src={r.cover ? `${TWT.static}${r.cover}` : ''}
                    width={60}
                    height={60}
                    style={{ borderRadius: 4 }}
                />
            ),
        },
        { title: '商品标题', dataIndex: 'title' },
        { title: '品类', dataIndex: 'catL1', search: false },
        {
            title: '价格',
            dataIndex: 'price',
            search: false,
            render: (_, r) => <span style={{ color: '#FF6B35', fontWeight: 600 }}>¥{r.price}</span>,
        },
        { title: '卖家', dataIndex: 'sellerNickName', search: false },
        {
            title: '状态',
            dataIndex: 'status',
            valueEnum: { auditing: '待审核', onsale: '已上架', rejected: '已驳回' },
            render: (_, r) => (
                <Tag color={statusMap[r.status]?.color}>{statusMap[r.status]?.text}</Tag>
            ),
        },
        { title: '发布时间', dataIndex: 'publishTime', search: false, valueType: 'dateTime' },
        {
            title: 'AI 初审',
            dataIndex: 'aiRiskLevel',
            search: false,
            width: 110,
            render: (_, r) => <AiAuditTag record={r} />,
        },
        {
            title: 'AI 筛选',
            dataIndex: 'aiRisk',
            valueType: 'select',
            hideInTable: true,
            valueEnum: {
                flagged: { text: '仅看 AI 标疑' },
                auto: { text: 'AI 已自动处理' },
            },
        },
        {
            title: '操作',
            valueType: 'option',
            width: 200,
            render: (_, row) => (
                <Space>
                    {row.status === 'auditing' && (
                        <>
                            <a
                                onClick={async () => {
                                    await adminProductPass(row.productId);
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
                    <a target="_blank" href={`/product/detail/${row.productId}`} rel="noreferrer">
                        查看
                    </a>
                </Space>
            ),
        },
    ];

    return (
        <PageContainer header={{ title: '商品审核', breadcrumb: {} }}>
            <ProTable
                actionRef={actionRef}
                rowKey="productId"
                columns={columns}
                request={async (params) => {
                    const r: any = await adminProductAuditPage(params);
                    return {
                        data: r?.records || r?.data?.records || [],
                        success: true,
                        total: r?.total || r?.data?.total || 0,
                    };
                }}
            />
            <Modal
                title="驳回理由"
                open={rejectVisible}
                onCancel={() => setRejectVisible(false)}
                onOk={async () => {
                    if (!reason) {
                        message.warning('请填写驳回理由');
                        return;
                    }
                    await adminProductReject({ productId: currentRow.productId, reason });
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
                    placeholder="如:商品图片不清晰、价格不合理、涉嫌违规等"
                />
            </Modal>
        </PageContainer>
    );
};

export default ProductAudit;
