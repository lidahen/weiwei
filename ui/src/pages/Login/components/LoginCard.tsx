import { createStyles } from 'antd-style';
import React from 'react';
import { loginTokens } from '../styles/tokens';

const useStyles = createStyles(() => ({
    card: {
        position: 'relative',
        width: '100%',
        maxWidth: 400,
        background: loginTokens.color.white,
        borderRadius: loginTokens.radius.sm,
        padding: `${loginTokens.space.xxl}px ${loginTokens.space.xl}px`,
        boxShadow: loginTokens.shadow.card,
        border: '1px solid rgba(107, 107, 102, 0.10)',
    },
}));

const LoginCard: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const { styles } = useStyles();
    return <div className={styles.card}>{children}</div>;
};

export default LoginCard;
