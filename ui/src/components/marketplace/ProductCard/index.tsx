import { Card, Tag } from 'antd';
import { createStyles } from 'antd-style';
import { gsap } from 'gsap';
import TWT from '@/setting';
import { IMG_FALLBACK } from '@/pages/marketplace/utils';
import React, { useEffect, useRef, useState } from 'react';
import { marketplaceTokens } from '@/pages/marketplace/styles/tokens';

export interface ProductCardProps {
    image: string;
    title: string;
    price: number;
    originalPrice?: number;
    condition?: string;
    sellerName?: string;
    city?: string;
    /** 推荐理由（首页推荐流附带，如"你最近在看「数码」"） */
    reason?: string;
    /** 负反馈"不感兴趣"回调（传入时卡片右上角显示 ✕ 按钮） */
    onDislike?: () => void;
    onClick?: () => void;
}

const conditionMap: Record<string, string> = {
    brand_new: '全新',
    '99': '99新',
    '95': '95新',
    '90': '9成新',
    '80': '8成新',
    '70_lower': '7成及以下',
};

const useStyles = createStyles(() => ({
    // 3D tilt 外层（perspective）
    tilt: {
        perspective: 1000,
        cursor: 'pointer',
        willChange: 'transform',
    },
    // antd Card 上的 hover 抬升
    card: {
        borderRadius: `${marketplaceTokens.radius.md}px !important`,
        overflow: 'hidden',
        background: marketplaceTokens.color.white,
        border: `1px solid ${marketplaceTokens.color.line} !important`,
        boxShadow: `${marketplaceTokens.shadow.card} !important`,
        transition: `transform ${marketplaceTokens.motion.base}s cubic-bezier(0.4, 0, 0.2, 1), box-shadow ${marketplaceTokens.motion.base}s cubic-bezier(0.4, 0, 0.2, 1)`,
        willChange: 'transform',
    },
    coverWrap: {
        overflow: 'hidden',
    },
    cover: {
        height: 200,
        objectFit: 'cover',
        display: 'block',
        width: '100%',
        transition: `transform ${marketplaceTokens.motion.slow}s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.4s ease`,
        // 手机两列小卡时封面收窄，避免图大字小失衡
        '@media (max-width: 767px)': {
            height: 140,
        },
    },
    body: {
        padding: 12,
    },
    title: {
        fontSize: 14,
        lineHeight: '20px',
        height: 40,
        overflow: 'hidden',
        display: '-webkit-box',
        WebkitLineClamp: 2,
        WebkitBoxOrient: 'vertical',
        color: marketplaceTokens.color.ink,
        fontWeight: 500,
    },
    priceRow: {
        marginTop: 8,
    },
    price: {
        color: marketplaceTokens.color.primary,
        fontSize: 18,
        fontWeight: 700,
        '@media (max-width: 767px)': {
            fontSize: 16,
        },
    },
    originalPrice: {
        color: marketplaceTokens.color.mute,
        fontSize: 12,
        marginLeft: 8,
        textDecoration: 'line-through',
    },
    meta: {
        marginTop: 8,
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        fontSize: 12,
        color: marketplaceTokens.color.sub,
        '@media (max-width: 767px)': {
            marginTop: 6,
            fontSize: 11,
        },
    },
    seller: {
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        whiteSpace: 'nowrap',
        flex: 1,
    },
    city: {
        marginLeft: 8,
        flexShrink: 0,
        // 移动端（复查报告 P1-4）：允许城市收缩省略，杜绝贴边裁切
        '@media (max-width: 767px)': {
            marginLeft: 6,
            flexShrink: 1,
            minWidth: 0,
            maxWidth: 72,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
        },
    },
    conditionTag: {
        margin: 0,
        background: marketplaceTokens.color.primarySoft,
        border: 'none',
        color: marketplaceTokens.color.primary,
        borderRadius: marketplaceTokens.radius.pill,
        fontSize: 11,
    },
    reasonTag: {
        position: 'absolute',
        top: 8,
        left: 8,
        maxWidth: 'calc(100% - 44px)',
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        whiteSpace: 'nowrap',
        padding: '2px 8px',
        fontSize: 11,
        lineHeight: '16px',
        color: marketplaceTokens.color.primary,
        background: 'rgba(255, 255, 255, 0.92)',
        borderRadius: marketplaceTokens.radius.pill,
        backdropFilter: 'blur(4px)',
        pointerEvents: 'none',
    },
    dislikeBtn: {
        position: 'absolute',
        top: 6,
        right: 6,
        width: 22,
        height: 22,
        lineHeight: '20px',
        textAlign: 'center',
        borderRadius: '50%',
        fontSize: 12,
        color: marketplaceTokens.color.mute,
        background: 'rgba(255, 255, 255, 0.92)',
        border: 'none',
        cursor: 'pointer',
        backdropFilter: 'blur(4px)',
        transition: `all ${marketplaceTokens.motion.base}s`,
        '&:hover': {
            color: marketplaceTokens.color.primary,
            transform: 'scale(1.1)',
        },
    },
}));

