import { gsap } from 'gsap';
import { useEffect, useRef, useState } from 'react';

interface CountUpProps {
    /** 目标数值（变化时从当前值滚动到新值） */
    value: number;
    /** 滚动时长（秒） */
    duration?: number;
    /** 保留小数位（默认按数值自带精度取整显示） */
    precision?: number;
    /** 前后缀，如 ¥ */
    prefix?: string;
    suffix?: string;
    style?: React.CSSProperties;
}

/**
 * 数字滚动动画（管理端 Dashboard 统计卡等）。
 * - 首次挂载从 0 滚到 value，之后 value 变化从当前值过渡到新值
 * - prefers-reduced-motion 用户直接显示最终值，不做动画
 */
const CountUp: React.FC<CountUpProps> = ({
    value,
    duration = 0.8,
    precision,
    prefix = '',
    suffix = '',
    style,
}) => {
    const [display, setDisplay] = useState(0);
    const objRef = useRef({ v: 0 });
    // 后端统计值可能是字符串数字（MyBatis map 计数），统一强转
    const target = Number(value) || 0;

    useEffect(() => {
        const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
        if (reduced) {
            setDisplay(target);
            return;
        }
        const tween = gsap.to(objRef.current, {
            v: target,
            duration,
            ease: 'power2.out',
            overwrite: true,
            onUpdate: () => setDisplay(objRef.current.v),
        });
        return () => {
            tween.kill();
        };
    }, [target, duration]);

    const text =
        precision !== null && precision !== undefined
            ? display.toFixed(precision)
            : Number.isInteger(target)
            ? Math.round(display).toLocaleString()
            : display.toFixed(2);

    return (
        <span style={style}>
            {prefix}
            {text}
            {suffix}
        </span>
    );
};

export default CountUp;
