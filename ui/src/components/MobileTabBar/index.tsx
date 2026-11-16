import { history, useLocation } from '@umijs/max';
import { createStyles } from 'antd-style';
import {
    CompassOutlined,
    HomeOutlined,
    MessageOutlined,
    PlusCircleFilled,
    UserOutlined,
} from '@ant-design/icons';
import React, { useEffect, useState } from 'react';
import { marketplaceTokens, mq } from '@/pages/marketplace/styles/tokens';
import { useIsMobile } from '@/hooks/useIsMobile';

/**
 * 移动端底部 TabBar（< 768px 渲染，桌面端返回 null）
 *
 * 5 个入口：首页 / 动态 / 发布（中央凸起，弹发布面板）/ 消息 / 我的
 * - 满屏页面（IM 聊天、AI 导购/管家聊天）自动隐藏
 * - 挂载时给 body 加 .wb-mobile-tabbar，配合 global.less 给内容区留出底栏高度
 */

const useStyles = createStyles(() => ({
    '@keyframes wbSheetUp': {
        from: { transform: 'translateY(100%)' },
        to: { transform: 'translateY(0)' },
    },
    tabbar: {
        position: 'fixed',
        left: 0,
        right: 0,
        bottom: 0,
        // 998：低于 antd Drawer/Modal(1000)，否则侧边菜单/弹窗会被 TabBar 盖住
        zIndex: 998,
        display: 'flex',
        alignItems: 'stretch',
        background: 'rgba(255,255,255,0.96)',
        backdropFilter: 'blur(12px)',
        borderTop: `1px solid ${marketplaceTokens.color.line}`,
        // iOS 安全区：全面屏手势条
        paddingBottom: 'env(safe-area-inset-bottom)',
        height: 'calc(56px + env(safe-area-inset-bottom))',
    },
    tabItem: {
        flex: 1,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 2,
        fontSize: 11,
        lineHeight: 1,
        color: marketplaceTokens.color.mute,
        background: 'none',
        border: 'none',
        cursor: 'pointer',
        transition: `color ${marketplaceTokens.motion.fast}s`,
        '&:active': {
            transform: 'scale(0.92)',
        },
    },
    tabItemActive: {
        color: marketplaceTokens.color.primary,
    },
    tabIcon: {
        fontSize: 22,
        lineHeight: 1,
    },
    // 中央发布按钮
    publishWrap: {
        flex: 1,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
    },
    publishBtn: {
        width: 46,
        height: 46,
        marginTop: -18,
        borderRadius: '50%',
        border: 'none',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        fontSize: 24,
        color: '#fff',
        background: `linear-gradient(135deg, ${marketplaceTokens.color.primary} 0%, ${marketplaceTokens.color.primaryHover} 100%)`,
        boxShadow: '0 6px 16px rgba(255, 107, 53, 0.35)',
        cursor: 'pointer',
        transition: `transform ${marketplaceTokens.motion.fast}s`,
        '&:active': {
            transform: 'scale(0.92) rotate(90deg)',
        },
    },
    // 发布面板
    sheetMask: {
        position: 'fixed',
        inset: 0,
        zIndex: 1001,
        background: 'rgba(26,26,26,0.45)',
        backdropFilter: 'blur(2px)',
    },
    sheet: {
        position: 'fixed',
        left: 0,
        right: 0,
        bottom: 0,
        zIndex: 1002,
        background: '#fff',
        borderRadius: '16px 16px 0 0',
        padding: '16px 16px calc(16px + env(safe-area-inset-bottom))',
        animation: 'wbSheetUp 0.22s cubic-bezier(0.22, 1, 0.36, 1)',
    },
    sheetTitle: {
        textAlign: 'center',
        fontSize: 13,
        color: marketplaceTokens.color.mute,
        margin: '0 0 12px',
    },
    sheetOptions: {
        display: 'flex',
        gap: 12,
        [mq.mobile]: {
            gap: 12,
        },
    },
    sheetOption: {
        flex: 1,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        gap: 8,
        padding: '18px 0 14px',
        borderRadius: marketplaceTokens.radius.md,
        border: `1px solid ${marketplaceTokens.color.line}`,
        background: marketplaceTokens.color.bg,
        cursor: 'pointer',
        '&:active': {
            background: marketplaceTokens.color.primarySoft,
            borderColor: marketplaceTokens.color.primary,
        },
    },
    sheetOptionIcon: {
        fontSize: 28,
        color: marketplaceTokens.color.primary,
        lineHeight: 1,
    },
    sheetOptionText: {
        fontSize: 14,
        color: marketplaceTokens.color.ink,
    },
    sheetCancel: {
        width: '100%',
        marginTop: 12,
        padding: '12px 0',
        borderRadius: marketplaceTokens.radius.md,
        border: 'none',
        background: '#F2F2F0',
        fontSize: 15,
        color: marketplaceTokens.color.sub,
        cursor: 'pointer',
    },
}));