const ProductCard: React.FC<ProductCardProps> = ({
    image,
    title,
    price,
    originalPrice,
    condition,
    sellerName,
    city,
    reason,
    onDislike,
    onClick,
}) => {
    const { styles } = useStyles();
    const tiltRef = useRef<HTMLDivElement>(null);
    const coverImgRef = useRef<HTMLImageElement>(null);
    // 封面淡入：缓存图可能先于 onLoad 完成加载，effect 里补判 complete
    const [imgLoaded, setImgLoaded] = useState(false);
    useEffect(() => {
        const img = coverImgRef.current;
        if (img && img.complete && img.naturalWidth > 0) {
            setImgLoaded(true);
        }
    }, []);
    const markImgLoaded = () => setImgLoaded(true);

    // 3D tilt —— 鼠标位置驱动 rotateX/rotateY（max ±6deg）
    // 用 useEffect 而非 useGSAP：addEventListener 在持续事件场景下不被 useGSAP 正确管理
    // 用 gsap.to + overwrite:auto 而非 gsap.quickTo：quickTo 首次 tween 在该环境下不启动
    // gsap.matchMedia：prefers-reduced-motion 用户不启用装饰性 tilt，仅保留 CSS hover 反馈
    useEffect(() => {
        if (!tiltRef.current) return;
        const el = tiltRef.current;
        const mm = gsap.matchMedia();
        mm.add('(prefers-reduced-motion: no-preference)', () => {
            const setRotateY = (v: number) =>
                gsap.to(el, { rotateY: v, duration: 0.4, ease: 'power3.out', overwrite: 'auto' });
            const setRotateX = (v: number) =>
                gsap.to(el, { rotateX: v, duration: 0.4, ease: 'power3.out', overwrite: 'auto' });
            const setScale = (v: number) =>
                gsap.to(el, { scale: v, duration: 0.4, ease: 'power3.out', overwrite: 'auto' });

            const onMove = (e: MouseEvent) => {
                const rect = el.getBoundingClientRect();
                const x = (e.clientX - rect.left) / rect.width;
                const y = (e.clientY - rect.top) / rect.height;
                setRotateY((x - 0.5) * 12);
                setRotateX(-(y - 0.5) * 12);
                setScale(1.02);
            };
            const onEnter = () => setScale(1.02);
            const onLeave = () => {
                setRotateY(0);
                setRotateX(0);
                setScale(1);
            };

            el.addEventListener('mouseenter', onEnter);
            el.addEventListener('mousemove', onMove);
            el.addEventListener('mouseleave', onLeave);
            return () => {
                el.removeEventListener('mouseenter', onEnter);
                el.removeEventListener('mousemove', onMove);
                el.removeEventListener('mouseleave', onLeave);
            };
        });
        return () => mm.revert();
    }, []);

    // hover 抬升 + 封面缩放 —— 用 CSS sibling 选择器
    const handleMouseEnter = (e: React.MouseEvent<HTMLDivElement>) => {
        const card = e.currentTarget.querySelector(`.${styles.card}`) as HTMLElement | null;
        const cover = e.currentTarget.querySelector(`.${styles.cover}`) as HTMLElement | null;
        if (card) {
            card.style.transform = 'translateY(-6px)';
            card.style.boxShadow = marketplaceTokens.shadow.cardHover;
        }
        if (cover) cover.style.transform = 'scale(1.05)';
    };
    const handleMouseLeave = (e: React.MouseEvent<HTMLDivElement>) => {
        const card = e.currentTarget.querySelector(`.${styles.card}`) as HTMLElement | null;
        const cover = e.currentTarget.querySelector(`.${styles.cover}`) as HTMLElement | null;
        if (card) {
            card.style.transform = '';
            card.style.boxShadow = '';
        }
        if (cover) cover.style.transform = '';
    };

    const imgUrl = image?.startsWith('http') ? image : `${TWT.static}${image}`;

    return (
        <div
            ref={tiltRef}
            className={styles.tilt}
            onClick={onClick}
            onMouseEnter={handleMouseEnter}
            onMouseLeave={handleMouseLeave}
        >
            <Card
                hoverable={false}
                className={styles.card}
                styles={{ body: { padding: 12 } }}
                cover={
                    <div className={styles.coverWrap} style={{ position: 'relative' }}>
                        {/* 封面加载中：shimmer 占位底，图片 onLoad 后淡入覆盖 */}
                        {!imgLoaded && (
                            <div className="twt-shimmer" style={{ position: 'absolute', inset: 0 }} />
                        )}
                        <img
                            ref={coverImgRef}
                            src={imgUrl}
                            alt={title}
                            className={`${styles.cover} ${imgLoaded ? 'twt-loaded' : 'twt-fade-img'}`}
                            loading="lazy"
                            onLoad={markImgLoaded}
                            onError={(e) => {
                                // 封面缺失/裂图兜底（与详情页 IMG_FALLBACK 同源）；兜底图直接可见
                                const img = e.currentTarget;
                                if (!img.dataset.fbk) {
                                    img.dataset.fbk = '1';
                                    img.src = IMG_FALLBACK;
                                }
                                setImgLoaded(true);
                            }}
                        />
                        {reason && <span className={styles.reasonTag}>{reason}</span>}
                        {onDislike && (
                            <button
                                type="button"
                                title="不感兴趣"
                                aria-label="不感兴趣"
                                className={styles.dislikeBtn}
                                onClick={(e) => {
                                    e.stopPropagation();
                                    onDislike();
                                }}
                            >
                                ✕
                            </button>
                        )}
                    </div>
                }
            >
                <div className={styles.title}>{title}</div>
                <div className={styles.priceRow}>
                    <span className={styles.price}>¥{price}</span>
                    {originalPrice && (
                        <span className={styles.originalPrice}>¥{originalPrice}</span>
                    )}
                </div>
                <div className={styles.meta}>
                    <span className={styles.seller}>{sellerName}</span>
                    {condition && (
                        <Tag className={styles.conditionTag}>
                            {conditionMap[condition] || condition}
                        </Tag>
                    )}
                    {city && <span className={styles.city}>{city}</span>}
                </div>
            </Card>
        </div>
    );
};

export default ProductCard;
