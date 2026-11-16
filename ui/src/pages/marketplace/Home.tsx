import { useGsapReveal } from '@/hooks/useGsapReveal';
import { useGSAP } from '@gsap/react';
import InterestGuideModal, {
    INTEREST_INIT_FLAG,
} from '@/components/marketplace/InterestGuideModal';
import HomeWidget from '@/components/marketplace/HomeWidget';
import ProductCard from '@/components/marketplace/ProductCard';
import EmptyState from '@/components/marketplace/EmptyState';
import {
    interestDislike,
    ipCity,
    productFollow,
    productNearby,
    productRecommend,
} from '@/services/marketplace/api';
import { history, useModel, useRequest } from '@umijs/max';
import { AutoComplete, Button, message } from 'antd';
import { createStyles } from 'antd-style';
import { gsap } from 'gsap';
import React, { useEffect, useRef, useState } from 'react';
import { useIsMobile } from '@/hooks/useIsMobile';
import Seo from '@/components/Seo';
import { requireLogin } from '@/utils/loginGuard';
import { shouldSendDislike } from './guestActions';
import { buildOrganizationJsonLd, buildWebSiteJsonLd, resolveSiteUrl } from '@/utils/seo';
import { marketplaceTokens } from './styles/tokens';

const useStyles = createStyles(() => ({
    page: {
        position: 'relative',
        zIndex: 10,
        padding: '20px 16px',
        background: 'transparent',
        '@media (min-width: 768px)': {
            padding: '24px 24px',
        },
    },
    // Hero 行：左侧标题，右侧「今日好心情」小组件（窄屏自动堆叠）
    heroWrap: {
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: 24,
        '@media (max-width: 899px)': {
            flexDirection: 'column',
            alignItems: 'stretch',
            gap: 16,
        },
    },
    hero: {
        padding: '32px 0 24px',
        display: 'flex',
        flexDirection: 'column',
        gap: 8,
        flex: 1,
        minWidth: 0,
        '@media (max-width: 899px)': {
            padding: '28px 0 0',
        },
    },
    heroSide: {
        flexShrink: 0,
        '@media (max-width: 899px)': {
            width: '100%',
        },
    },
    heroEyebrow: {
        fontSize: 12,
        color: marketplaceTokens.color.primary,
        letterSpacing: 2,
        textTransform: 'uppercase',
        fontWeight: 600,
    },
    heroTitle: {
        fontSize: 'clamp(32px, 4.5vw, 48px)',
        fontWeight: 700,
        color: marketplaceTokens.color.ink,
        margin: 0,
        lineHeight: 1.15,
        fontFamily: marketplaceTokens.font.sans,
    },
    heroSub: {
        fontSize: 15,
        color: marketplaceTokens.color.sub,
        margin: 0,
        marginTop: 4,
    },
    cityRow: {
        display: 'flex',
        alignItems: 'center',
        gap: 6,
        marginTop: 8,
        fontSize: 14,
        color: marketplaceTokens.color.mute,
    },
    // 城市选择器：无边框、继承城市行的字体与颜色，交互上加下划线提示可编辑
    citySelect: {
        fontSize: 14,
        color: marketplaceTokens.color.mute,
        '& .ant-select-selection-search-input': {
            fontSize: 14,
            color: marketplaceTokens.color.mute,
        },
        '& .ant-select-selection-placeholder': {
            fontSize: 14,
            color: marketplaceTokens.color.mute,
        },
        '& .ant-select-selector': {
            padding: '0 4px !important',
            borderBottom: '1px dashed transparent',
            transition: `border-color ${marketplaceTokens.motion.fast}s ease`,
        },
        '&:hover .ant-select-selector': {
            borderBottomColor: `${marketplaceTokens.color.line} !important`,
        },
    },
    relocateBtn: {
        fontSize: 12,
        color: marketplaceTokens.color.sub,
        background: 'transparent',
        border: 'none',
        padding: '2px 4px',
        cursor: 'pointer',
        fontFamily: 'inherit',
        '&:hover': {
            color: marketplaceTokens.color.primary,
        },
        '&:disabled': {
            cursor: 'default',
            color: marketplaceTokens.color.mute,
        },
    },
    tabBar: {
        display: 'flex',
        gap: 8,
        margin: '24px 0 16px',
        flexWrap: 'wrap',
    },
    tab: {
        padding: '8px 18px',
        fontSize: 14,
        fontWeight: 500,
        color: marketplaceTokens.color.sub,
        background: 'transparent',
        border: `1px solid ${marketplaceTokens.color.line}`,
        borderRadius: marketplaceTokens.radius.pill,
        cursor: 'pointer',
        transition: `all ${marketplaceTokens.motion.base}s cubic-bezier(0.4, 0, 0.2, 1)`,
        fontFamily: 'inherit',
        '&:hover': {
            color: marketplaceTokens.color.primary,
            borderColor: marketplaceTokens.color.primary,
        },
    },
    tabActive: {
        color: marketplaceTokens.color.white,
        background: marketplaceTokens.color.primary,
        borderColor: marketplaceTokens.color.primary,
        '&:hover': {
            color: marketplaceTokens.color.white,
            background: marketplaceTokens.color.primaryHover,
            borderColor: marketplaceTokens.color.primaryHover,
        },
    },
}));

