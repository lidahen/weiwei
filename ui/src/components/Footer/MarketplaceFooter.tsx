import { createStyles } from 'antd-style';
import React from 'react';
import { marketplaceTokens } from '@/pages/marketplace/styles/tokens';
import { useIsMobile } from '@/hooks/useIsMobile';

const useStyles = createStyles(() => ({
    footer: {
        background: marketplaceTokens.color.white,
        borderTop: `1px solid ${marketplaceTokens.color.line}`,
        padding: '40px 24px 20px',
        marginTop: 48,
    },
    inner: {
        maxWidth: 1320,
        margin: '0 auto',
    },
    grid: {
        display: 'grid',
        gridTemplateColumns: '2fr 1fr 1fr',
        gap: 40,
        '@media (max-width: 768px)': {
            gridTemplateColumns: '1fr',
            gap: 24,
        },
    },
    brand: {
        fontFamily: marketplaceTokens.font.serif,
        fontSize: 28,
        fontWeight: 600,
        color: marketplaceTokens.color.ink,
        marginBottom: 8,
        letterSpacing: '-0.01em',
    },
    slogan: {
        color: marketplaceTokens.color.sub,
        fontSize: 14,
        lineHeight: 1.7,
        maxWidth: 380,
        margin: 0,
    },
    colTitle: {
        fontSize: 12,
        fontWeight: 600,
        color: marketplaceTokens.color.ink,
        marginBottom: 14,
        letterSpacing: 1.5,
        textTransform: 'uppercase',
    },
    colLink: {
        display: 'block',
        fontSize: 14,
        color: marketplaceTokens.color.sub,
        marginBottom: 10,
        textDecoration: 'none',
        cursor: 'pointer',
        transition: `color ${marketplaceTokens.motion.base}s ease`,
        '&:hover': {
            color: marketplaceTokens.color.primary,
        },
        '&:last-child': {
            marginBottom: 0,
        },
    },
    bottom: {
        borderTop: `1px solid ${marketplaceTokens.color.line}`,
        marginTop: 32,
        paddingTop: 20,
        textAlign: 'center',
        fontSize: 12,
        color: marketplaceTokens.color.mute,
        letterSpacing: 0.5,
    },
}));

const MarketplaceFooter: React.FC = () => {
    const { styles } = useStyles();
    // 移动端(<768px)由底部 TabBar 取代（见 components/MobileTabBar），不再渲染桌面页脚
    const isMobile = useIsMobile();
    if (isMobile) return null;
    return (
        <footer className={styles.footer}>
            <div className={styles.inner}>
                <div className={styles.grid}>
                    <div>
                        <div className={styles.brand}>weiwei</div>
                        <p className={styles.slogan}>
                            邻里二手 · 让闲置流动起来。
                            <br />
                            社区闲置，安心交易 —— 每件物品都有自己的故事。
                        </p>
                    </div>
                    <div>
                        <div className={styles.colTitle}>了解 weiwei</div>
                        <a className={styles.colLink}>关于我们</a>
                        <a className={styles.colLink}>使用指南</a>
                        <a className={styles.colLink}>社区公约</a>
                    </div>
                    <div>
                        <div className={styles.colTitle}>交易保障</div>
                        <a className={styles.colLink}>买家保障</a>
                        <a className={styles.colLink}>用户协议</a>
                        <a className={styles.colLink}>隐私政策</a>
                    </div>
                </div>
                <div className={styles.bottom}>© 2026 weiwei · 让闲置流动起来</div>
            </div>
        </footer>
    );
};

export default MarketplaceFooter;
