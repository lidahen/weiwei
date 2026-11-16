// marketplace 子树专属 design tokens —— 与登录页 loginTokens 解耦
// 风格：现代社区/UGC 调性，暖橙主色 + 薄荷绿辅色，无衬线字体

export const marketplaceTokens = {
    color: {
        white: '#FFFFFF',
        bg: '#FAFAF8',          // 极淡暖灰背景
        ink: '#1A1A1A',         // 主文字
        sub: '#6B6B6B',         // 次文字
        mute: '#9A9A9A',        // 弱化文字
        line: '#EAEAE8',        // 分割线 / 边框
        primary: '#FF6B35',     // 暖橙（主色）
        primaryHover: '#FF8559',
        primarySoft: '#FFF1EC', // 橙底
        accent: '#4ECDC4',      // 薄荷绿
        accentSoft: '#E8F8F6',
    },
    radius: {
        sm: 8,
        md: 12,
        lg: 16,
        pill: 999,
    },
    shadow: {
        card: '0 2px 8px rgba(26,26,26,0.06)',
        cardHover: '0 12px 32px rgba(26,26,26,0.14)',
    },
    motion: {
        fast: 0.15,
        base: 0.25,
        slow: 0.4,
    },
    font: {
        sans: "'Inter', -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif",
        // 与 Login 设计令牌保持一致（见 src/pages/Login/styles/tokens.ts:34）：
        // MarketplaceFooter.tsx:27 的 brand 标题引用本字段，此前缺失导致 tsc 报 TS2339。
        serif: "'Cormorant Garamond', 'Source Han Serif SC', 'Noto Serif SC', serif",
    },
} as const;

export type MarketplaceTokens = typeof marketplaceTokens;

// ============================================================
// 统一响应式断点（全站收敛约定）
// < 768px = 移动端；≥ 768px = 桌面（维持现有布局）
// antd-style 的 createStyles 里直接把 mq.mobile / mq.desktop 当对象键用：
//   page: { padding: 16, [mq.desktop]: { padding: 24 } }
// 历史遗留的 768/899/1024 混用不强制回改，新增代码一律走这里。
// ============================================================
export const mq = {
    mobile: '@media (max-width: 767px)',
    desktop: '@media (min-width: 768px)',
} as const;

// JS 侧判断（与 CSS 断点保持一致）
export const MOBILE_BREAKPOINT = 767;