const TABS = [
    { key: 'recommend', label: '推荐' },
    { key: 'nearby', label: '📍 同城 · 深圳' },
    { key: 'follow', label: '关注' },
] as const;

// ── 首页城市定位 ─────────────────────────────
// 优先级：手动选择（localStorage 记忆）> IP 归属地 > 默认城市。
// 注意：不再取「默认收货地址」的城市 —— 收货地址是「收货」概念，与浏览者所在地无关
//（历史缺陷：测试地址填了南京，首页就一直显示南京）。
// 自动定位走 IP 归属地（/ai/assist/ip-city）：生产站点为 HTTP，浏览器
// Geolocation API 在非安全上下文不可用，IP 定位是当前唯一可行的自动方案。
const CITY_STORAGE_KEY = 'twt_home_city';
const DEFAULT_CITY = '深圳';
const HOT_CITIES = [
    '深圳', '郑州', '北京', '上海', '广州', '杭州',
    '成都', '武汉', '西安', '重庆', '南京', '长沙',
];

const PAGE_SIZE = 20;

const Home: React.FC = () => {
    const { styles } = useStyles();
    const isMobile = useIsMobile();
    const [tab, setTab] = useState<'recommend' | 'nearby' | 'follow'>('recommend');
    const fetcher =
        tab === 'recommend' ? productRecommend : tab === 'nearby' ? productNearby : productFollow;

    // 分页累加（加载更多）：pages 按页缓存，翻页失败/重取按页替换
    const [page, setPage] = useState(1);
    const [pages, setPages] = useState<any[][]>([]);
    const [hasMore, setHasMore] = useState(true);
    const { data, loading } = useRequest(() => fetcher({ current: page, pageSize: PAGE_SIZE }), {
        refreshDeps: [tab, page],
    });
    const resp: any = data;
    const records: any[] = resp?.records || resp?.data?.records || [];

    useEffect(() => {
        setPages([]);
        setPage(1);
        setHasMore(true);
    }, [tab]);

    useEffect(() => {
        if (!data) return;
        setPages((prev) => {
            const base = prev.slice(0, page - 1);
            return [...base, records];
        });
        setHasMore(records.length >= PAGE_SIZE);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [data]);

    const list = pages.flat();
    const loadMore = () => {
        if (!loading && hasMore) setPage((p) => p + 1);
    };

    // 同城标签/天气组件城市：手动选择 > IP 归属地 > 默认深圳（逻辑见顶部 CITY_STORAGE_KEY 注释）
    const { initialState } = useModel('@@initialState');
    const loggedIn = !!(initialState as any)?.currentUser?.user?.userId;
    const [cityName, setCityName] = useState<string>(
        () => localStorage.getItem(CITY_STORAGE_KEY) || '',
    );
    const [locating, setLocating] = useState(false);

    const detectCity = (silent = false) => {
        if (locating) return;
        setLocating(true);
        ipCity()
            .then((resp: any) => {
                const city: string | null = (resp?.data ?? resp)?.city || null;
                setCityName((prev) => (prev ? prev : city || DEFAULT_CITY));
                if (!silent && !city) {
                    message.info('未能识别你的城市，已展示默认城市');
                }
            })
            .catch(() => {
                setCityName((prev) => prev || DEFAULT_CITY);
                if (!silent) message.warning('定位失败，请检查网络后重试');
            })
            .finally(() => setLocating(false));
    };

    // 首次进入且无手动选择时，静默 IP 定位一次
    useEffect(() => {
        if (!localStorage.getItem(CITY_STORAGE_KEY)) {
            detectCity(true);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    // 手动切换城市：写入 localStorage，此后优先级最高（重新定位才清除）
    const handleSelectCity = (value: string) => {
        const v = value.trim().slice(0, 20);
        if (!v) return;
        localStorage.setItem(CITY_STORAGE_KEY, v);
        setCityName(v);
    };

    const handleRelocate = () => {
        localStorage.removeItem(CITY_STORAGE_KEY);
        // 先清空当前值：detectCity 的写入口径是「已有值不覆盖」，不清会一直显示旧城市
        setCityName('');
        detectCity();
    };

    // 负反馈：点"不感兴趣"后本地移除该卡片（服务端已记录降权）
    const [hiddenIds, setHiddenIds] = useState<Set<number>>(new Set());
    const visibleList = list.filter((p: any) => !hiddenIds.has(p.productId));
    const handleDislike = (productId: number) => {
        // 游客只在本机隐藏卡片并引导登录，不发请求（该接口需登录）
        if (!shouldSendDislike({ loggedIn })) {
            requireLogin();
            return;
        }
        setHiddenIds((prev) => new Set(prev).add(productId));
        interestDislike(productId)
            .then(() => message.success('已减少此类内容推荐'))
            .catch(() => message.warning('操作未保存成功'));
    };

    // 新用户兴趣引导：登录用户 + 本机未完成过引导时弹一次
    const [guideOpen, setGuideOpen] = useState(false);
    useEffect(() => {
        if (loggedIn && !localStorage.getItem(INTEREST_INIT_FLAG)) {
            setGuideOpen(true);
        }
    }, [loggedIn]);
    const handleGuideDone = () => {
        localStorage.setItem(INTEREST_INIT_FLAG, '1');
        setGuideOpen(false);
    };

    // Hero 入场 —— 大标题、副标题、城市行依次淡入上移
    const heroRef = useRef<HTMLDivElement>(null);
    useGSAP(
        () => {
            const mm = gsap.matchMedia();
            mm.add('(prefers-reduced-motion: no-preference)', () => {
                if (!heroRef.current) return;
                gsap.from(heroRef.current.children, {
                    y: 16,
                    opacity: 0,
                    duration: 0.55,
                    stagger: 0.08,
                    ease: 'power2.out',
                });
            });
            mm.add('(prefers-reduced-motion: reduce)', () => {
                if (heroRef.current) {
                    gsap.set(heroRef.current.children, { clearProps: 'all' });
                }
            });
            return () => mm.revert();
        },
        { scope: heroRef },
    );

    // 卡片网格 stagger 入场 —— 仅切 tab 时重放；"加载更多"追加静默（避免整网格反复重动画）
    const gridRef = useGsapReveal<HTMLDivElement>({
        stagger: 0.05,
        duration: 0.45,
        y: 20,
        selector: '.product-reveal-item',
        dependencies: [tab],
    });

    const siteUrl = resolveSiteUrl(process.env.UMI_APP_SITE_URL, window.location.origin);
    // 用 initialState 的登录态，不用 token：过期 token 仍在 localStorage 里，
    // isLoggedIn() 会误判为已登录，进而让「关注」Tab 对已掉线的游客可见。
    const visibleTabs = loggedIn ? TABS : TABS.filter((t) => t.key !== 'follow');

    return (
        <div className={styles.page}>
            <Seo
                title="邻里二手 - 同城闲置交易与互助"
                description="邻里二手是同城闲置交易与互助平台：发布二手商品、以物换物、社区动态与邻里互助。"
                canonicalPath="/home"
                jsonLd={[buildWebSiteJsonLd(siteUrl), buildOrganizationJsonLd(siteUrl)]}
            />
            <div>
                {/* Hero 区域 —— 左：大标题；右：今日小组件（天气 + 日期 + AI 激励语） */}
                <div className={styles.heroWrap}>
                    <div ref={heroRef} className={styles.hero}>
                        <span className={styles.heroEyebrow}>weiwei marketplace</span>
                        <h1 className={styles.heroTitle}>邻里二手</h1>
                        <p className={styles.heroSub}>社区闲置 · 安心交易</p>
                        <div className={styles.cityRow}>
                            <span>📍</span>
                            <AutoComplete
                                value={cityName || undefined}
                                options={HOT_CITIES.map((c) => ({ value: c }))}
                                variant="borderless"
                                size="small"
                                popupMatchSelectWidth={140}
                                style={{ width: 88, minWidth: 64 }}
                                className={styles.citySelect}
                                placeholder="选择城市"
                                onChange={handleSelectCity}
                                allowClear={false}
                            />
                            <span>· 找到附近的闲置好物</span>
                            <button
                                type="button"
                                className={styles.relocateBtn}
                                onClick={handleRelocate}
                                disabled={locating}
                                title="按 IP 重新定位"
                            >
                                {locating ? '定位中…' : '重新定位'}
                            </button>
                        </div>
                    </div>
                    <div className={styles.heroSide}>
                        {/* 天气与同城标签城市均按浏览器公网 IP 由后端定位，组件解析后回调回传 */}
                        <HomeWidget
                            variant="column"
                            onCityResolved={setCityName}
                        />
                    </div>
                </div>

                {/* Pill 形状 Tab（同城标签跟随当前城市：手动选择 > IP 定位 > 默认） */}
                <div className={styles.tabBar} role="tablist">
                    {visibleTabs.map((t) => (
                        <button
                            key={t.key}
                            type="button"
                            role="tab"
                            aria-selected={tab === t.key}
                            className={`${styles.tab} ${tab === t.key ? styles.tabActive : ''}`}
                            onClick={() => setTab(t.key)}
                        >
                            {t.key === 'nearby' ? `📍 同城 · ${cityName}` : t.label}
                        </button>
                    ))}
                </div>

                {loading && pages.length === 0 ? (
                    /* 首屏骨架：与下方网格同构（minmax(320px,1fr)），避免加载完成时布局跳动 */
                    <div
                        style={{
                            display: 'grid',
                            gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))',
                            gap: 16,
                        }}
                        aria-label="加载中"
                    >
                        {Array.from({ length: 8 }).map((_, i) => (
                            <div
                                key={i}
                                style={{
                                    background: marketplaceTokens.color.white,
                                    borderRadius: marketplaceTokens.radius.md,
                                    border: `1px solid ${marketplaceTokens.color.line}`,
                                    padding: 12,
                                    overflow: 'hidden',
                                }}
                            >
                                <div
                                    className="twt-shimmer"
                                    style={{ height: 200, borderRadius: 8 }}
                                />
                                <div
                                    className="twt-shimmer"
                                    style={{ height: 14, borderRadius: 4, marginTop: 12, width: '85%' }}
                                />
                                <div
                                    className="twt-shimmer"
                                    style={{ height: 14, borderRadius: 4, marginTop: 8, width: '45%' }}
                                />
                            </div>
                        ))}
                    </div>
                ) : !loading && visibleList.length === 0 ? (
                    <EmptyState
                        text={tab === 'recommend' ? '暂无推荐，去看看其他好物吧' : '暂无商品'}
                        ctaText="去发布一件闲置"
                        onCtaClick={() => history.push('/product/publish')}
                    />
                ) : (
                    <div
                        ref={gridRef}
                        style={{
                            // 手机两列瀑布式小卡（320px 下限在 375 视口会退化为单列大图）
                            display: 'grid',
                            gridTemplateColumns: isMobile
                                ? 'repeat(2, minmax(0, 1fr))'
                                : 'repeat(auto-fill, minmax(320px, 1fr))',
                            gap: isMobile ? 10 : 16,
                        }}
                    >
                        {visibleList.map((p: any) => (
                            <div key={p.productId} className="product-reveal-item">
                                <ProductCard
                                    image={p.cover}
                                    title={p.title}
                                    price={p.price}
                                    originalPrice={p.originalPrice}
                                    condition={p.condition}
                                    sellerName={p.sellerNickName}
                                    city={p.city}
                                    reason={p.reason}
                                    onDislike={
                                        tab === 'recommend'
                                            ? () => handleDislike(p.productId)
                                            : undefined
                                    }
                                    onClick={() =>
                                        history.push(`/product/detail/${p.productId}`)
                                    }
                                />
                            </div>
                        ))}
                    </div>
                )}
                {!loading || pages.length > 0 ? (
                    list.length > 0 && (hasMore ? (
                        <Button
                            block
                            type="text"
                            onClick={loadMore}
                            loading={loading}
                            style={{ marginTop: 16 }}
                        >
                            {loading ? '正在加载…' : '加载更多'}
                        </Button>
                    ) : (
                        <div
                            style={{
                                textAlign: 'center',
                                color: marketplaceTokens.color.mute,
                                fontSize: 12,
                                padding: '16px 0 0',
                            }}
                        >
                            — 到底啦，去发布一件闲置好物吧 —
                        </div>
                    ))
                ) : null}
            </div>

            {/* 新用户兴趣引导 */}
            <InterestGuideModal open={guideOpen} onDone={handleGuideDone} />
        </div>
    );
};

export default Home;
