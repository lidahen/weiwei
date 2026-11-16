import { adminFeedAuditPage, adminFeedPass, adminFeedReject } from '@/services/marketplace/api';
import AiAuditTag from '@/components/marketplace/AiAuditTag';
import type { ProColumns } from '@ant-design/pro-components';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import { Image, Input, message, Modal, Space, Tag } from 'antd';
import React, { useRef, useState } from 'react';

const statusMap: Record<string, { text: string; color: string }> = {
    pending: { text: '待审核', color: 'orange' },
    approved: { text: '已通过', color: 'green' },
    rejected: { text: '已驳回', color: 'red' },
};

const FeedAudit: React.FC = () => {
    const actionRef = useRef<any>();
    const [rejectVisible, setRejectVisible] = useState(false);
    const [currentRow, setCurrentRow] = useState<any>(null);
    const [reason, setReason] = useState('');

    const columns: ProColumns<any>[] = [
        { title: 'ID', dataIndex: 'feedId', width: 80 },
        {
            title: '图片',
            dataIndex: 'images',
            search: false,
            width: 80,
            render: (_, r) => {
                const imgs: string[] = r.images ? (() => { try { return JSON.parse(r.images); } catch { return []; } })() : [];
                return imgs.length > 0 ? <Image src={imgs[0]} width={60} height={60} style={{ borderRadius: 4 }} /> : '无图';
            },
        },
        { title: '内容', dataIndex: 'content', ellipsis: true },
        { title: '发布者', dataIndex: 'userNickName', search: false },
        {
            title: '状态',
            dataIndex: 'auditStatus',
            valueEnum: {
                pending: { text: '待审核', status: 'Warning' },
                approved: { text: '已通过', status: 'Success' },
                rejected: { text: '已驳回', status: 'Error' },
            },
            render: (_, r) => (
                <Tag color={statusMap[r.auditStatus]?.color}>{statusMap[r.auditStatus]?.text}</Tag>
            ),
        },
        { title: '点赞', dataIndex: 'likeCount', search: false, width: 80 },
        { title: '评论', dataIndex: 'commentCount', search: false, width: 80 },
        { title: '发布时间', dataIndex: 'publishTime', valueType: 'dateTime', search: false },
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
                    {row.auditStatus === 'pending' && (
                        <>
                            <a
                                onClick={async () => {
                                    await adminFeedPass(row.feedId);
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
                    <a target="_blank" href={`/feed/detail/${row.feedId}`} rel="noreferrer">
                        查看
                    </a>
                </Space>
            ),
        },
    ];

    return (
        <PageContainer header={{ title: '动态审核', breadcrumb: {} }}>
            <ProTable
                actionRef={actionRef}
                rowKey="feedId"
                columns={columns}
                request={async (params) => {
                    const r: any = await adminFeedAuditPage(params);
                    // 接口返回 JsonResult<List<Feed>>（全量、前端分页），AI 筛选在客户端执行
                    const d = r?.data ?? r;
                    let list: any[] = Array.isArray(d) ? d : d?.records || [];
                    if (params.aiRisk === 'flagged') {
                        list = list.filter(
                            (x) => x.aiRiskLevel === 'medium' || x.aiRiskLevel === 'high',
                        );
                    } else if (params.aiRisk === 'auto') {
                        list = list.filter(
                            (x) => x.aiAction === 'auto_pass' || x.aiAction === 'auto_reject',
                        );
                    }
                    return {
                        data: list,
                        success: true,
                        total: list.length,
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
                    await adminFeedReject({ feedId: currentRow.feedId, reason });
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
                    placeholder="如:内容违规、涉黄涉暴、广告引流等"
                />
            </Modal>
        </PageContainer>
    );
};

export default FeedAudit;
