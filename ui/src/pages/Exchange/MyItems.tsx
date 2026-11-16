import { useIsMobile } from '@/hooks/useIsMobile';
import { itemMyItems, itemOffshelf } from '@/services/exchange/api';
import TWT from '@/setting';
import type { ProColumns } from '@ant-design/pro-components';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import { history } from '@umijs/max';
import { Alert, Button, Empty, Modal, Space, Spin, Tabs, Tag, message } from 'antd';
import { createStyles } from 'antd-style';
import React, { useEffect, useRef, useState } from 'react';
import {
    CONDITION_MAP,
    DELIVERY_MODE_MAP,
    ITEM_STATUS_MAP,
    formatDateTime,
    formatPrice,
} from './utils';

const STATUS_TABS = [
    { key: 'all', label: '全部' },
    { key: 'auditing', label: '审核中' },
    { key: 'onswap', label: '换物中' },
    { key: 'locked', label: '锁定' },
    { key: 'swapped', label: '已换出' },
    { key: 'rejected', label: '已驳回' },
    { key: 'offshelf', label: '已下架' },
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
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        whiteSpace: 'nowrap',
    },
    cover: {
        width: 56,
        height: 56,
        borderRadius: 8,
        objectFit: 'cover',
        flexShrink: 0,
    },
    coverEmpty: {
        width: 56,
        height: 56,
        borderRadius: 8,
        background: '#F5F1E8',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        fontSize: 22,
        flexShrink: 0,
    },
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

