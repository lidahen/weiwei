import { marketplaceTokens } from '@/pages/marketplace/styles/tokens';
import { homeWidget } from '@/services/marketplace/api';
import { SyncOutlined } from '@ant-design/icons';
import { useRequest } from '@umijs/max';
import { Button, Tooltip } from 'antd';
import { createStyles } from 'antd-style';
import React, { useEffect, useMemo, useRef, useState } from 'react';

/**
 * 首页「今日好心情」小组件：天气 + 日期 + AI 激励语
 *
 * 数据来自 GET /ai/assist/home-widget（游客可见，全链路静默降级）：
 * - 天气源故障 → 后端返回 weather=null → 本组件隐藏天气块；
 * - 请求失败 → 前端本地日期 + 内置兜底激励语，卡片永不空白。
 * 激励语「换一句」在后端返回的 5 条池内本地轮换，不重复请求。
 *
 * 性能：后端未命中缓存时先返回模板池（source=pending）并后台生成，
 * 本组件拿到 pending 会静默重拉一次；同时用 localStorage 缓存上次结果，
 * 二次进入时卡片 0ms 出内容，接口在后台更新。
 */

const FALLBACK_QUOTES = [
    '旧物有故事，转手即新生。',
    '让好物流转，让生活减负。',
    '今天是适合逛逛闲置的好日子。',
    '每一次出闲置，都是一次心动的接力。',
    '少买一点，多爱一点，旧物也很好。',
];

const CACHE_KEY = 'home_widget_cache_v1';
const CACHE_MAX_AGE = 30 * 60 * 1000; // 天气缓存 30 分钟内可直接使用

interface WidgetCache {
    ts: number;
    weather?: any;
    quotes?: string[];
    date?: string;
    weekday?: string;
}

function readCache(): WidgetCache | null {
    try {
        const raw = localStorage.getItem(CACHE_KEY);
        if (!raw) return null;
        const v = JSON.parse(raw);
        if (!v || typeof v !== 'object') return null;
        if (!v.ts || Date.now() - v.ts > CACHE_MAX_AGE) return null;
        return v as WidgetCache;
    } catch {
        return null;
    }
}

function writeCache(v: Omit<WidgetCache, 'ts'>) {
    try {
        localStorage.setItem(CACHE_KEY, JSON.stringify({ ...v, ts: Date.now() }));
    } catch {
        /* 隐私模式下 localStorage 不可写，忽略即可 */
    }
}

