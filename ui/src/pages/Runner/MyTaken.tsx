import { hallFinish, hallMyTaken, hallReject, hallStart } from '@/services/runner/api';
import { TASK_STATUS_MAP, TASK_TYPE_MAP, formatDateTime, formatPrice } from './utils';
import type { ProColumns } from '@ant-design/pro-components';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import { history } from '@umijs/max';
import { Button, Empty, Modal, Space, Spin, Tabs, Tag, message } from 'antd';
import { createStyles } from 'antd-style';
import React, { useEffect, useRef, useState } from 'react';
import { useIsMobile } from '@/hooks/useIsMobile';

const useMobileStyles = createStyles(() => ({
    card: {
        background: '#fff',
        borderRadius: 12,
        border: '1px solid #EAEAE8',
        padding: 12,
        marginBottom: 10,
    },
    headRow: {
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: 8,
        marginBottom: 4,
    },
    title: {
        fontWeight: 600,
        fontSize: 15,
        flex: 1,
        minWidth: 0,
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        whiteSpace: 'nowrap',
    },
    price: { color: '#FF6B35', fontWeight: 700, flexShrink: 0 },
    meta: { fontSize: 12, color: '#9A9A9A', marginTop: 4 },
    actions: {
        marginTop: 10,
        display: 'flex',
        justifyContent: 'flex-end',
        gap: 8,
        flexWrap: 'wrap',
    },
}));

const MobileMyTaken: React.FC<{ status: string; reloadFlag: number; onStart: (id: number) => void; onFinish: (id: number) => void; onReject: (id: number) => void }> = ({ status, reloadFlag, onStart, onFinish, onReject }) => {
    const { styles } = useMobileStyles();
    const [list, setList] = useState<any[]>([]);
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        let cancelled = false;
        (async () => {
            setLoading(true);
            try {
                const r: any = await hallMyTaken(status === 'all' ? undefined : { status });
                const data = r?.data ?? r ?? [];
                if (!cancelled) setList(Array.isArray(data) ? data : []);
            } finally {
                if (!cancelled) setLoading(false);
            }
        })();
        return () => {
            cancelled = true;
        };
    }, [status, reloadFlag]);

    if (loading && list.length === 0) return <Spin style={{ display: 'block', margin: '60px auto' }} />;
    if (list.length === 0) return <Empty description="暂无任务" style={{ padding: '40px 0' }} />;

    return (
        <>
            {list.map((r) => (
                <div key={r.id} className={styles.card}>
                    <div className={styles.headRow}>
                        <a
                            className={styles.title}
                            onClick={() => history.push(`/runner/task/detail?id=${r.id}`)}
                        >
                            {TASK_TYPE_MAP[r.taskType]?.emoji} {r.title}
                        </a>
                        <span className={styles.price}>
                            {r.isPaid === 1 ? formatPrice(r.price) : '免费'}
                        </span>
                    </div>
                    <div className={styles.meta}>
                        编号 {r.taskNo} · 接单 {formatDateTime(r.assignTime)}
                    </div>
                    <div style={{ marginTop: 6 }}>
                        <Tag color={TASK_STATUS_MAP[r.status]?.color}>
                            {TASK_STATUS_MAP[r.status]?.text || r.status}
                        </Tag>
                    </div>
                    <div className={styles.actions}>
                        <Button size="small" onClick={() => history.push(`/runner/task/detail?id=${r.id}`)}>
                            详情
                        </Button>
                        {r.status === 'accepted' && (
                            <>
                                <Button size="small" type="primary" onClick={() => onStart(r.id)}>
                                    开始服务
                                </Button>
                                <Button size="small" danger onClick={() => onReject(r.id)}>
                                    拒单
                                </Button>
                            </>
                        )}
                        {r.status === 'running' && (
                            <Button size="small" type="primary" onClick={() => onFinish(r.id)}>
                                提交完成
                            </Button>
                        )}
                    </div>
                </div>
            ))}
        </>
    );
};

