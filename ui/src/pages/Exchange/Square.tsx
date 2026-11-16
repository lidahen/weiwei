import { useIsMobile } from '@/hooks/useIsMobile';
import { categoryTree, squareList, squareRecommend } from '@/services/exchange/api';
import TWT from '@/setting';
import { PageContainer } from '@ant-design/pro-components';
import Seo from '@/components/Seo';
import SeoHeading from '@/components/Seo/Heading';
import { isLoggedIn } from '@/utils/loginGuard';
import { history } from '@umijs/max';
import { Button, Card, Col, Empty, Input, Pagination, Row, Select, Spin, Tag, message } from 'antd';
import { createStyles } from 'antd-style';
import React, { useEffect, useMemo, useState } from 'react';
import {
    CONDITION_MAP,
    CONDITION_OPTIONS,
    DELIVERY_MODE_MAP,
    DELIVERY_MODE_OPTIONS,
    formatPrice,
} from './utils';

// 手机端/桌面端共用的换物卡片样式（基调对齐 Runner 页面卡片）
const useStyles = createStyles(() => ({
    grid: {
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))',
        gap: 12,
    },
    card: {
        background: '#fff',
        borderRadius: 12,
        border: '1px solid #EAEAE8',
        overflow: 'hidden',
        cursor: 'pointer',
        display: 'flex',
        flexDirection: 'column',
    },
    cover: {
        width: '100%',
        aspectRatio: '1 / 1',
        objectFit: 'cover',
        display: 'block',
        background: '#F5F1E8',
    },
    coverEmpty: {
        width: '100%',
        aspectRatio: '1 / 1',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        color: '#B8B8B0',
        fontSize: 28,
        background: '#F5F1E8',
    },
    body: {
        padding: 10,
        display: 'flex',
        flexDirection: 'column',
        gap: 6,
        flex: 1,
    },
    title: {
        fontWeight: 600,
        fontSize: 14,
        color: '#1A1A1A',
        lineHeight: 1.4,
        display: '-webkit-box',
        WebkitLineClamp: 2,
        WebkitBoxOrient: 'vertical',
        overflow: 'hidden',
    },
    want: {
        fontSize: 12,
        color: '#6B6B66',
        display: '-webkit-box',
        WebkitLineClamp: 2,
        WebkitBoxOrient: 'vertical',
        overflow: 'hidden',
    },
    metaRow: {
        marginTop: 'auto',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        fontSize: 12,
        color: '#9A9A9A',
        gap: 8,
    },
    value: {
        color: '#FF6B35',
        fontWeight: 700,
        fontSize: 14,
    },
    // 为你推荐：横向卡片条（缩小版广场卡片，移动端/桌面端均可横向滑动）
    recStrip: {
        display: 'flex',
        gap: 12,
        overflowX: 'auto',
        paddingBottom: 4,
    },
}));

