import ProductCard from '@/components/marketplace/ProductCard';
import SkeletonCards from '@/components/marketplace/SkeletonCards';
import {
    dictCategoryTree,
    dictCities,
    productSearch,
    searchHistory,
    searchHistoryClear,
    searchHot,
} from '@/services/marketplace/api';
import { SearchOutlined } from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import Seo from '@/components/Seo';
import SeoHeading from '@/components/Seo/Heading';
import { history, useRequest, useSearchParams } from '@umijs/max';
import { Button, Col, Empty, Input, Row, Select, Tag, message } from 'antd';
import { createStyles } from 'antd-style';
import React, { useEffect, useState } from 'react';
import { marketplaceTokens } from './styles/tokens';

const useStyles = createStyles(({ css }) => ({
    filterBar: css`
        background: ${marketplaceTokens.color.white};
        border: 1px solid ${marketplaceTokens.color.line};
        border-radius: ${marketplaceTokens.radius.md}px;
        padding: 14px 16px;
        margin-bottom: 20px;
        display: flex;
        align-items: center;
        gap: 10px;
        flex-wrap: wrap;
        box-shadow: ${marketplaceTokens.shadow.card};

        @media (max-width: 767px) {
            padding: 10px 12px;
            gap: 8px;

            // 搜索框独占一行
            .search-input {
                flex: 1 1 100% !important;
                width: auto !important;
            }

            // 下拉条件两列排布，覆盖固定宽度
            .ant-select {
                flex: 1 1 calc(50% - 4px) !important;
                width: auto !important;
                min-width: 0 !important;
            }

            .result-count {
                margin-left: 0;
            }
        }
    `,
    resultCount: css`
        margin-left: auto;
        font-size: 13px;
        color: ${marketplaceTokens.color.sub};
        b {
            color: ${marketplaceTokens.color.primary};
            font-size: 16px;
        }
    `,
    trendBar: css`
        display: flex;
        flex-direction: column;
        gap: 6px;
        margin-bottom: 16px;
    `,
    trendGroup: css`
        display: flex;
        align-items: center;
        flex-wrap: wrap;
        gap: 2px;
    `,
    trendLabel: css`
        font-size: 12px;
        color: ${marketplaceTokens.color.mute};
        margin-right: 6px;
        flex-shrink: 0;
    `,
}));

