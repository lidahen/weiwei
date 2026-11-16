import { useGSAP } from '@gsap/react';
import { createStyles } from 'antd-style';
import { gsap } from 'gsap';
import React, { useRef } from 'react';
import { loginTokens } from '../styles/tokens';

const useStyles = createStyles(() => ({
    left: {
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'space-between',
        height: '100%',
        gap: loginTokens.space.xl,
    },
    ornament: {
        fontSize: 11,
        letterSpacing: 4,
        textTransform: 'uppercase',
        color: loginTokens.color.stone,
        fontFamily: loginTokens.font.sans,
    },
    ornamentRow: {
        display: 'flex',
        alignItems: 'center',
        gap: loginTokens.space.md,
    },
    ornamentLine: {
        width: 32,
        height: 1,
        background: loginTokens.color.stone,
        opacity: 0.6,
    },
    center: {
        flex: 1,
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'center',
        padding: `${loginTokens.space.xl}px 0`,
    },
    headline: {
        fontFamily: loginTokens.font.serif,
        fontWeight: 300,
        lineHeight: 1.05,
        color: loginTokens.color.ink,
        margin: 0,
        marginBottom: loginTokens.space.md,
        fontSize: 'clamp(48px, 6.5vw, 88px)',
        letterSpacing: '-0.02em',
    },
    line: {
        display: 'block',
        overflow: 'hidden',
        paddingBottom: '0.05em',
    },
    lineInner: {
        display: 'inline-block',
    },
    italic: {
        fontStyle: 'italic',
    },
    sub: {
        fontSize: 15,
        color: loginTokens.color.stone,
        lineHeight: 1.7,
        maxWidth: 380,
        margin: 0,
        fontFamily: loginTokens.font.sans,
    },
}));

const BrandHeader: React.FC = () => {
    const { styles } = useStyles();
    const ref = useRef<HTMLDivElement>(null);

    useGSAP(
        () => {
            const mm = gsap.matchMedia();

            mm.add('(prefers-reduced-motion: no-preference)', () => {
                if (!ref.current) return;
                // 标题分行 mask 入场
                gsap.from(ref.current.querySelectorAll('.title-line-inner'), {
                    yPercent: 110,
                    duration: 0.85,
                    stagger: 0.12,
                    ease: 'power3.out',
                });
                // 顶部装饰 + 副标题 + 底部装饰
                gsap.from(ref.current.querySelectorAll(`.${styles.ornament}, .${styles.sub}`), {
                    opacity: 0,
                    y: 12,
                    duration: 0.7,
                    stagger: 0.1,
                    ease: 'power2.out',
                    delay: 0.5,
                });
            });

            mm.add('(prefers-reduced-motion: reduce)', () => {
                if (!ref.current) return;
                gsap.set(
                    ref.current.querySelectorAll(
                        '.title-line-inner, .ornament, .sub',
                    ),
                    { clearProps: 'all' },
                );
            });

            return () => mm.revert();
        },
        { scope: ref },
    );

    return (
        <div ref={ref} className={styles.left}>
            {/* 顶部 ornament */}
            <div className={styles.ornament}>weiwei — est. 2026</div>

            {/* 中部大标题 + 副标题 */}
            <div className={styles.center}>
                <h1 className={styles.headline}>
                    <span className={styles.line}>
                        <span className="title-line-inner">Quietly</span>
                    </span>
                    <span className={styles.line}>
                        <span className={`title-line-inner ${styles.italic}`}>
                            curated,
                        </span>
                    </span>
                    <span className={styles.line}>
                        <span className="title-line-inner">deeply lived.</span>
                    </span>
                </h1>
                <p className={styles.sub}>
                    一个为二手物品而生的安静角落。每件物品都有自己的故事，我们只负责让它们相遇。
                </p>
            </div>

            {/* 底部 ornament */}
            <div className={`${styles.ornament} ${styles.ornamentRow}`}>
                <span>Since 2026</span>
                <span className={styles.ornamentLine} />
                <span>Beijing</span>
            </div>
        </div>
    );
};

export default BrandHeader;
