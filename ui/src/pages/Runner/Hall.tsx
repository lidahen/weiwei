import { useIsMobile } from '@/hooks/useIsMobile';
import { hallAssign, hallList } from '@/services/runner/api';
import type { ProColumns } from '@ant-design/pro-components';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import { history } from '@umijs/max';
import {
    Button,
    Card,
    Col,
    Empty,
    InputNumber,
    Modal,
    Row,
    Segmented,
    Select,
    Space,
    Spin,
    message,
} from 'antd';
import { createStyles } from 'antd-style';
import React, { useEffect, useRef, useState } from 'react';
import { TASK_TYPE_MAP, formatDateTime, formatPrice } from './utils';

// 手机端任务卡片样式
const useMobileStyles = createStyles(() => ({
    list: {
        display: 'flex',
        flexDirection: 'column',
        gap: 10,
    },
    card: {
        background: '#fff',
        borderRadius: 12,
        border: '1px solid #EAEAE8',
        padding: 12,
    },
    headRow: {
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: 8,
        marginBottom: 6,
    },
    title: {
        fontWeight: 600,
        fontSize: 15,
        color: '#1A1A1A',
        flex: 1,
        minWidth: 0,
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        whiteSpace: 'nowrap',
    },
    price: {
        color: '#FF6B35',
        fontWeight: 700,
        fontSize: 16,
        flexShrink: 0,
    },
    route: {
        fontSize: 13,
        color: '#444',
        lineHeight: 1.6,
    },
    routeSub: {
        color: '#999',
    },
    metaRow: {
        marginTop: 8,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        fontSize: 12,
        color: '#9A9A9A',
        gap: 8,
    },
    takeBtn: {
        height: 32,
        padding: '0 18px',
        borderRadius: 16,
        background: '#FF6B35',
        borderColor: '#FF6B35',
        color: '#fff',
        fontWeight: 600,
    },
}));

// 手机端任务卡片列表
const MobileHall: React.FC<{ params: Record<string, any>; onAssign: (id: number) => void }> = ({
    params,
    onAssign,
}) => {
    const { styles } = useMobileStyles();
    const [list, setList] = useState<any[]>([]);
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        let cancelled = false;
        const load = async () => {
            setLoading(true);
            try {
                const r: any = await hallList({ limit: 50, ...params });
                const data = r?.data ?? r ?? [];
                if (!cancelled) setList(Array.isArray(data) ? data : []);
            } finally {
                if (!cancelled) setLoading(false);
            }
        };
        load();
        return () => {
            cancelled = true;
        };
    }, [params]);

    if (loading && list.length === 0)
        return <Spin style={{ display: 'block', margin: '60px auto' }} />;
    if (list.length === 0)
        return <Empty description="暂无可接任务" style={{ padding: '40px 0' }} />;

    return (
        <div className={styles.list}>
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
                    <div className={styles.route}>
                        📍 {r.pickupAddr || '-'}
                        <br />
                        <span className={styles.routeSub}>↓ {r.deliverAddr || '-'}</span>
                    </div>
                    <div className={styles.metaRow}>
                        <span>
                            {TASK_TYPE_MAP[r.taskType]?.label} · 编号 {r.taskNo}
                        </span>
                        <span>截止 {formatDateTime(r.deadline)}</span>
                    </div>
                    <div style={{ marginTop: 10, textAlign: 'right' }}>
                        <Button
                            size="small"
                            className={styles.takeBtn}
                            onClick={() => onAssign(r.id)}
                        >
                            接单
                        </Button>
                    </div>
                </div>
            ))}
        </div>
    );
};