const MyTaken: React.FC = () => {
    const isMobile = useIsMobile();
    const [status, setStatus] = useState<string>('all');
    const [reloadFlag, setReloadFlag] = useState(0);
    const actionRef = useRef<any>();

    const bump = () => {
        actionRef.current?.reload();
        setReloadFlag((f) => f + 1);
    };

    const handleStart = (taskId: number) => {
        Modal.confirm({
            title: '开始服务',
            content: '请确认已取件，开始服务。',
            onOk: async () => {
                try {
                    await hallStart(taskId);
                    message.success('已开始服务');
                    bump();
                } catch (e: any) {
                    message.error(e?.msg || e?.message || '操作失败');
                }
            },
        });
    };

    const handleFinish = (taskId: number) => {
        let images = '';
        Modal.confirm({
            title: '提交完成',
            content: (
                <div>
                    <p>请输入完成凭证图片 URL（可多个，逗号分隔）：</p>
                    <input
                        type="text"
                        placeholder="https://example.com/img1.jpg, https://example.com/img2.jpg"
                        onChange={(e) => (images = e.target.value)}
                        style={{ width: '100%', padding: 4, border: '1px solid #d9d9d9', borderRadius: 4 }}
                    />
                </div>
            ),
            onOk: async () => {
                try {
                    await hallFinish(taskId, images || undefined);
                    message.success('已提交，等待下单人确认');
                    bump();
                } catch (e: any) {
                    message.error(e?.msg || e?.message || '操作失败');
                }
            },
        });
    };

    const handleReject = (taskId: number) => {
        let reason = '';
        Modal.confirm({
            title: '拒单',
            content: (
                <div>
                    <p>请输入拒单原因：</p>
                    <input
                        type="text"
                        defaultValue="距离太远"
                        onChange={(e) => (reason = e.target.value)}
                        style={{ width: '100%', padding: 4, border: '1px solid #d9d9d9', borderRadius: 4 }}
                    />
                </div>
            ),
            okText: '确认拒单',
            okButtonProps: { danger: true },
            onOk: async () => {
                try {
                    await hallReject(taskId, reason || '接单人拒单');
                    message.success('已拒单，任务重新放回互助大厅');
                    bump();
                } catch (e: any) {
                    message.error(e?.msg || e?.message || '操作失败');
                }
            },
        });
    };

    const columns: ProColumns<any>[] = [
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
            title: '类型',
            dataIndex: 'taskType',
            search: false,
            width: 90,
            render: (_, r) => TASK_TYPE_MAP[r.taskType]?.label || '-',
        },
        {
            title: '报酬',
            dataIndex: 'price',
            search: false,
            width: 100,
            render: (_, r) => (
                <span style={{ color: '#FF6B35', fontWeight: 600 }}>
                    {r.isPaid === 1 ? formatPrice(r.price) : '免费（无报酬）'}
                </span>
            ),
        },
        {
            title: '状态',
            dataIndex: 'status',
            search: false,
            width: 100,
            render: (_, r) => (
                <Tag color={TASK_STATUS_MAP[r.status]?.color}>
                    {TASK_STATUS_MAP[r.status]?.text || r.status}
                </Tag>
            ),
        },
        {
            title: '接单时间',
            dataIndex: 'assignTime',
            search: false,
            width: 150,
            render: (_, r) => formatDateTime(r.assignTime),
        },
        {
            title: '操作',
            valueType: 'option',
            fixed: 'right',
            width: 240,
            render: (_, r) => (
                <Space>
                    <a onClick={() => history.push(`/runner/task/detail?id=${r.id}`)}>详情</a>
                    {r.status === 'accepted' && (
                        <>
                            <a style={{ color: '#52c41a' }} onClick={() => handleStart(r.id)}>
                                开始
                            </a>
                            <a style={{ color: '#ff4d4f' }} onClick={() => handleReject(r.id)}>
                                拒单
                            </a>
                        </>
                    )}
                    {r.status === 'running' && (
                        <a style={{ color: '#52c41a' }} onClick={() => handleFinish(r.id)}>
                            完成
                        </a>
                    )}
                </Space>
            ),
        },
    ];

    return (
        <PageContainer
            header={{ title: '我接的任务', subTitle: '跟踪任务状态，及时处理', breadcrumb: {} }}
        >
            {isMobile ? (
                <>
                    <Tabs
                        activeKey={status}
                        onChange={(k) => setStatus(k as string)}
                        items={[
                            { key: 'all', label: '全部' },
                            { key: 'accepted', label: '已接单' },
                            { key: 'running', label: '服务中' },
                            { key: 'pending_confirm', label: '待确认' },
                            { key: 'completed', label: '已完成' },
                        ]}
                    />
                    <MobileMyTaken
                        status={status}
                        reloadFlag={reloadFlag}
                        onStart={handleStart}
                        onFinish={handleFinish}
                        onReject={handleReject}
                    />
                </>
            ) : (
                <ProTable
                    actionRef={actionRef}
                    rowKey="id"
                    columns={columns}
                    search={false}
                    scroll={{ x: 1000 }}
                    pagination={{ showSizeChanger: true, defaultPageSize: 10 }}
                    request={async () => {
                        const r: any = await hallMyTaken(status === 'all' ? undefined : { status });
                        const list = r?.data ?? r ?? [];
                        return { data: Array.isArray(list) ? list : [], success: true, total: list.length };
                    }}
                    params={{ status }}
                    toolbar={{
                        menu: {
                            type: 'tab',
                            activeKey: status,
                            onChange: (k) => setStatus(k as string),
                            items: [
                                { key: 'all', label: '全部' },
                                { key: 'accepted', label: '已接单' },
                                { key: 'running', label: '服务中' },
                                { key: 'pending_confirm', label: '待确认' },
                                { key: 'completed', label: '已完成' },
                            ],
                        },
                    }}
                    options={false}
                />
            )}
        </PageContainer>
    );
};

export default MyTaken;