const useStyles = createStyles(() => ({
    cardRow: {
        display: 'flex',
        alignItems: 'center',
        gap: 20,
        flexWrap: 'wrap',
        padding: '16px 20px',
        marginTop: 4,
        marginBottom: 20,
        background: marketplaceTokens.color.white,
        border: `1px solid ${marketplaceTokens.color.line}`,
        borderRadius: marketplaceTokens.radius.md,
        boxShadow: marketplaceTokens.shadow.card,
        fontFamily: marketplaceTokens.font.sans,
        animation: 'homeWidgetFadeIn 0.5s ease both',
        '@media (max-width: 767px)': {
            gap: 12,
            padding: '14px 16px',
        },
        '@keyframes homeWidgetFadeIn': {
            from: { opacity: 0, transform: 'translateY(10px)' },
            to: { opacity: 1, transform: 'translateY(0)' },
        },
    },
    // 竖排变体：放在 Hero 标题右侧
    cardColumn: {
        display: 'flex',
        flexDirection: 'column',
        gap: 10,
        width: 300,
        maxWidth: '100%',
        padding: '16px 18px',
        background: marketplaceTokens.color.white,
        border: `1px solid ${marketplaceTokens.color.line}`,
        borderRadius: marketplaceTokens.radius.md,
        boxShadow: marketplaceTokens.shadow.card,
        fontFamily: marketplaceTokens.font.sans,
        animation: 'homeWidgetFadeIn 0.5s ease both',
    },
    columnTop: {
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: 12,
    },
    weather: {
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        minWidth: 150,
    },
    weatherCompact: {
        minWidth: 0,
        gap: 8,
    },
    weatherIcon: {
        fontSize: 34,
        lineHeight: 1,
    },
    weatherIconCompact: {
        fontSize: 28,
    },
    temp: {
        fontSize: 22,
        fontWeight: 700,
        color: marketplaceTokens.color.ink,
        lineHeight: 1.2,
    },
    weatherText: {
        fontSize: 12,
        color: marketplaceTokens.color.sub,
        marginTop: 2,
        whiteSpace: 'nowrap',
    },
    tempRange: {
        fontSize: 11,
        color: marketplaceTokens.color.mute,
        marginTop: 1,
    },
    divider: {
        width: 1,
        height: 38,
        background: marketplaceTokens.color.line,
        flexShrink: 0,
        '@media (max-width: 767px)': {
            display: 'none',
        },
    },
    dateSection: {
        minWidth: 104,
    },
    dateSectionCompact: {
        minWidth: 0,
        textAlign: 'right' as const,
    },
    dateBig: {
        fontSize: 17,
        fontWeight: 700,
        color: marketplaceTokens.color.ink,
        lineHeight: 1.3,
        whiteSpace: 'nowrap',
    },
    weekday: {
        fontSize: 12,
        color: marketplaceTokens.color.sub,
        marginTop: 2,
        whiteSpace: 'nowrap',
    },
    quoteSection: {
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        flex: 1,
        minWidth: '100%',
        paddingTop: 12,
        borderTop: `1px dashed ${marketplaceTokens.color.line}`,
        '@media (min-width: 768px)': {
            minWidth: 0,
            paddingTop: 0,
            borderTop: 'none',
        },
    },
    quoteSectionColumn: {
        display: 'flex',
        alignItems: 'flex-start',
        gap: 6,
        paddingTop: 10,
        borderTop: `1px dashed ${marketplaceTokens.color.line}`,
    },
    quoteMark: {
        fontSize: 26,
        fontWeight: 700,
        color: marketplaceTokens.color.primary,
        lineHeight: 0.6,
        transform: 'translateY(6px)',
        flexShrink: 0,
    },
    quoteText: {
        flex: 1,
        fontSize: 13,
        color: marketplaceTokens.color.sub,
        fontStyle: 'italic',
        lineHeight: 1.6,
        transition: `opacity ${marketplaceTokens.motion.fast}s ease`,
    },
    refreshBtn: {
        flexShrink: 0,
        fontSize: 12,
        color: marketplaceTokens.color.mute,
        '&:hover': {
            color: marketplaceTokens.color.primary,
        },
    },
    refreshIconBtn: {
        flexShrink: 0,
        marginTop: -2,
    },
}));