const Square: React.FC = () => {
    const isMobile = useIsMobile();
    const { styles } = useStyles();
    const [catL1, setCatL1] = useState<string | undefined>(undefined);
    const [conditionLevel, setConditionLevel] = useState<string | undefined>(undefined);
    const [deliveryMode, setDeliveryMode] = useState<string | undefined>(undefined);
    const [keyword, setKeyword] = useState<string>('');
    const [pageNum, setPageNum] = useState(1);
    const pageSize = isMobile ? 12 : 20;

    const [list, setList] = useState<any[]>([]);
    const [total, setTotal] = useState(0);
    const [loading, setLoading] = useState(false);
    const [catOptions, setCatOptions] = useState<any[]>([]);

    // 为你推荐（登录用户 want_desc 向量召回；空态/降级整条隐藏，绝不阻塞广场主流程）
    const [recs, setRecs] = useState<any[]>([]);
    useEffect(() => {
        if (!isLoggedIn()) return;
        let cancelled = false;
        squareRecommend({ limit: 8 })
            .then((r: any) => {
                if (cancelled) return;
                const payload = r?.data ?? r;
                setRecs(Array.isArray(payload) ? payload : []);
            })
            .catch(() => {
                if (!cancelled) setRecs([]);
            });
        return () => {
            cancelled = true;
        };
    }, []);

    // 类目树（只读复用商品类目字典；广场筛选只到一级，与后端 catL1 参数对应）
    useEffect(() => {
        categoryTree()
            .then((r: any) => setCatOptions(r?.data ?? r ?? []))
            .catch(() => setCatOptions([]));
    }, []);

    const filters = useMemo(
        () => ({ catL1, conditionLevel, deliveryMode, keyword: keyword.trim() || undefined }),
        [catL1, conditionLevel, deliveryMode, keyword],
    );

    useEffect(() => {
        let cancelled = false;
        (async () => {
            setLoading(true);
            try {
                const r: any = await squareList({ pageNum, pageSize, ...filters });
                const payload = r?.data ?? r;
                const records = payload?.records ?? [];
                if (!cancelled) {
                    setList(Array.isArray(records) ? records : []);
                    setTotal(payload?.total ?? 0);
                }
            } catch (e: any) {
                if (!cancelled) message.error(e?.msg || e?.message || '广场加载失败');
            } finally {
                if (!cancelled) setLoading(false);
            }
        })();
        return () => {
            cancelled = true;
        };
    }, [filters, pageNum, pageSize]);

    // 筛选变化回到第一页
    const changeFilter = (setter: (v: any) => void) => (v: any) => {
        setter(v);
        setPageNum(1);
    };

    const onSearch = (v: string) => {
        setKeyword(v);
        setPageNum(1);
    };

    const renderCard = (item: any) => {
        const imgs = item.cover ? [item.cover] : [];
        return (
            <div
                key={item.id}
                className={styles.card}
                onClick={() => history.push(`/exchange/detail/${item.id}`)}
            >
                {imgs.length > 0 ? (
                    <img
                        className={styles.cover}
                        src={imgs[0].startsWith('http') ? imgs[0] : `${TWT.static}${imgs[0]}`}
                        alt={item.title}
                        loading="lazy"
                    />
                ) : (
                    <div className={styles.coverEmpty}>🎁</div>
                )}
                <div className={styles.body}>
                    <div className={styles.title}>{item.title}</div>
                    <div>
                        <Tag color="green" style={{ marginRight: 4 }}>
                            {CONDITION_MAP[item.conditionLevel] || item.conditionLevel}
                        </Tag>
                        <Tag>{DELIVERY_MODE_MAP[item.deliveryMode] || item.deliveryMode}</Tag>
                    </div>
                    {item.wantDesc && <div className={styles.want}>想换：{item.wantDesc}</div>}
                    <div className={styles.metaRow}>
                        <span className={styles.value}>
                            {item.estValue !== null && item.estValue !== undefined
                                ? `参考值 ${formatPrice(item.estValue)}`
                                : '参考值 --'}
                        </span>
                        <span>
                            {item.city || item.location || ''}
                            {item.offerCount > 0 ? ` · ${item.offerCount} 意向` : ''}
                        </span>
                    </div>
                </div>
            </div>
        );
    };

    // 推荐位缩小版卡片（样式复用广场卡片，仅宽度收窄 + 单行标题）
    const renderRecCard = (item: any) => {
        const imgs = item.cover ? [item.cover] : [];
        return (
            <div
                key={item.id}
                className={styles.card}
                style={{ width: 148, minWidth: 148, flexShrink: 0 }}
                onClick={() => history.push(`/exchange/detail/${item.id}`)}
            >
                {imgs.length > 0 ? (
                    <img
                        className={styles.cover}
                        src={imgs[0].startsWith('http') ? imgs[0] : `${TWT.static}${imgs[0]}`}
                        alt={item.title}
                        loading="lazy"
                    />
                ) : (
                    <div className={styles.coverEmpty} style={{ fontSize: 22 }}>
                        🎁
                    </div>
                )}
                <div className={styles.body}>
                    <div className={styles.title} style={{ fontSize: 13, WebkitLineClamp: 1 }}>
                        {item.title}
                    </div>
                    <div>
                        <Tag color="green" style={{ marginRight: 4 }}>
                            {CONDITION_MAP[item.conditionLevel] || item.conditionLevel}
                        </Tag>
                    </div>
                    <div className={styles.metaRow}>
                        <span className={styles.value} style={{ fontSize: 12 }}>
                            {item.estValue !== null && item.estValue !== undefined
                                ? formatPrice(item.estValue)
                                : '参考值 --'}
                        </span>
                    </div>
                </div>
            </div>
        );
    };

    const filterBar = (
        <Card style={{ marginBottom: 16 }}>
            {isMobile ? (
                // 移动端：标签上置、控件整行（对齐 Runner/Hall 移动端筛选习惯）
                <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                    <Input.Search allowClear placeholder="搜物品标题 / 描述" onSearch={onSearch} />
                    <div>
                        <div style={{ fontSize: 12, color: '#999', marginBottom: 4 }}>类目</div>
                        <Select
                            placeholder="全部"
                            allowClear
                            style={{ width: '100%' }}
                            value={catL1}
                            onChange={changeFilter(setCatL1)}
                            options={catOptions.map((c: any) => ({ value: c.code, label: c.name }))}
                        />
                    </div>
                    <div>
                        <div style={{ fontSize: 12, color: '#999', marginBottom: 4 }}>成色</div>
                        <Select
                            placeholder="全部"
                            allowClear
                            style={{ width: '100%' }}
                            value={conditionLevel}
                            onChange={changeFilter(setConditionLevel)}
                            options={CONDITION_OPTIONS}
                        />
                    </div>
                    <div>
                        <div style={{ fontSize: 12, color: '#999', marginBottom: 4 }}>交付方式</div>
                        <Select
                            placeholder="全部"
                            allowClear
                            style={{ width: '100%' }}
                            value={deliveryMode}
                            onChange={changeFilter(setDeliveryMode)}
                            options={DELIVERY_MODE_OPTIONS.map((o) => ({
                                value: o.value,
                                label: DELIVERY_MODE_MAP[o.value],
                            }))}
                        />
                    </div>
                    <Button type="primary" block onClick={() => history.push('/exchange/publish')}>
                        发布物品
                    </Button>
                </div>
            ) : (
                <Row gutter={16} align="middle">
                    <Col>
                        <Input.Search
                            allowClear
                            placeholder="搜物品标题 / 描述"
                            style={{ width: 240 }}
                            onSearch={onSearch}
                        />
                    </Col>
                    <Col>
                        <Select
                            placeholder="类目"
                            allowClear
                            style={{ width: 140 }}
                            value={catL1}
                            onChange={changeFilter(setCatL1)}
                            options={catOptions.map((c: any) => ({ value: c.code, label: c.name }))}
                        />
                    </Col>
                    <Col>
                        <Select
                            placeholder="成色"
                            allowClear
                            style={{ width: 120 }}
                            value={conditionLevel}
                            onChange={changeFilter(setConditionLevel)}
                            options={CONDITION_OPTIONS}
                        />
                    </Col>
                    <Col>
                        <Select
                            placeholder="交付方式"
                            allowClear
                            style={{ width: 150 }}
                            value={deliveryMode}
                            onChange={changeFilter(setDeliveryMode)}
                            options={DELIVERY_MODE_OPTIONS.map((o) => ({
                                value: o.value,
                                label: DELIVERY_MODE_MAP[o.value],
                            }))}
                        />
                    </Col>
                </Row>
            )}
        </Card>
    );

    return (
        <PageContainer
            header={{ title: '换物广场', subTitle: '以物换物，让闲置流动起来', breadcrumb: {} }}
            extra={
                isMobile ? undefined : (
                    <Button type="primary" onClick={() => history.push('/exchange/publish')}>
                        发布物品
                    </Button>
                )
            }
        >
            <Seo
                title="交换广场"
                description="邻里二手交换广场：以物换物，按类目、成色与交付方式筛选邻居的闲置。"
                canonicalPath="/exchange/square"
            />
            <SeoHeading>换物广场</SeoHeading>
            {filterBar}
            {/* 为你推荐：向量召回，空态/降级整条隐藏（数据不足 4 条也照常展示） */}
            {recs.length > 0 && (
                <Card title="为你推荐" style={{ marginBottom: 16 }}>
                    <div className={styles.recStrip}>{recs.map(renderRecCard)}</div>
                </Card>
            )}
            {loading && list.length === 0 ? (
                <Spin style={{ display: 'block', margin: '60px auto' }} />
            ) : list.length === 0 ? (
                <Empty description="广场还没有可交换的物品" style={{ padding: '40px 0' }}>
                    <Button type="primary" onClick={() => history.push('/exchange/publish')}>
                        发布第一件物品
                    </Button>
                </Empty>
            ) : (
                <>
                    <div className={styles.grid}>{list.map(renderCard)}</div>
                    <div style={{ marginTop: 16, textAlign: 'right' }}>
                        <Pagination
                            current={pageNum}
                            pageSize={pageSize}
                            total={total}
                            showSizeChanger={false}
                            showTotal={(t) => `共 ${t} 件`}
                            onChange={(p) => setPageNum(p)}
                        />
                    </div>
                </>
            )}
        </PageContainer>
    );
};

export default Square;
