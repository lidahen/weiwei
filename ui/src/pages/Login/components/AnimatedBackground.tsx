import { createStyles } from 'antd-style';
import React from 'react';
import { loginTokens } from '../styles/tokens';

const useStyles = createStyles(() => ({
    bgBase: {
        position: 'fixed',
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        zIndex: 0,
        background: loginTokens.color.cream,
    },
    bgAccent: {
        position: 'fixed',
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        zIndex: 1,
        pointerEvents: 'none',
        // 极淡的右栏高光，让表单区有焦点感
        background:
            'radial-gradient(circle at 75% 50%, rgba(255, 255, 255, 0.6) 0%, transparent 60%)',
    },
    bgAccentDeep: {
        position: 'fixed',
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        zIndex: 1,
        pointerEvents: 'none',
        // 右下角极淡墨绿，给空间感
        background:
            'radial-gradient(circle at 90% 95%, rgba(74, 107, 87, 0.05) 0%, transparent 40%)',
    },
}));

const AnimatedBackground: React.FC = () => {
    const { styles } = useStyles();
    return (
        <>
            <div className={styles.bgBase} />
            <div className={styles.bgAccent} />
            <div className={styles.bgAccentDeep} />
        </>
    );
};

export default AnimatedBackground;
