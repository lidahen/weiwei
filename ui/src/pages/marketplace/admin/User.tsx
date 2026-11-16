import { adminUserBan, adminUserPage } from '@/services/marketplace/api';
import type { ProColumns } from '@ant-design/pro-components';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import { Avatar, Popconfirm, Space, Tag, message } from 'antd';
import React, { useRef } from 'react';

const userTypeMap: Record<string, { text: string; color: string }> = {
    '00': { text: '运营', color: 'blue' },
    '01': { text: '客服', color: 'red' },
    '02': { text: '普通用户', color: 'orange' },
};

const User: React.FC = () => {
    const actionRef = useRef<any>();
    const columns: ProColumns<any>[] = [
        { title: 'ID', dataIndex: 'userId', width: 80 },
        {
            title: '头像',
            dataIndex: 'avatar',
            search: false,
            width: 60,
            render: (_, r) => <Avatar src={r.avatar} />,
        },
        { title: '昵称', dataIndex: 'nickName' },
        { title: '账号', dataIndex: 'username', search: false },
        { title: '手机', dataIndex: 'phonenumber', search: false },
        {
            title: '角色',
            dataIndex: 'userType',
            search: false,
            render: (_, r) => (
                <Tag color={userTypeMap[r.userType]?.color}>
                    {userTypeMap[r.userType]?.text || r.userType}
                </Tag>
            ),
        },
        {
            title: '状态',
            dataIndex: 'status',
            search: false,
            render: (_, r) =>
                r.status === 1 ? <Tag color="red">已封禁</Tag> : <Tag color="green">正常</Tag>,
        },
        { title: '注册时间', dataIndex: 'createTime', search: false, valueType: 'dateTime' },
        {
            title: '操作',
            valueType: 'option',
            render: (_, row) => (
                <Space>
                    <a target="_blank" href={`/user/profile/${row.userId}`} rel="noreferrer">
                        查看主页
                    </a>
                    {row.status === 0 ? (
                        <Popconfirm
                            title={`确认封禁 ${row.nickName}?`}
                            onConfirm={async () => {
                                await adminUserBan({ userId: row.userId, status: 1 });
                                message.success('已封禁');
                                actionRef.current?.reload();
                            }}
                        >
                            <a style={{ color: '#F87171' }}>封禁</a>
                        </Popconfirm>
                    ) : (
                        <Popconfirm
                            title={`解封 ${row.nickName}?`}
                            onConfirm={async () => {
                                await adminUserBan({ userId: row.userId, status: 0 });
                                message.success('已解封');
                                actionRef.current?.reload();
                            }}
                        >
                            <a>解封</a>
                        </Popconfirm>
                    )}
                </Space>
            ),
        },
    ];

    return (
        <PageContainer header={{ title: '用户管理', breadcrumb: {} }}>
            <ProTable
                actionRef={actionRef}
                rowKey="userId"
                columns={columns}
                request={async (params) => {
                    const r: any = await adminUserPage(params);
                    return {
                        data: r?.records || r?.data?.records || [],
                        success: true,
                        total: r?.total || r?.data?.total || 0,
                    };
                }}
            />
        </PageContainer>
    );
};

export default User;
