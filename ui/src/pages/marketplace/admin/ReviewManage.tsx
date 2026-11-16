import { adminReviewHide, adminReviewShow, adminReviewPage } from '@/services/marketplace/api';
import type { ProColumns } from '@ant-design/pro-components';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import { Image, message, Modal, Space, Tag } from 'antd';
import React, { useRef } from 'react';

const statusMap: Record<string, { text: string; color: string }> = {
    normal: { text: '展示中', color: 'green' },
    hidden: { text: '已隐藏', color: 'red' },
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

const ReviewManage: React.FC = () => {
    const actionRef = useRef<any>();

    const toggleStatus = (reviewId: number, next: 'hidden' | 'normal') => {
        Modal.confirm({
            title: next === 'hidden' ? '隐藏该评价？' : '恢复该评价？',
            content:
                next === 'hidden'
                    ? '隐藏后商品页/订单详情将不再展示该评价，可随时恢复。'
                    : '恢复后该评价将重新对外展示。',
            okText: '确认',
            cancelText: '取消',
            onOk: async () => {
                if (next === 'hidden') {
                    await adminReviewHide(reviewId);
                } else {
                    await adminReviewShow(reviewId);
                }
                message.success(next === 'hidden' ? '已隐藏' : '已恢复');
                actionRef.current?.reload();
            },
        });
    };

    const columns: ProColumns<any>[] = [
        { title: 'ID', dataIndex: 'reviewId', width: 70, search: false },
        {
            title: '关键词',
            dataIndex: 'keyword',
            hideInTable: true,
            fieldProps: { placeholder: '评语 / 订单号 / 评价人昵称' },
        },
        { title: '订单号', dataIndex: 'orderNo', search: false, width: 180, ellipsis: true },
        { title: '商品', dataIndex: 'productTitle', search: false, width: 160, ellipsis: true },
        {
            title: '方向',
            dataIndex: 'role',
            width: 90,
            valueEnum: {
                buyer: { text: '买家评卖家' },
                seller: { text: '卖家评买家' },
            },
            render: (_, r) => (
                <Tag color={r.role === 'buyer' ? 'orange' : 'blue'}>
                    {r.role === 'buyer' ? '买家评卖家' : '卖家评买家'}
                </Tag>
            ),
        },
        { title: '评分', dataIndex: 'overallScore', search: false, width: 80 },
        {
            title: '评价人 → 被评价人',
            dataIndex: 'reviewerNickName',
            search: false,
            width: 150,
            ellipsis: true,
            render: (_, r) => `${r.reviewerNickName || '匿名用户'} → ${r.revieweeNickName || '-'}`,
        },
        { title: '内容', dataIndex: 'content', search: false, ellipsis: true },
        {
            title: '图片',
            dataIndex: 'images',
            search: false,
            width: 80,
            render: (_, r) => {
                const imgs = parseImages(r.images);
                return imgs.length > 0 ? (
                    <Image src={imgs[0]} width={60} height={60} style={{ borderRadius: 4 }} />
                ) : (
                    '无图'
                );
            },
        },
        {
            title: '状态',
            dataIndex: 'status',
            width: 90,
            valueEnum: {
                normal: { text: '展示中', status: 'Success' },
                hidden: { text: '已隐藏', status: 'Error' },
            },
            render: (_, r) => (
                <Tag color={statusMap[r.status]?.color}>{statusMap[r.status]?.text || r.status}</Tag>
            ),
        },
        { title: '评价时间', dataIndex: 'createTime', valueType: 'dateTime', search: false, width: 150 },
        {
            title: '操作',
            valueType: 'option',
            width: 100,
            render: (_, row) => (
                <Space>
                    {row.status === 'normal' ? (
                        <a style={{ color: '#F87171' }} onClick={() => toggleStatus(row.reviewId, 'hidden')}>
                            隐藏
                        </a>
                    ) : (
                        <a onClick={() => toggleStatus(row.reviewId, 'normal')}>恢复</a>
                    )}
                </Space>
            ),
        },
    ];

    return (
        <PageContainer header={{ title: '评价管理', breadcrumb: {} }}>
            <ProTable
                actionRef={actionRef}
                rowKey="reviewId"
                columns={columns}
                search={{ labelWidth: 'auto' }}
                request={async (params) => {
                    const r: any = await adminReviewPage(params);
                    const payload = r?.data ?? r ?? {};
                    return {
                        data: payload.records || [],
                        success: true,
                        total: payload.total || 0,
                    };
                }}
            />
        </PageContainer>
    );
};

export default ReviewManage;
