import { useEffect, useState } from 'react';
import { MOBILE_BREAKPOINT } from '@/pages/marketplace/styles/tokens';

/**
 * 是否移动端视口（与 CSS 断点 mq.mobile 保持一致：< 768px）
 * 用 matchMedia 监听，横竖屏切换 / 拖动窗口宽度均实时响应。
 */
export function useIsMobile(breakpoint: number = MOBILE_BREAKPOINT): boolean {
    const [isMobile, setIsMobile] = useState<boolean>(() => {
        if (typeof window === 'undefined') return false;
        return window.innerWidth <= breakpoint;
    });

    useEffect(() => {
        const mql = window.matchMedia(`(max-width: ${breakpoint}px)`);
        const onChange = (e: MediaQueryListEvent) => setIsMobile(e.matches);
        setIsMobile(mql.matches);
        mql.addEventListener('change', onChange);
        return () => mql.removeEventListener('change', onChange);
    }, [breakpoint]);

    return isMobile;
}