const Search: React.FC = () => {
    const { styles } = useStyles();
    const [params] = useSearchParams();
    const [q, setQ] = useState(params.get('q') || '');
    const [qInput, setQInput] = useState(params.get('q') || '');
    const [catL1, setCatL1] = useState<string | undefined>();
    const [city, setCity] = useState<string | undefined>();
    const [orderBy, setOrderBy] = useState('default');

    const { data: catResp } = useRequest(dictCategoryTree);
    const cats: any[] = ((catResp as any)?.data ?? (catResp as any)) || [];
    const { data: cityResp } = useRequest(dictCities);
    const cities: any[] = ((cityResp as any)?.data ?? (cityResp as any)) || [];

    // 分页累加（加载更多）
    const PAGE_SIZE = 24;
    const [page, setPage] = useState(1);
    const [pages, setPages] = useState<any[][]>([]);
    const [hasMore, setHasMore] = useState(true);

    const { data, loading } = useRequest(
        () => productSearch({ q, catL1, city, orderBy, current: page, pageSize: PAGE_SIZE }),
        { refreshDeps: [q, catL1, city, orderBy, page] },
    );
    const resp: any = data;
    const payload = resp?.data ?? resp;
    const total = payload?.total || 0;
    const pageRecords: any[] = payload?.records || [];

    useEffect(() => {
        setPages([]);
        setPage(1);
        setHasMore(true);
    }, [q, catL1, city, orderBy]);

    useEffect(() => {
        if (!data) return;
        setPages((prev) => {
            const base = prev.slice(0, page - 1);
            return [...base, pageRecords];
        });
        setHasMore(pageRecords.length >= PAGE_SIZE);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [data]);

    const list: any[] = pages.flat();
    const loadMore = () => {
        if (!loading && hasMore) setPage((p) => p + 1);
    };

    // 热搜榜 + 个人搜索历史（匿名为空）
    const { data: hotResp } = useRequest(searchHot);
    const hotList: any[] = ((hotResp as any)?.data ?? (hotResp as any)) || [];
    const { data: hisResp, refresh: refreshHistory } = useRequest(searchHistory);
    const historyList: string[] = ((hisResp as any)?.data ?? (hisResp as any)) || [];

    const applyKeyword = (kw: string) => {
        setQ(kw);
        setQInput(kw);
    };
    const clearHistory = async () => {
        try {
            await searchHistoryClear();
            message.success('已清空搜索历史');
            refreshHistory();
        }
        catch {
            message.warning('清空失败，请稍后再试');
        }
    };

    return (
        <PageContainer header={{ title: '搜索商品', breadcrumb: {} }}>
            <Seo
                title={`搜索「${q || '全部'}」`}
                description="在邻里二手搜索同城闲置商品，支持关键词、类目与城市筛选。"
                canonicalPath="/search"
                noindex
            />
            <SeoHeading>{`搜索「${q || '全部'}」`}</SeoHeading>
            <div className={`${styles.filterBar} twt-fade-up`}>
                <Input
                    className="search-input"
                    placeholder="搜索关键字"
                    prefix={<SearchOutlined />}
                    style={{ width: 260 }}
                    value={qInput}
                    onChange={(e) => setQInput(e.target.value)}
                    onPressEnter={(e: any) => setQ(e.target.value)}
                    allowClear
                />
                <Select
                    placeholder="品类"
                    allowClear
                    style={{ width: 140 }}
                    value={catL1}
                    onChange={setCatL1}
                    options={cats.map((c: any) => ({ label: c.name, value: c.code }))}
                />
                <Select
                    placeholder="城市"
                    allowClear
                    style={{ width: 120 }}
                    value={city}
                    onChange={setCity}
                    options={cities.map((c: string) => ({ label: c, value: c }))}
                />
                <Select
                    style={{ width: 140 }}
                    value={orderBy}
                    onChange={setOrderBy}
                    options={[
                        { label: '综合排序', value: 'default' },
                        { label: '相关度优先', value: 'relevance' },
                        { label: '价格从低到高', value: 'price_asc' },
                        { label: '价格从高到低', value: 'price_desc' },
                        { label: '最多浏览', value: 'hot' },
                    ]}
                />
                {(q || catL1 || city) && (
                    <Button
                        type="text"
                        size="small"
                        onClick={() => {
                            setQ('');
                            setQInput('');
                            setCatL1(undefined);
                            setCity(undefined);
                            setOrderBy('default');
                        }}
                    >
                        清空条件
                    </Button>
                )}
                <span className={styles.resultCount}>
                    共 <b>{total}</b> 件
                </span>
            </div>
            {/* 热搜榜 + 个人搜索历史（匿名为空） */}            {(hotList.length > 0 || historyList.length > 0) && (
                <div className={styles.trendBar}>
                    {historyList.length > 0 && (
                        <span className={styles.trendGroup}>
                            <span className={styles.trendLabel}>搜索历史</span>
                            {historyList.map((kw: string) => (
                                <Tag
                                    key={`h-${kw}`}
                                    style={{ cursor: 'pointer', marginInlineEnd: 6 }}
                                    onClick={() => applyKeyword(kw)}
                                >
                                    {kw}
                                </Tag>
                            ))}
                            <Button type="link" size="small" onClick={clearHistory}>
                                清空
                            </Button>
                        </span>
                    )}
                    {hotList.length > 0 && (
                        <span className={styles.trendGroup}>
                            <span className={styles.trendLabel}>🔥 热搜</span>
                            {hotList.map((h: any, i: number) => (
                                <Tag
                                    key={`hot-${h.keyword}`}
                                    color={i < 3 ? 'volcano' : undefined}
                                    style={{ cursor: 'pointer', marginInlineEnd: 6 }}
                                    onClick={() => applyKeyword(h.keyword)}
                                >
                                    {i < 3 ? `${i + 1}. ` : ''}
                                    {h.keyword}
                                </Tag>
                            ))}
                        </span>
                    )}
                </div>
            )}
            {loading ? (
                <SkeletonCards count={12} />
            ) : list.length === 0 ? (
                <Empty
                    description={q ? `没有找到与「${q}」相关的商品，换个关键字试试` : '输入关键字开始寻找闲置好物'}
                    style={{ padding: '60px 0' }}
                />
            ) : (
                <Row gutter={[16, 16]}>
                    {list.map((p: any, idx: number) => (
                        <Col
                            key={p.productId}
                            xs={12}
                            sm={8}
                            md={6}
                            lg={6}
                            xl={4}
                            className="twt-fade-up"
                            style={{ animationDelay: `${Math.min(idx, 12) * 45}ms` }}
                        >
                            <ProductCard
                                image={p.cover}
                                title={p.title}
                                price={p.price}
                                originalPrice={p.originalPrice}
                                condition={p.condition}
                                sellerName={p.sellerNickName}
                                city={p.city}
                                onClick={() => history.push(`/product/detail/${p.productId}`)}
                            />
                        </Col>
                    ))}
                </Row>
            )}
            {!loading && list.length > 0 && (hasMore ? (
                <Button block type="text" onClick={loadMore} loading={loading} style={{ marginTop: 8 }}>
                    {loading ? '正在加载…' : '加载更多'}
                </Button>
            ) : (
                <div style={{ textAlign: 'center', color: marketplaceTokens.color.mute, fontSize: 12, padding: '8px 0' }}>
                    — 没有更多结果了 —
                </div>
            ))}
        </PageContainer>
    );
};

export default Search;
