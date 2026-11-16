import { Avatar, Button } from 'antd';
import React from 'react';

export interface UserCardProps {
    avatar: string;
    nickName: string;
    subText?: string;
    followed?: boolean;
    onFollow?: () => void;
    onClick?: () => void;
    size?: number;
}

const UserCard: React.FC<UserCardProps> = ({
    avatar,
    nickName,
    subText,
    followed,
    onFollow,
    onClick,
    size = 48,
}) => (
    <div style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '8px 0' }}>
        <Avatar
            src={avatar}
            size={size}
            onClick={onClick}
            style={{ cursor: onClick ? 'pointer' : 'default' }}
        />
        <div style={{ flex: 1, cursor: onClick ? 'pointer' : 'default' }} onClick={onClick}>
            <div style={{ fontWeight: 500 }}>{nickName}</div>
            {subText && <div style={{ fontSize: 12, color: '#999', marginTop: 2 }}>{subText}</div>}
        </div>
        {onFollow && (
            <Button size="small" type={followed ? 'default' : 'primary'} onClick={onFollow}>
                {followed ? '已关注' : '关注'}
            </Button>
        )}
    </div>
);

export default UserCard;