type TabDef = {
    key: string;
    icon: React.ReactNode;
    label: string;
    path: string;
    // 当前路径是否高亮此 Tab
    match: (pathname: string) => boolean;
};

const TABS: TabDef[] = [
    {
        key: 'home',
        icon: <HomeOutlined />,
        label: '首页',
        path: '/home',
        match: (p) =>
            p === '/home' ||
            p.startsWith('/search') ||
            p.startsWith('/product/detail') ||
            p.startsWith('/user/favorites'),
    },
    {
        key: 'feed',
        icon: <CompassOutlined />,
        label: '动态',
        path: '/feed/stream',
        match: (p) => p.startsWith('/feed'),
    },
    {
        key: 'im',
        icon: <MessageOutlined />,
        label: '消息',
        path: '/im/conversations',
        match: (p) => p.startsWith('/im'),
    },
    {
        key: 'me',
        icon: <UserOutlined />,
        label: '我的',
        path: '/user/profile/me',
        match: (p) => p.startsWith('/user') || p.startsWith('/trade') || p.startsWith('/product/my'),
    },
];

// 满屏页面隐藏 TabBar（聊天输入框需要贴底）
const HIDDEN_PREFIXES = ['/im/chat', '/ai/shopping-guide', '/ai/butler'];

export default function MobileTabBar() {
    const isMobile = useIsMobile();
    const { pathname } = useLocation();
    const [sheetOpen, setSheetOpen] = useState(false);
    const { styles, cx } = useStyles();

    // 挂载时给 body 加标记，global.less 据此给内容区留出底栏高度
    useEffect(() => {
        if (isMobile) {
            document.body.classList.add('wb-mobile-tabbar');
            return () => {
                document.body.classList.remove('wb-mobile-tabbar');
            };
        }
        document.body.classList.remove('wb-mobile-tabbar');
        return undefined;
    }, [isMobile]);

    if (!isMobile) return null;

    const hidden = HIDDEN_PREFIXES.some((p) => pathname.startsWith(p));
    if (hidden) return null;

    const go = (path: string) => {
        setSheetOpen(false);
        if (pathname !== path) history.push(path);
    };

    const openPublish = (path: string) => {
        setSheetOpen(false);
        history.push(path);
    };

    return (
        <>
            <nav className={styles.tabbar} aria-label="移动端主导航">
                {TABS.map((tab, index) => (
                    <React.Fragment key={tab.key}>
                        {/* 第 3 格留给中央发布按钮 */}
                        {index === 2 && (
                            <div className={styles.publishWrap}>
                                <button
                                    type="button"
                                    className={styles.publishBtn}
                                    aria-label="发布"
                                    onClick={() => setSheetOpen(true)}
                                >
                                    <PlusCircleFilled />
                                </button>
                            </div>
                        )}
                        <button
                            type="button"
                            className={cx(styles.tabItem, tab.match(pathname) && styles.tabItemActive)}
                            onClick={() => go(tab.path)}
                        >
                            <span className={styles.tabIcon}>{tab.icon}</span>
                            <span>{tab.label}</span>
                        </button>
                    </React.Fragment>
                ))}
            </nav>

            {sheetOpen && (
                <>
                    <div className={styles.sheetMask} onClick={() => setSheetOpen(false)} />
                    <div className={styles.sheet}>
                        <p className={styles.sheetTitle}>选择要发布的内容</p>
                        <div className={styles.sheetOptions}>
                            <div
                                className={styles.sheetOption}
                                onClick={() => openPublish('/product/publish')}
                            >
                                <span className={styles.sheetOptionIcon}>🏷️</span>
                                <span className={styles.sheetOptionText}>发布闲置</span>
                            </div>
                            <div
                                className={styles.sheetOption}
                                onClick={() => openPublish('/feed/publish')}
                            >
                                <span className={styles.sheetOptionIcon}>📝</span>
                                <span className={styles.sheetOptionText}>发动态</span>
                            </div>
                            <div
                                className={styles.sheetOption}
                                onClick={() => openPublish('/runner/task/publish')}
                            >
                                <span className={styles.sheetOptionIcon}>🛵</span>
                                <span className={styles.sheetOptionText}>发互助</span>
                            </div>
                        </div>
                        <button
                            type="button"
                            className={styles.sheetCancel}
                            onClick={() => setSheetOpen(false)}
                        >
                            取消
                        </button>
                    </div>
                </>
            )}
        </>
    );
}
