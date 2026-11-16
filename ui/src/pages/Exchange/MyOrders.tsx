import { useIsMobile } from '@/hooks/useIsMobile';
import { orderMyOrders } from '@/services/exchange/api';
import type { ProColumns } from '@ant-design/pro-components';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import { history, useModel } from '@umijs/max';
import { Empty, Pagination, Space, Spin, Tabs, Tag, Typography } from 'antd';
import { createStyles } from 'antd-style';
import React, { useEffect, useRef, useState } from 'react';
import { DELIVERY_MODE_MAP, ORDER_STATUS_MAP, formatDateTime } from './utils';

const { Text } = Typography;

// tab → 后端 statuses 参数映射（服务端真分页，"进行中"跨 created/delivering 由后端 IN 查询）
const TAB_STATUSES: Record<string, string | undefined> = {
    all: undefined,
    ongoing: 'created,delivering',
    pending_confirm: 'pending_confirm',
    completed: 'completed',
    disputed: 'disputed',
};

const STATUS_TABS = [
    { key: 'all', label: '全部' },
    { key: 'ongoing', label: '进行中' },
    { key: 'pending_confirm', label: '待确认' },
    { key: 'completed', label: '已完成' },
    { key: 'disputed', label: '纠纷' },
];

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
    },
    meta: { fontSize: 12, color: '#9A9A9A', marginTop: 4 },
    actions: {
        marginTop: 10,
        display: 'flex',
        justifyContent: 'flex-end',
        gap: 8,
        flexWrap: 'wrap',
    },
}));

const MobileMyOrders: React.FC<{ list: any[]; loading: boolean; myId: number }> = ({
    list,
    loading,
    myId,
}) => {
    const { styles } = useMobileStyles();
    if (loading && list.length === 0)
        return <Spin style={{ display: 'block', margin: '60px auto' }} />;
    if (list.length === 0)
        return <Empty description="暂无交换订单" style={{ padding: '40px 0' }} />;

    return (
        <>
            {list.map((r) => {
                const isA = Number(r.ownerAId) === myId;
                return (
                    <div key={r.id} className={styles.card}>
                        <div className={styles.headRow}>
                            <span className={styles.title}>交换单 {r.orderNo}</span>
                            <Tag color={ORDER_STATUS_MAP[r.status]?.color}>
                                {ORDER_STATUS_MAP[r.status]?.text || r.status}
                            </Tag>
                        </div>
                        <div className={styles.meta}>
                            我的角色：{isA ? 'A · 物品主人' : 'B · 换物发起人'} ·{' '}
                            {DELIVERY_MODE_MAP[r.deliveryMode] || r.deliveryMode}
                        </div>
                        <div className={styles.meta}>
                            {isA
                                ? `我的物品 #${r.itemAId} ⇄ 对方物品 #${r.itemBId}`
                                : `对方物品 #${r.itemAId} ⇄ 我的物品 #${r.itemBId}`}
                        </div>
                        <div className={styles.meta}>建立 {formatDateTime(r.createTime)}</div>
                        <div className={styles.actions}>
                            <a onClick={() => history.push(`/exchange/order-detail/${r.id}`)}>
                                查看进度 →
                            </a>
                        </div>
                    </div>
                );
            })}
        </>
    );
};

