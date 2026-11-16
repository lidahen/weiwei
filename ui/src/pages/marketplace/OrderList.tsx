import { orderConfirm, orderPage } from '@/services/marketplace/api';
import { formatPrice, formatTime, IMG_FALLBACK, staticUrl } from './utils';
import type { ProColumns } from '@ant-design/pro-components';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import { history, useSearchParams } from '@umijs/max';
import { Avatar, Button, Empty, Image, Modal, Pagination, Space, Tabs, Tag, message } from 'antd';
import React, { useCallback, useRef, useState } from 'react';
import { createStyles } from 'antd-style';
import { marketplaceTokens, mq } from './styles/tokens';
import { useIsMobile } from '@/hooks/useIsMobile';

const statusMap: Record<string, { text: string; color: string }> = {
    unpaid: { text: '待付款', color: 'orange' },
    paid: { text: '已付款', color: 'gold' },
    unshipped: { text: '待发货', color: 'blue' },
    shipped: { text: '待收货', color: 'cyan' },
    done: { text: '已完成', color: 'green' },
    refunding: { text: '退款中', color: 'gold' },
    refunded: { text: '已退款', color: 'red' },
    closed: { text: '已关闭', color: 'default' },
};

// ---------- 手机端订单卡片样式 ----------
const useMobileStyles = createStyles(() => ({
    list: {
        display: 'flex',
        flexDirection: 'column',
        gap: 10,
    },
    card: {
        background: '#fff',
        borderRadius: 12,
        border: `1px solid ${marketplaceTokens.color.line}`,
        padding: 12,
    },
    headRow: {
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        marginBottom: 8,
    },
    peer: {
        display: 'flex',
        alignItems: 'center',
        gap: 6,
        fontSize: 13,
        color: marketplaceTokens.color.sub,
        minWidth: 0,
    },
    peerName: {
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        whiteSpace: 'nowrap',
    },
    statusTag: {
        flexShrink: 0,
        marginRight: 0,
    },
    goods: {
        display: 'flex',
        gap: 10,
        alignItems: 'center',
    },
    goodsInfo: {
        flex: 1,
        minWidth: 0,
        display: 'flex',
        flexDirection: 'column',
        gap: 4,
    },
    goodsTitle: {
        fontSize: 14,
        fontWeight: 500,
        color: marketplaceTokens.color.ink,
        display: '-webkit-box',
        WebkitLineClamp: 2,
        WebkitBoxOrient: 'vertical',
        overflow: 'hidden',
        lineHeight: 1.4,
    },
    goodsPrice: {
        fontSize: 15,
        fontWeight: 700,
        color: marketplaceTokens.color.primary,
    },
    goodsThumb: {
        borderRadius: 8,
        objectFit: 'cover',
        flexShrink: 0,
    },
    metaRow: {
        marginTop: 8,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        fontSize: 12,
        color: marketplaceTokens.color.mute,
        gap: 8,
    },
    orderNo: {
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        whiteSpace: 'nowrap',
        minWidth: 0,
    },
    amount: {
        fontWeight: 700,
        color: marketplaceTokens.color.ink,
        fontSize: 13,
    },
    actions: {
        marginTop: 10,
        display: 'flex',
        justifyContent: 'flex-end',
        gap: 8,
        flexWrap: 'wrap',
    },
    actionBtn: {
        height: 30,
        padding: '0 14px',
        borderRadius: 15,
        fontSize: 13,
    },
    actionBtnPrimary: {
        background: marketplaceTokens.color.primary,
        borderColor: marketplaceTokens.color.primary,
        color: '#fff',
    },
    pager: {
        display: 'flex',
        justifyContent: 'center',
        marginTop: 16,
        [mq.mobile]: {
            paddingBottom: 4,
        },
    },
}));

// 解析商品快照
const parseSnapshot = (raw: any) => {
    try {
        return typeof raw === 'string' ? JSON.parse(raw) : raw || {};
    } catch {
        return {};
    }
};

