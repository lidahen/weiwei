import { useGSAP } from '@gsap/react';
import { gsap } from 'gsap';
import { useRef } from 'react';

export interface RevealOptions {
    /** 错开延迟，秒，默认 0.08 */
    stagger?: number;
    /** 单元素时长，秒，默认 0.6 */
    duration?: number;
    /** Y 偏移 px，默认 24 */
    y?: number;
    /** 缓动，默认 'power2.out' */
    ease?: string;
    /** 子选择器（相对 container），默认 '.reveal-item' */
    selector?: string;
    /** 依赖项（响应式值变化时重放） */
    dependencies?: any[];
}

/**
 * 容器内子元素 stagger 入场动画。
 *
 * 用法：
 *   const ref = useGsapReveal<HTMLDivElement>({ stagger: 0.06 });
 *   return <div ref={ref}>
 *     <div className="reveal-item">A</div>
 *     <div className="reveal-item">B</div>
 *   </div>
 *
 * a11y：自动通过 gsap.matchMedia() 适配 prefers-reduced-motion。
 *   - no-preference → 完整 fade + 上移
 *   - reduce → gsap.set 直接显示，零动画
 *
 * 防御：依赖项变化触发重放时，若容器内还没有匹配元素（如数据尚未到达），
 *       直接 return 避免 GSAP 报 "target not found"。
 */
export function useGsapReveal<T extends HTMLElement = HTMLDivElement>(
    options: RevealOptions = {},
) {
    const container = useRef<T>(null);
    const {
        stagger = 0.08,
        duration = 0.6,
        y = 24,
        ease = 'power2.out',
        selector = '.reveal-item',
        dependencies = [],
    } = options;

    useGSAP(
        () => {
            const mm = gsap.matchMedia();

            mm.add('(prefers-reduced-motion: no-preference)', () => {
                if (!container.current) return;
                const targets = container.current.querySelectorAll(selector);
                if (targets.length === 0) return;
                gsap.from(targets, {
                    y,
                    opacity: 0,
                    duration,
                    stagger,
                    ease,
                });
            });

            mm.add('(prefers-reduced-motion: reduce)', () => {
                if (!container.current) return;
                const targets = container.current.querySelectorAll(selector);
                if (targets.length === 0) return;
                gsap.set(targets, { opacity: 1, y: 0 });
            });

            return () => mm.revert();
        },
        { scope: container, dependencies },
    );

    return container;
}