const MyOrders: React.FC = () => {
    const isMobile = useIsMobile();
    const { initialState } = useModel('@@initialState');
    const myId = Number((initialState as any)?.currentUser?.user?.userId);
    const [tab, setTab] = useState<string>('all');
    const [reloadFlag] = useState(0);
    const actionRef = useRef<any>();

    const [orders, setOrders] = useState<any[]>([]);
    const [total, setTotal] = useState(0);
    const [pageNum, setPageNum] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const [loading, setLoading] = useState(false);

    // 服务端真分页：tab 变化回到第 1 页
    useEffect(() => {
        setPageNum(1);
    }, [tab]);

    useEffect(() => {
        let cancelled = false;
        (async () => {
            setLoading(true);
            try {
                const r: any = await orderMyOrders({
                    statuses: TAB_STATUSES[tab],
                    pageNum,
                    pageSize,
                });
                const payload = r?.data ?? r ?? {};
                if (!cancelled) {
                    setOrders(Array.isArray(payload?.records) ? payload.records : []);
                    setTotal(Number(payload?.total ?? 0));
                }
            } finally {
                if (!cancelled) setLoading(false);
            }
        })();
        return () => {
            cancelled = true;
        };
    }, [tab, pageNum, pageSize, reloadFlag]);

    const columns: ProColumns<any>[] = [
        {
            title: '订单号',
            dataIndex: 'orderNo',
            width: 200,
            render: (_, r) => (
                <Space direction="vertical" size={0}>
                    <a onClick={() => history.push(`/exchange/order-detail/${r.id}`)}>
                        {r.orderNo}
                    </a>
                    <span style={{ color: '#999', fontSize: 12 }}>
                        {formatDateTime(r.createTime)}
                    </span>
                </Space>
            ),
        },
        {
            title: '我的角色',
            search: false,
            width: 110,
            render: (_, r) =>
                Number(r.ownerAId) === myId ? (
                    <Tag color="green">A · 物品主人</Tag>
                ) : (
                    <Tag color="blue">B · 发起人</Tag>
                ),
        },
        {
            title: '交换物品',
            search: false,
            ellipsis: true,
            render: (_, r) => (
                <span>
                    {Number(r.ownerAId) === myId ? '我的' : '对方'}物品 #{r.itemAId} ⇄{' '}
                    {Number(r.ownerBId) === myId ? '我的' : '对方'}物品 #{r.itemBId}
                </span>
            ),
        },
        {
            title: '交付方式',
            dataIndex: 'deliveryMode',
            search: false,
            width: 100,
            render: (_, r) => DELIVERY_MODE_MAP[r.deliveryMode] || r.deliveryMode,
        },
        {
            title: '状态',
            dataIndex: 'status',
            search: false,
            width: 90,
            render: (_, r) => (
                <Tag color={ORDER_STATUS_MAP[r.status]?.color}>
                    {ORDER_STATUS_MAP[r.status]?.text || r.status}
                </Tag>
            ),
        },
        {
            title: '更新时间',
            dataIndex: 'updateTime',
            search: false,
            width: 150,
            render: (_, r) => formatDateTime(r.updateTime),
        },
        {
            title: '操作',
            valueType: 'option',
            fixed: 'right',
            width: 100,
            render: (_, r) => (
                <a onClick={() => history.push(`/exchange/order-detail/${r.id}`)}>查看进度</a>
            ),
        },
    ];

    return (
        <PageContainer
            header={{ title: '我的交换', subTitle: 'A/B 双视角跟踪双边履约进度', breadcrumb: {} }}
        >
            {isMobile ? (
                <>
                    <Tabs
                        activeKey={tab}
                        onChange={(k) => setTab(k as string)}
                        items={STATUS_TABS}
                    />
                    <MobileMyOrders list={orders} loading={loading} myId={myId} />
                    <div style={{ display: 'flex', justifyContent: 'center', padding: '12px 0' }}>
                        <Pagination
                            current={pageNum}
                            pageSize={pageSize}
                            total={total}
                            size="small"
                            showSizeChanger
                            onChange={(p, s) => {
                                setPageNum(p);
                                setPageSize(s);
                            }}
                        />
                    </div>
                </>
            ) : (
                <ProTable
                    actionRef={actionRef}
                    rowKey="id"
                    columns={columns}
                    search={false}
                    scroll={{ x: 1000 }}
                    dataSource={orders}
                    loading={loading}
                    pagination={{
                        current: pageNum,
                        pageSize,
                        total,
                        showSizeChanger: true,
                        onChange: (p, s) => {
                            setPageNum(p);
                            setPageSize(s);
                        },
                    }}
                    toolbar={{
                        menu: {
                            type: 'tab',
                            activeKey: tab,
                            onChange: (k) => setTab(k as string),
                            items: STATUS_TABS,
                        },
                    }}
                    options={false}
                    locale={{ emptyText: <Empty description="暂无交换订单" /> }}
                />
            )}
            <Text type="secondary" style={{ fontSize: 12 }}>
                双边履约：双方各自交付、各自确认；建单 7 天未双交付自动取消，双交付后 3
                天未双确认自动完成。
            </Text>
        </PageContainer>
    );
};

export default MyOrders;