// ---------- 手机端卡片列表 ----------
const MobileOrders: React.FC<{
    role: 'buyer' | 'seller';
    status: string;
    reloadFlag: number;
    onConfirm: (orderId: number) => void;
}> = ({ role, status, reloadFlag, onConfirm }) => {
    const { styles } = useMobileStyles();
    const [page, setPage] = useState(1);
    const pageSize = 10;
    const [data, setData] = useState<any[]>([]);
    const [total, setTotal] = useState(0);
    const [loading, setLoading] = useState(false);

    // role/status 翻页重置
    React.useEffect(() => {
        setPage(1);
    }, [role, status]);

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const statusParam =
                status === 'all' ? undefined : status === 'unshipped' ? 'unshipped,paid' : status;
            const r: any = await orderPage({
                role,
                status: statusParam,
                // 后端 PageUtils 读取的是 current/pageSize（不是 pageNum）
                current: page,
                pageSize,
            });
            const payload = r?.data ?? r;
            setData(payload?.records || []);
            setTotal(payload?.total || 0);
        } finally {
            setLoading(false);
        }
    }, [role, status, page]);

    React.useEffect(() => {
        load();
    }, [load, reloadFlag]);

    return (
        <>
            <div className={styles.list}>
                {data.map((row) => {
                    const snap = parseSnapshot(row.productSnapshot);
                    const peerName = role === 'buyer' ? row.sellerNickName : row.buyerNickName;
                    const peerAvatar = role === 'buyer' ? row.sellerAvatar : row.buyerAvatar;
                    return (
                        <div key={row.orderId} className={styles.card}>
                            <div className={styles.headRow}>
                                <div className={styles.peer}>
                                    <Avatar src={staticUrl(peerAvatar)} size={22}>
                                        {peerName?.[0]}
                                    </Avatar>
                                    <span className={styles.peerName}>
                                        {role === 'buyer' ? '卖家：' : '买家：'}
                                        {peerName || '-'}
                                    </span>
                                </div>
                                <Tag color={statusMap[row.status]?.color} className={styles.statusTag}>
                                    {statusMap[row.status]?.text || row.status}
                                </Tag>
                            </div>
                            <div
                                className={styles.goods}
                                onClick={() => history.push(`/trade/order/detail?orderId=${row.orderId}`)}
                            >
                                <Image
                                    src={staticUrl(snap?.cover)}
                                    fallback={IMG_FALLBACK}
                                    width={64}
                                    height={64}
                                    className={styles.goodsThumb}
                                    preview={false}
                                />
                                <div className={styles.goodsInfo}>
                                    <div className={styles.goodsTitle}>{snap?.title || '-'}</div>
                                    <div className={styles.goodsPrice}>{formatPrice(snap?.price)}</div>
                                </div>
                            </div>
                            <div className={styles.metaRow}>
                                <span className={styles.orderNo}>单号 {row.orderNo}</span>
                                <span className={styles.amount}>实付 {formatPrice(row.totalAmount)}</span>
                            </div>
                            <div className={styles.metaRow} style={{ marginTop: 2 }}>
                                <span>{formatTime(row.createTime)}</span>
                            </div>
                            <div className={styles.actions}>
                                <Button
                                    size="small"
                                    className={styles.actionBtn}
                                    onClick={() => history.push(`/trade/order/detail?orderId=${row.orderId}`)}
                                >
                                    详情
                                </Button>
                                {role === 'buyer' && row.status === 'unpaid' && (
                                    <Button
                                        size="small"
                                        className={`${styles.actionBtn} ${styles.actionBtnPrimary}`}
                                        onClick={() => history.push(`/trade/pay?orderId=${row.orderId}`)}
                                    >
                                        去付款
                                    </Button>
                                )}
                                {role === 'buyer' && row.status === 'shipped' && (
                                    <Button
                                        size="small"
                                        className={styles.actionBtn}
                                        onClick={() => onConfirm(row.orderId)}
                                    >
                                        确认收货
                                    </Button>
                                )}
                                {role === 'seller' && row.status === 'unshipped' && (
                                    <Button
                                        size="small"
                                        className={`${styles.actionBtn} ${styles.actionBtnPrimary}`}
                                        onClick={() => history.push(`/trade/ship?orderId=${row.orderId}`)}
                                    >
                                        去发货
                                    </Button>
                                )}
                            </div>
                        </div>
                    );
                })}
                {!loading && data.length === 0 && (
                    <Empty description="暂无订单" style={{ padding: '40px 0' }} />
                )}
            </div>
            {total > pageSize && (
                <div className={styles.pager}>
                    <Pagination
                        simple
                        current={page}
                        pageSize={pageSize}
                        total={total}
                        onChange={(p) => setPage(p)}
                    />
                </div>
            )}
        </>
    );
};