const Hall: React.FC = () => {
    const isMobile = useIsMobile();
    const [taskType, setTaskType] = useState<number | undefined>(undefined);
    const [minPrice, setMinPrice] = useState<number | undefined>(undefined);
    const [maxPrice, setMaxPrice] = useState<number | undefined>(undefined);
    const actionRef = useRef<any>();

    const handleAssign = (taskId: number) => {
        Modal.confirm({
            title: '确认接单',
            content: '接单后请尽快到取件地址取件。重复接同一单会失败。',
            okText: '接单',
            cancelText: '再看看',
            onOk: async () => {
                try {
                    const r: any = await hallAssign(taskId);
                    const data = r?.data ?? r;
                    if (data?.success) {
                        message.success('接单成功！请尽快到取件地址');
                        actionRef.current?.reload();
                        history.push(`/runner/hall/my-taken`);
                    } else {
                        message.warning('已被接单 / 任务不存在 / 已过期 / 自接');
                        actionRef.current?.reload();
                    }
                } catch (e: any) {
                    message.error(e?.msg || e?.message || '接单失败');
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
            width: 120,
            render: (_, r) => (
                <span style={{ color: '#FF6B35', fontWeight: 700, fontSize: 16 }}>
                    {r.isPaid === 1 ? formatPrice(r.price) : '免费（无报酬）'}
                </span>
            ),
        },
        {
            title: '路线',
            dataIndex: 'pickupAddr',
            ellipsis: true,
            render: (_, r) => (
                <Space direction="vertical" size={0}>
                    <span>📍 {r.pickupAddr}</span>
                    <span style={{ color: '#999' }}>↓ {r.deliverAddr}</span>
                </Space>
            ),
        },
        {
            title: '截止',
            dataIndex: 'deadline',
            search: false,
            width: 150,
            render: (_, r) => formatDateTime(r.deadline),
        },
        {
            title: '操作',
            valueType: 'option',
            fixed: 'right',
            width: 120,
            render: (_, r) => (
                <a style={{ color: '#FF6B35', fontWeight: 600 }} onClick={() => handleAssign(r.id)}>
                    接单
                </a>
            ),
        },
    ];

    return (
        <PageContainer
            header={{ title: '互助大厅', subTitle: '邻里互助，举手之劳', breadcrumb: {} }}
        >
            <Card style={{ marginBottom: 16 }}>
                {isMobile ? (
                    // 移动端（复查报告 P0-1）：标签上置、控件整行，杜绝桌面 Row/Col 挤压重叠
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                        <div>
                            <div style={{ fontSize: 12, color: '#999', marginBottom: 4 }}>类型</div>
                            <Select
                                placeholder="全部"
                                allowClear
                                style={{ width: '100%' }}
                                value={taskType}
                                onChange={(v) => setTaskType(v)}
                                options={Object.entries(TASK_TYPE_MAP).map(([k, v]) => ({
                                    value: Number(k),
                                    label: `${v.emoji} ${v.label}`,
                                }))}
                            />
                        </div>
                        <div>
                            <div style={{ fontSize: 12, color: '#999', marginBottom: 4 }}>
                                价格区间
                            </div>
                            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                                <InputNumber
                                    placeholder="最低"
                                    min={0}
                                    style={{ flex: 1 }}
                                    value={minPrice}
                                    onChange={(v) => setMinPrice(v ?? undefined)}
                                    addonAfter="¥"
                                />
                                <span>~</span>
                                <InputNumber
                                    placeholder="最高"
                                    min={0}
                                    style={{ flex: 1 }}
                                    value={maxPrice}
                                    onChange={(v) => setMaxPrice(v ?? undefined)}
                                    addonAfter="¥"
                                />
                            </div>
                        </div>
                        <div>
                            <div style={{ fontSize: 12, color: '#999', marginBottom: 4 }}>排序</div>
                            <Segmented
                                block
                                options={[
                                    { label: '价格降序', value: 'price_desc' },
                                    { label: '最新发布', value: 'latest' },
                                ]}
                                defaultValue="price_desc"
                                disabled
                            />
                        </div>
                    </div>
                ) : (
                    <Row gutter={16} align="middle">
                        <Col>
                            <Space>
                                <span>类型：</span>
                                <Select
                                    placeholder="全部"
                                    allowClear
                                    style={{ width: 140 }}
                                    value={taskType}
                                    onChange={(v) => setTaskType(v)}
                                    options={Object.entries(TASK_TYPE_MAP).map(([k, v]) => ({
                                        value: Number(k),
                                        label: `${v.emoji} ${v.label}`,
                                    }))}
                                />
                            </Space>
                        </Col>
                        <Col>
                            <Space>
                                <span>价格：</span>
                                <InputNumber
                                    placeholder="最低"
                                    min={0}
                                    value={minPrice}
                                    onChange={(v) => setMinPrice(v ?? undefined)}
                                    addonAfter="¥"
                                />
                                <span>~</span>
                                <InputNumber
                                    placeholder="最高"
                                    min={0}
                                    value={maxPrice}
                                    onChange={(v) => setMaxPrice(v ?? undefined)}
                                    addonAfter="¥"
                                />
                            </Space>
                        </Col>
                        <Col>
                            <Space>
                                <span>排序：</span>
                                <Segmented
                                    options={[
                                        { label: '价格降序', value: 'price_desc' },
                                        { label: '最新发布', value: 'latest' },
                                    ]}
                                    defaultValue="price_desc"
                                    disabled
                                />
                            </Space>
                        </Col>
                    </Row>
                )}
            </Card>
            {isMobile ? (
                <MobileHall params={{ taskType, minPrice, maxPrice }} onAssign={handleAssign} />
            ) : (
                <ProTable
                    actionRef={actionRef}
                    rowKey="id"
                    columns={columns}
                    search={false}
                    scroll={{ x: 1000 }}
                    pagination={{ showSizeChanger: true, defaultPageSize: 20 }}
                    request={async () => {
                        const params: Record<string, any> = { limit: 50 };
                        if (taskType) params.taskType = taskType;
                        if (minPrice !== undefined) params.minPrice = minPrice;
                        if (maxPrice !== undefined) params.maxPrice = maxPrice;
                        const r: any = await hallList(params);
                        const list = r?.data ?? r ?? [];
                        return {
                            data: Array.isArray(list) ? list : [],
                            success: true,
                            total: list.length,
                        };
                    }}
                    params={{ taskType, minPrice, maxPrice }}
                    options={false}
                />
            )}
        </PageContainer>
    );
};

export default Hall;
