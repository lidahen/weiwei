import { paymentMockPay, taskCancel, taskConfirm, taskMyList, taskRefund } from '@/services/runner/api';
import {
    PAY_STATUS_MAP,
    TASK_STATUS_MAP,
    TASK_TYPE_MAP,
    formatDateTime,
    formatPrice,
} from './utils';
import type { ProColumns } from '@ant-design/pro-components';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import { history, request, useSearchParams } from '@umijs/max';
import { Button, Empty, Modal, Space, Spin, Tabs, Tag, message } from 'antd';
import { createStyles } from 'antd-style';
import React, { useEffect, useRef, useState } from 'react';
import { useIsMobile } from '@/hooks/useIsMobile';

// 重新支付（后端 POST /runner/task/{id}/pay，幂等）
const taskRepay = (id: number | string) =>
    request(`/runner/task/${id}/pay`, { method: 'POST' });

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
    tags: { display: 'flex', gap: 6, flexWrap: 'wrap', marginTop: 6 },
    actions: {
        marginTop: 10,
        display: 'flex',
        justifyContent: 'flex-end',
        gap: 8,
        flexWrap: 'wrap',
    },
}));

const MobileMyList: React.FC<{
    status: string;
    reloadFlag: number;
    onRepay: (id: number) => void;
    onCancel: (id: number) => void;
    onConfirm: (id: number) => void;
    onRefund: (id: number) => void;
}> = ({ status, reloadFlag, onRepay, onCancel, onConfirm, onRefund }) => {
    const { styles } = useMobileStyles();
    const [list, setList] = useState<any[]>([]);
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        let cancelled = false;
        (async () => {
            setLoading(true);
            try {
                const r: any = await taskMyList(status === 'all' ? undefined : { status });
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
                        {r.isPaid === 1 ? (
                            <span className={styles.price}>{formatPrice(r.price)}</span>
                        ) : (
                            <Tag color="blue">免费</Tag>
                        )}
                    </div>
                    <div className={styles.meta}>
                        编号 {r.taskNo} · 截止 {formatDateTime(r.deadline)}
                    </div>
                    <div className={styles.tags}>
                        <Tag color={PAY_STATUS_MAP[r.payStatus || 'unpaid']?.color}>
                            {PAY_STATUS_MAP[r.payStatus || 'unpaid']?.text}
                        </Tag>
                        <Tag color={TASK_STATUS_MAP[r.status]?.color}>
                            {TASK_STATUS_MAP[r.status]?.text || r.status}
                        </Tag>
                    </div>
                    <div className={styles.actions}>
                        <Button size="small" onClick={() => history.push(`/runner/task/detail?id=${r.id}`)}>
                            详情
                        </Button>
                        {r.isPaid === 1 && (r.payStatus ?? 'unpaid') !== 'paid' && r.status !== 'cancelled' && (
                            <Button size="small" type="primary" onClick={() => onRepay(r.id)}>
                                去支付
                            </Button>
                        )}
                        {(r.status === 'pending' || r.status === 'accepted') && (
                            <Button size="small" danger onClick={() => onCancel(r.id)}>
                                取消
                            </Button>
                        )}
                        {r.status === 'pending_confirm' && (
                            <Button size="small" type="primary" onClick={() => onConfirm(r.id)}>
                                确认完成
                            </Button>
                        )}
                        {r.status === 'pending_confirm' && r.isPaid === 1 && (
                            <Button size="small" onClick={() => onRefund(r.id)}>
                                退款
                            </Button>
                        )}
                    </div>
                </div>
            ))}
        </>
    );
};