// ---------- 主页面 ----------
const OrderList: React.FC = () => {
    const [params] = useSearchParams();
    const isMobile = useIsMobile();
    const [role, setRole] = useState<'buyer' | 'seller'>((params.get('role') as any) || 'buyer');
    const [status, setStatus] = useState<string>('all');
    const [reloadFlag, setReloadFlag] = useState(0);
    const actionRef = useRef<any>();

    const handleConfirmReceive = async (orderId: number) => {
        Modal.confirm({
            title: '确认收货',
            content: '请确认已收到商品且与描述一致，确认后交易完成。',
            okText: '确认收货',
            cancelText: '再等等',
            onOk: async () => {
                try {
                    await orderConfirm(orderId);
                    message.success('确认收货成功');
                    actionRef.current?.reload();
                    setReloadFlag((f) => f + 1);
                } catch (e: any) {
                    message.error(e?.msg || e?.message || '操作失败');
                }
            },
        });
    };

    const columns: ProColumns<any>[] = [
        {
            title: '商品',
            dataIndex: 'productSnapshot',
            search: false,
            render: (_, row) => {
                const snap = parseSnapshot(row.productSnapshot);
                return (
                    <Space>
                        <Image
                            src={staticUrl(snap?.cover)}
                            fallback={IMG_FALLBACK}
                            width={48}
                            height={48}
                            style={{ borderRadius: 8, objectFit: 'cover' }}
                        />
                        <div>
                            <div style={{ fontWeight: 500 }}>{snap?.title || '-'}</div>
                            <div style={{ color: marketplaceTokens.color.primary, fontWeight: 600 }}>
                                {formatPrice(snap?.price)}
                            </div>
                        </div>
                    </Space>
                );
            },
        },
        {
            title: role === 'buyer' ? '卖家' : '买家',
            dataIndex: role === 'buyer' ? 'sellerNickName' : 'buyerNickName',
            search: false,
            render: (_, row) => (
                <Space>
                    <Avatar src={staticUrl(role === 'buyer' ? row.sellerAvatar : row.buyerAvatar)} size={24} />
                    <span>{role === 'buyer' ? row.sellerNickName : row.buyerNickName}</span>
                </Space>
            ),
        },
        { title: '订单号', dataIndex: 'orderNo', search: false, copyable: true, ellipsis: true },
        {
            title: '金额',
            dataIndex: 'totalAmount',
            search: false,
            render: (_, r) => (
                <span style={{ color: marketplaceTokens.color.primary, fontWeight: 600 }}>
                    {formatPrice(r.totalAmount)}
                </span>
            ),
        },
        {
            title: '状态',
            dataIndex: 'status',
            search: false,
            render: (_, r) => (
                <Tag color={statusMap[r.status]?.color}>
                    {statusMap[r.status]?.text || r.status}
                </Tag>
            ),
        },
        {
            title: '下单时间',
            dataIndex: 'createTime',
            search: false,
            render: (_, r) => formatTime(r.createTime),
        },
        {
            title: '操作',
            valueType: 'option',
            fixed: 'right',
            width: 160,
            render: (_, row) => (
                <Space>
                    <a onClick={() => history.push(`/trade/order/detail?orderId=${row.orderId}`)}>详情</a>
                    {role === 'buyer' && row.status === 'shipped' && (
                        <a onClick={() => handleConfirmReceive(row.orderId)}>确认收货</a>
                    )}
                    {role === 'buyer' && row.status === 'unpaid' && (
                        <a onClick={() => history.push(`/trade/pay?orderId=${row.orderId}`)}>去付款</a>
                    )}
                    {role === 'seller' && row.status === 'unshipped' && (
                        <a onClick={() => history.push(`/trade/ship?orderId=${row.orderId}`)}>去发货</a>
                    )}
                </Space>
            ),
        },
    ];

    return (
        <PageContainer header={{ title: '我的订单', breadcrumb: {} }}>
            <Tabs
                activeKey={role}
                onChange={(k) => setRole(k as any)}
                items={[
                    { key: 'buyer', label: '我买到的' },
                    { key: 'seller', label: '我卖出的' },
                ]}
            />
            <Tabs
                activeKey={status}
                onChange={(k) => setStatus(k)}
                items={[
                    { key: 'all', label: '全部' },
                    { key: 'unpaid', label: '待付款' },
                    { key: 'unshipped', label: '待发货' },
                    { key: 'shipped', label: '待收货' },
                    { key: 'done', label: '已完成' },
                ]}
            />
            {isMobile ? (
                <MobileOrders role={role} status={status} reloadFlag={reloadFlag} onConfirm={handleConfirmReceive} />
            ) : (
                <ProTable
                    actionRef={actionRef}
                    rowKey="orderId"
                    columns={columns}
                    search={false}
                    scroll={{ x: 860 }}
                    pagination={{ showSizeChanger: true, defaultPageSize: 10 }}
                    request={async (p) => {
                        // 待发货 Tab 需同时覆盖 paid（已付款）与 unshipped（后端 IN 查询）
                        const statusParam =
                            status === 'all'
                                ? undefined
                                : status === 'unshipped'
                                ? 'unshipped,paid'
                                : status;
                        const r: any = await orderPage({
                            role,
                            status: statusParam,
                            pageNum: p.current,
                            pageSize: p.pageSize,
                        });
                        const payload = r?.data ?? r;
                        return {
                            data: payload?.records || [],
                            success: true,
                            total: payload?.total || 0,
                        };
                    }}
                    params={{ role, status }}
                    options={false}
                />
            )}
        </PageContainer>
    );
};

export default OrderList;