const HomeWidget: React.FC<{
    city?: string;
    variant?: 'row' | 'column';
    /** 后端按 IP 解析出的城市名回传给父组件（首页同城标签复用同一来源） */
    onCityResolved?: (city: string) => void;
}> = ({ city, variant = 'row', onCityResolved }) => {
    const { styles, cx } = useStyles();
    const [quoteIdx, setQuoteIdx] = useState(0);
    // 上次结果：首次渲染即可出内容，避免刷新页面时空白等待
    const [cached] = useState<WidgetCache | null>(() => readCache());

    // 城市来源：显式 city 优先（地址城市场景）；未传时由后端定位
    // （生产取 X-Forwarded-For 客户端 IP，本地开发取服务端出口 IP，见 home_widget.py）
    const { data, loading, run } = useRequest(() => homeWidget({ city }), {
        refreshDeps: [city],
    });

    const resp: any = data;
    const widget = resp?.data ?? resp ?? {};
    const source: string = widget.source || '';

    // 后端解析出的城市名（IP 定位结果）回传父组件；仅在值变化时回调一次
    const resolvedCity: string = widget.city || '';
    useEffect(() => {
        if (resolvedCity && onCityResolved) {
            onCityResolved(resolvedCity);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [resolvedCity]);

    // 接口值优先，缺失时回落到本地缓存（首屏永不空白）
    const weather = widget.weather ?? cached?.weather ?? null;
    const quotes: string[] =
        (Array.isArray(widget.quotes) && widget.quotes.length > 0 && widget.quotes) ||
        (Array.isArray(cached?.quotes) && cached.quotes.length > 0 && cached.quotes) ||
        FALLBACK_QUOTES;
    const activeQuote = quotes[quoteIdx % quotes.length];

    // 日期：优先后端（固定北京时间口径），请求未就绪时用浏览器本地日期兜底
    const dateInfo = useMemo(() => {
        const src: string | undefined = widget.date || cached?.date;
        if (src) {
            const [y, m, d] = src.split('-').map((n: string) => parseInt(n, 10));
            return { text: `${m}月${d}日`, year: String(y) };
        }
        const now = new Date();
        return {
            text: `${now.getMonth() + 1}月${now.getDate()}日`,
            year: String(now.getFullYear()),
        };
    }, [widget.date, cached?.date]);
    const weekday: string =
        widget.weekday ||
        cached?.weekday ||
        ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六'][new Date().getDay()];

    // 命中新数据后落地缓存，供下次进入秒出
    useEffect(() => {
        if (!data) return;
        writeCache({
            weather: widget.weather ?? cached?.weather,
            quotes:
                Array.isArray(widget.quotes) && widget.quotes.length
                    ? widget.quotes
                    : cached?.quotes,
            date: widget.date,
            weekday: widget.weekday,
        });
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [data]);

    // 激励语池换了一批（模板池 → AI 版本）时从头展示
    useEffect(() => {
        setQuoteIdx(0);
    }, [quotes[0]]);

    // 后端后台生成中（source=pending）或天气冷启动缺失 → 静默重拉，最多 2 次
    const retries = useRef(0);
    useEffect(() => {
        if (!data) return;
        const needMore = source === 'pending' || (!weather && !cached?.weather);
        if (!needMore || retries.current >= 2) return;
        retries.current += 1;
        const timer = setTimeout(() => run(), 2500);
        return () => clearTimeout(timer);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [data, source]);

    const nextQuote = () => setQuoteIdx((i) => (i + 1) % quotes.length);

    const isColumn = variant === 'column';
    const cardClass = isColumn ? styles.cardColumn : styles.cardRow;

    // 天气块：有数据直接渲染；无数据且首次加载中 → 小块骨架；否则隐藏
    const weatherBlock = weather ? (
        <div className={cx(styles.weather, isColumn && styles.weatherCompact)}>
            <span
                className={cx(styles.weatherIcon, isColumn && styles.weatherIconCompact)}
                role="img"
                aria-label={weather.text}
            >
                {weather.icon}
            </span>
            <div>
                <div className={styles.temp}>
                    {weather.temp !== null && weather.temp !== undefined
                        ? `${Math.round(weather.temp)}°`
                        : weather.text}
                </div>
                <div className={styles.weatherText}>
                    {weather.text} · {weather.city || city}
                </div>
                {!isColumn &&
                    weather.tempMin !== null &&
                    weather.tempMin !== undefined &&
                    weather.tempMax !== null &&
                    weather.tempMax !== undefined && (
                        <div className={styles.tempRange}>
                            {Math.round(weather.tempMin)}~{Math.round(weather.tempMax)}°C
                        </div>
                    )}
            </div>
        </div>
    ) : loading && !cached ? (
        <div className="twt-shimmer" style={{ height: 40, width: 120, borderRadius: 8 }} />
    ) : null;

    const dateBlock = (
        <div className={cx(styles.dateSection, isColumn && styles.dateSectionCompact)}>
            <div className={styles.dateBig}>{dateInfo.text}</div>
            <div className={styles.weekday}>
                {weekday} · {dateInfo.year}
            </div>
        </div>
    );

    const quoteBlock = (
        <div className={isColumn ? styles.quoteSectionColumn : styles.quoteSection}>
            <span className={styles.quoteMark}>“</span>
            <span key={quoteIdx % quotes.length} className={styles.quoteText}>
                {activeQuote}
            </span>
            <Tooltip title="换一句 AI 激励语">
                {isColumn ? (
                    <Button
                        type="text"
                        size="small"
                        className={cx(styles.refreshBtn, styles.refreshIconBtn)}
                        icon={<SyncOutlined spin={loading} />}
                        onClick={nextQuote}
                    />
                ) : (
                    <Button
                        type="text"
                        size="small"
                        className={styles.refreshBtn}
                        icon={<SyncOutlined spin={loading} />}
                        onClick={nextQuote}
                    >
                        换一句
                    </Button>
                )}
            </Tooltip>
        </div>
    );

    if (isColumn) {
        return (
            <div className={cardClass}>
                <div className={styles.columnTop}>
                    {weatherBlock}
                    {dateBlock}
                </div>
                {quoteBlock}
            </div>
        );
    }

    return (
        <div className={cardClass}>
            {weatherBlock}
            {weather && <div className={styles.divider} />}
            {dateBlock}
            <div className={styles.divider} />
            {quoteBlock}
        </div>
    );
};

export default HomeWidget;
