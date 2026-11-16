import { Button, Empty } from 'antd';
import React from 'react';

export interface EmptyStateProps {
    text?: string;
    ctaText?: string;
    onCtaClick?: () => void;
    image?: React.ReactNode;
}

const EmptyState: React.FC<EmptyStateProps> = ({
    text = '暂无数据',
    ctaText,
    onCtaClick,
    image,
}) => (
    <Empty description={text} image={image}>
        {ctaText && (
            <Button type="primary" onClick={onCtaClick}>
                {ctaText}
            </Button>
        )}
    </Empty>
);

export default EmptyState;