const MobileMyItems: React.FC<{
    status: string;
    reloadFlag: number;
    onToggle: (item: any) => void;
}> = ({ status, reloadFlag, onToggle }) => {
    const { styles } = useMobileStyles();
    const [list, setList] = useState<any[]>([]);
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        let cancelled = false;
        (async () => {
            setLoading(true);
            try {
                const r: any = await itemMyItems(status === 'all' ? undefined : { status });
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

    if (loading && list.length === 0)
        return <Spin style={{ display: 'block', margin: '60px auto' }} />;
    if (list.length === 0) return <Empty description="暂无物品" style={{ padding: '40px 0' }} />;

    return (
        <>
            {list.map((r) => (
                <div key={r.id} className={styles.card}>
                    <div className={styles.headRow}>
                        {r.cover ? (
                            <img
                                className={styles.cover}
                                src={
                                    r.cover.startsWith('http') ? r.cover : `${TWT.static}${r.cover}`
                                }
                                alt={r.title}
                            />
                        ) : (
                            <div className={styles.coverEmpty}>🎁</div>
                        )}
                        <a
                            className={styles.title}
                            onClick={() => history.push(`/exchange/detail/${r.id}`)}
                            style={{ marginLeft: 10 }}
                        >
                            {r.title}
                        </a>
                        <Tag color={ITEM_STATUS_MAP[r.status]?.color}>
                            {ITEM_STATUS_MAP[r.status]?.text || r.status}
                        </Tag>
                    </div>
                    <div className={styles.meta}>
                        编号 {r.itemNo} · {CONDITION_MAP[r.conditionLevel] || r.conditionLevel} ·{' '}
                        {DELIVERY_MODE_MAP[r.deliveryMode] || r.deliveryMode}
                        {r.estValue !== null &&
                            r.estValue !== undefined &&
                            ` · 参考值 ${formatPrice(r.estValue)}`}
                    </div>
                    <div className={styles.meta}>发布 {formatDateTime(r.createTime)}</div>
                    {r.status === 'rejected' && r.rejectReason && (
                        <Alert
                            message={`驳回原因：${r.rejectReason}`}
                            type="error"
                            showIcon
                            style={{ marginTop: 6 }}
                        />
                    )}
                    <div className={styles.actions}>
                        <Button
                            size="small"
                            onClick={() => history.push(`/exchange/detail/${r.id}`)}
                        >
                            详情
                        </Button>
                        {(r.status === 'onswap' || r.status === 'offshelf') && (
                            <Button size="small" type="primary" onClick={() => onToggle(r)}>
                                {r.status === 'onswap' ? '下架' : '重新上架'}
                            </Button>
                        )}
                    </div>
                </div>
            ))}
        </>
    );
};

const MyItems: React.FC = () => {
    const isMobile = useIsMobile();
    const [status, setStatus] = useState<string>('all');
    const [reloadFlag, setReloadFlag] = useState(0);
    const actionRef = useRef<any>();

    const bump = () => {
        actionRef.current?.reload();
        setReloadFlag((f) => f + 1);
    };

    const handleToggle = (item: any) => {
        const toOffshelf = item.status === 'onswap';
        Modal.confirm({
            title: toOffshelf ? '下架物品' : '重新上架',
            content: toOffshelf
                ? '下架后将不会出现在换物广场，也收不到新的意向，随时可重新上架。'
                : '重新上架后物品回到换物广场，可继续接收意向。',
            okText: toOffshelf ? '下架' : '上架',
            onOk: async () => {
                try {
                    await itemOffshelf(item.id);
                    message.success(toOffshelf ? '已下架' : '已重新上架');
                    bump();
                } catch (e: any) {
                    message.error(e?.msg || e?.message || '操作失败');
                }
            },
        });
    };

    const columns: ProColumns<any>[] = [
        {
            title: '物品',
            dataIndex: 'title',
            ellipsis: true,
            render: (_, r) => (
                <Space direction="vertical" size={0}>
                    <a onClick={() => history.push(`/exchange/detail/${r.id}`)}>{r.title}</a>
                    <span style={{ color: '#999', fontSize: 12 }}>编号：{r.itemNo}</span>
                </Space>
            ),
        },
        {
            title: '成色',
            dataIndex: 'conditionLevel',
            search: false,
            width: 90,
            render: (_, r) => CONDITION_MAP[r.conditionLevel] || r.conditionLevel,
        },
        {
            title: '参考值',
            dataIndex: 'estValue',
            search: false,
            width: 110,
            render: (_, r) =>
                r.estValue !== null && r.estValue !== undefined ? (
                    <span style={{ color: '#FF6B35', fontWeight: 600 }}>
                        {formatPrice(r.estValue)}
                    </span>
                ) : (
                    '-'
                ),
        },
        {
            title: '交付方式',
            dataIndex: 'deliveryMode',
            search: false,
            width: 110,
            render: (_, r) => DELIVERY_MODE_MAP[r.deliveryMode] || r.deliveryMode,
        },
        {
            title: '状态',
            dataIndex: 'status',
            search: false,
            width: 90,
            render: (_, r) => (
                <Tag color={ITEM_STATUS_MAP[r.status]?.color}>
                    {ITEM_STATUS_MAP[r.status]?.text || r.status}
                </Tag>
            ),
        },
        {
            title: '浏览/意向',
            search: false,
            width: 100,
            render: (_, r) => `${r.viewCount ?? 0} / ${r.offerCount ?? 0}`,
        },
        {
            title: '发布时间',
            dataIndex: 'createTime',
            search: false,
            width: 150,
            render: (_, r) => formatDateTime(r.createTime),
        },
        {
            title: '操作',
            valueType: 'option',
            fixed: 'right',
            width: 160,
            render: (_, r) => (
                <Space>
                    <a onClick={() => history.push(`/exchange/detail/${r.id}`)}>详情</a>
                    {(r.status === 'onswap' || r.status === 'offshelf') && (
                        <a
                            style={{ color: r.status === 'onswap' ? '#ff4d4f' : '#52c41a' }}
                            onClick={() => handleToggle(r)}
                        >
                            {r.status === 'onswap' ? '下架' : '重新上架'}
                        </a>
                    )}
                </Space>
            ),
        },
    ];

    return (
        <PageContainer
            header={{ title: '我的换物物品', subTitle: '管理物品状态与上下架', breadcrumb: {} }}
        >
            <Space style={{ marginBottom: 16 }}>
                <Button type="primary" onClick={() => history.push('/exchange/publish')}>
                    发布新物品
                </Button>
            </Space>
            {isMobile ? (
                <>
                    <Tabs
                        activeKey={status}
                        onChange={(k) => setStatus(k as string)}
                        items={STATUS_TABS}
                    />
                    <MobileMyItems
                        status={status}
                        reloadFlag={reloadFlag}
                        onToggle={handleToggle}
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
                        const r: any = await itemMyItems(status === 'all' ? undefined : { status });
                        const list = r?.data ?? r ?? [];
                        return {
                            data: Array.isArray(list) ? list : [],
                            success: true,
                            total: list.length || 0,
                        };
                    }}
                    params={{ status }}
                    toolbar={{
                        menu: {
                            type: 'tab',
                            activeKey: status,
                            onChange: (k) => setStatus(k as string),
                            items: STATUS_TABS,
                        },
                    }}
                    options={false}
                />
            )}
        </PageContainer>
    );
};

export default MyItems;