const MyList: React.FC = () => {
    const isMobile = useIsMobile();
    const [params] = useSearchParams();
    const [status, setStatus] = useState<string>(params.get('status') || 'all');
    const [reloadFlag, setReloadFlag] = useState(0);
    const actionRef = useRef<any>();

    const bump = () => {
        actionRef.current?.reload();
        setReloadFlag((f) => f + 1);
    };

    const handleCancel = (taskId: number) => {
        let reason = '';
        Modal.confirm({
            title: '取消任务',
            content: (
                <div>
                    <p>请输入取消原因：</p>
                    <input
                        type="text"
                        defaultValue="用户主动取消"
                        onChange={(e) => (reason = e.target.value)}
                        style={{ width: '100%', padding: 4, border: '1px solid #d9d9d9', borderRadius: 4 }}
                    />
                </div>
            ),
            onOk: async () => {
                try {
                    const r: any = await taskCancel(taskId, reason || '用户主动取消');
                    const data = r?.data ?? r;
                    if (data?.refundResult) {
                        message.success(
                            data.refundResult === 'success' ? '已取消并退款成功' : '已取消，退款处理中',
                        );
                    } else {
                        message.success('已取消');
                    }
                    bump();
                } catch (e: any) {
                    message.error(e?.msg || e?.message || '取消失败');
                }
            },
        });
    };

    const handleConfirm = (taskId: number) => {
        Modal.confirm({
            title: '确认完成',
            content: '请确认任务已完成，资金将释放给接单人。',
            onOk: async () => {
                try {
                    await taskConfirm(taskId);
                    message.success('确认成功');
                    bump();
                } catch (e: any) {
                    message.error(e?.msg || e?.message || '操作失败');
                }
            },
        });
    };

    const handleRefund = (taskId: number) => {
        let reason = '';
        Modal.confirm({
            title: '申请退款',
            content: (
                <div>
                    <p>请输入退款原因：</p>
                    <input
                        type="text"
                        defaultValue="任务未达预期"
                        onChange={(e) => (reason = e.target.value)}
                        style={{ width: '100%', padding: 4, border: '1px solid #d9d9d9', borderRadius: 4 }}
                    />
                </div>
            ),
            onOk: async () => {
                try {
                    const r: any = await taskRefund(taskId, reason || '任务未达预期');
                    const data = r?.data ?? r;
                    message.success(
                        data?.refundResult === 'success'
                            ? '退款已提交'
                            : data?.refundResult === 'failed'
                            ? '退款失败，请联系客服'
                            : '退款已申请',
                    );
                    bump();
                } catch (e: any) {
                    message.error(e?.msg || e?.message || '申请失败');
                }
            },
        });
    };

    const handleRepay = async (taskId: number) => {
        try {
            const r: any = await taskRepay(taskId);
            const data = r?.data ?? r;
            const pay = data?.pay;
            // dev 模拟支付标记：新建支付单带在 pay 上，复用旧单时由 controller 顶层返回
            const isMock = pay?.mock === true || data?.mock === true;
            if (data?.alreadyPaid) {
                message.success('该任务已支付，无需重复支付');
            } else if (pay?.error) {
                message.warning(pay.error + '，请稍后重试');
            } else if (isMock && pay?.payNo) {
                // dev 模拟支付：直接驱动履约（标支付单已付 + 任务进入互助大厅）
                const mockR: any = await paymentMockPay(pay.payNo);
                if (mockR?.code && mockR.code !== 200) {
                    message.error(mockR.msg || '模拟支付失败');
                } else {
                    message.success('模拟支付成功，任务已进入互助大厅');
                }
            } else if (pay?.qrCode || pay?.payUrl) {
                message.success('支付单已创建，请扫码完成支付');
                const url = pay.payUrl || pay.qrCode;
                if (typeof url === 'string' && url.startsWith('http')) {
                    window.open(url, '_blank');
                }
            } else if (data?.reused) {
                message.info('已有待支付订单，请在支付宝中完成扫码支付');
            } else {
                message.success('支付请求已提交');
            }
            bump();
        } catch (e: any) {
            message.error(e?.msg || e?.message || '支付发起失败');
        }
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
            render: (_, r) =>
                r.isPaid === 1 ? (
                    <span style={{ color: '#FF6B35', fontWeight: 600 }}>{formatPrice(r.price)}</span>
                ) : (
                    <Tag color="blue">免费任务（无报酬）</Tag>
                ),
        },
        {
            title: '支付',
            dataIndex: 'payStatus',
            search: false,
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
            search: false,
            width: 90,
            render: (_, r) => (
                <Tag color={TASK_STATUS_MAP[r.status]?.color}>
                    {TASK_STATUS_MAP[r.status]?.text || r.status}
                </Tag>
            ),
        },
        {
            title: '截止时间',
            dataIndex: 'deadline',
            search: false,
            width: 150,
            render: (_, r) => formatDateTime(r.deadline),
        },
        {
            title: '操作',
            valueType: 'option',
            fixed: 'right',
            width: 240,
            render: (_, r) => (
                <Space>
                    <a onClick={() => history.push(`/runner/task/detail?id=${r.id}`)}>详情</a>
                    {r.isPaid === 1 && (r.payStatus ?? 'unpaid') !== 'paid' && r.status !== 'cancelled' && (
                        <a style={{ color: '#FF6B35' }} onClick={() => handleRepay(r.id)}>
                            去支付
                        </a>
                    )}
                    {(r.status === 'pending' || r.status === 'accepted') && (
                        <a style={{ color: '#ff4d4f' }} onClick={() => handleCancel(r.id)}>
                            取消
                        </a>
                    )}
                    {r.status === 'pending_confirm' && (
                        <a style={{ color: '#52c41a' }} onClick={() => handleConfirm(r.id)}>
                            确认
                        </a>
                    )}
                    {r.status === 'pending_confirm' && r.isPaid === 1 && (
                        <a onClick={() => handleRefund(r.id)}>退款</a>
                    )}
                </Space>
            ),
        },
    ];

    return (
        <PageContainer
            header={{ title: '我发布的任务', subTitle: '管理与跟踪互助任务', breadcrumb: {} }}
        >
            <Space style={{ marginBottom: 16 }}>
                <Button type="primary" onClick={() => history.push('/runner/task/publish')}>
                    发布新任务
                </Button>
            </Space>
            {isMobile ? (
                <>
                    <Tabs
                        activeKey={status}
                        onChange={(k) => setStatus(k as string)}
                        items={[
                            { key: 'all', label: '全部' },
                            { key: 'pending', label: '待接单' },
                            { key: 'accepted', label: '已接单' },
                            { key: 'running', label: '服务中' },
                            { key: 'pending_confirm', label: '待确认' },
                            { key: 'completed', label: '已完成' },
                            { key: 'cancelled', label: '已取消' },
                            { key: 'refund', label: '退款中' },
                        ]}
                    />
                    <MobileMyList
                        status={status}
                        reloadFlag={reloadFlag}
                        onRepay={handleRepay}
                        onCancel={handleCancel}
                        onConfirm={handleConfirm}
                        onRefund={handleRefund}
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
                        const r: any = await taskMyList(status === 'all' ? undefined : { status });
                        const list = r?.data ?? r ?? [];
                        return { data: Array.isArray(list) ? list : [], success: true, total: list.length || 0 };
                    }}
                    params={{ status }}
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
                                { key: 'refund', label: '退款中' },
                            ],
                        },
                    }}
                    options={false}
                />
            )}
        </PageContainer>
    );
};

export default MyList;
