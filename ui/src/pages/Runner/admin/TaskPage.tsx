import { adminTaskPage } from '@/services/runner/api';
import {
    PAY_STATUS_MAP,
    TASK_STATUS_MAP,
    TASK_TYPE_MAP,
    formatDateTime,
    formatPrice,
} from '../utils';
import type { ProColumns } from '@ant-design/pro-components';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import { history } from '@umijs/max';
import { Input, Space, Tag } from 'antd';
import React, { useRef, useState } from 'react';

const TaskPage: React.FC = () => {
    const [status, setStatus] = useState<string>('all');
    const [keyword, setKeyword] = useState<string>('');
    const actionRef = useRef<any>();

    const columns: ProColumns<any>[] = [
        {
            title: 'ID',
            dataIndex: 'id',
            width: 80,
            fixed: 'left',
        },
        {
            title: '任务',
            dataIndex: 'title',
            ellipsis: true,
            render: (_, r) => (
                <Space direction="vertical" size={0}>
                    <a onClick={() => history.push(`/runner/task/detail?id=${r.id}`)}>
                        {TASK_TYPE_MAP[r.taskType]?.emoji} {r.title}
                    </a>
                    <span style={{ color: '#999', fontSize: 12 }}>编号：{r.taskNo}</span>
                </Space>
            ),
        },
        {
            title: '发布者',
            dataIndex: 'publisherId',
            width: 100,
        },
        {
            title: '接单者',
            dataIndex: 'assigneeId',
            width: 100,
            render: (_, r) => (r.assigneeId ? r.assigneeId : <Tag>未接单</Tag>),
        },
        {
            title: '类型',
            dataIndex: 'taskType',
            width: 90,
            render: (_, r) => TASK_TYPE_MAP[r.taskType]?.label || '-',
        },
        {
            title: '报酬',
            dataIndex: 'price',
            width: 100,
            render: (_, r) =>
                r.isPaid === 1 ? formatPrice(r.price) : <Tag color="blue">免费任务</Tag>,
        },
        {
            title: '支付',
            dataIndex: 'payStatus',
            width: 90,
            render: (_, r) => (
                <Tag color={PAY_STATUS_MAP[r.payStatus || 'unpaid']?.color}>
                    {PAY_STATUS_MAP[r.payStatus || 'unpaid']?.text}
                </Tag>
            ),
        },
        {
            title: '状态',
            dataIndex: 'status',
            width: 90,
            render: (_, r) => (
                <Tag color={TASK_STATUS_MAP[r.status]?.color}>
                    {TASK_STATUS_MAP[r.status]?.text || r.status}
                </Tag>
            ),
        },
        {
            title: '租户',
            dataIndex: 'tenantId',
            width: 80,
        },
        {
            title: '截止',
            dataIndex: 'deadline',
            width: 150,
            render: (_, r) => formatDateTime(r.deadline),
        },
        {
            title: '创建',
            dataIndex: 'createTime',
            width: 150,
            render: (_, r) => formatDateTime(r.createTime),
        },
    ];

    return (
        <PageContainer header={{ title: '互助任务管理', breadcrumb: {} }}>
            <Space style={{ marginBottom: 16 }}>
                <Input.Search
                    allowClear
                    placeholder="搜索标题 / 任务编号 / 描述"
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
                    const r: any = await adminTaskPage({
                        pageNum: p.current,
                        pageSize: p.pageSize,
                        status: status === 'all' ? undefined : status,
                        keyword: keyword || undefined,
                    });
                    const payload = r?.data ?? r;
                    return {
                        data: payload?.records || payload || [],
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
                            { key: 'pending', label: '待接单' },
                            { key: 'accepted', label: '已接单' },
                            { key: 'running', label: '服务中' },
                            { key: 'pending_confirm', label: '待确认' },
                            { key: 'completed', label: '已完成' },
                            { key: 'cancelled', label: '已取消' },
                        ],
                    },
                }}
            />
        </PageContainer>
    );
};

export default TaskPage;
