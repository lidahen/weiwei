import { Button, Card, Tag } from 'antd';
import TWT from '@/setting';
import React from 'react';

export interface BargainCardProps {
    productTitle: string;
    productImage?: string;
    bargainPrice: number;
    originalPrice?: number;
    onAgree?: () => void;
    onReject?: () => void;
    onCounter?: () => void;
    status?: 'pending' | 'agreed' | 'rejected' | 'expired';
    role?: 'buyer' | 'seller';
}

const BargainCard: React.FC<BargainCardProps> = ({
    productTitle,
    productImage,
    bargainPrice,
    originalPrice,
    onAgree,
    onReject,
    onCounter,
    status = 'pending',
    role = 'buyer',
}) => {
    const imgSrc = productImage
        ? productImage.startsWith('http')
            ? productImage
            : `${TWT.static}${productImage}`
        : undefined;

    return (
    <Card
        style={{ maxWidth: 320, borderRadius: 12, borderColor: '#FFE0D0' }}
        bodyStyle={{ padding: 12 }}
    >
        <div style={{ display: 'flex', gap: 12 }}>
            {imgSrc && (
                <img
                    src={imgSrc}
                    alt={productTitle}
                    style={{ width: 56, height: 56, borderRadius: 8, objectFit: 'cover' }}
                />
            )}
            <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontSize: 13, lineHeight: '18px', height: 36, overflow: 'hidden' }}>
                    {productTitle}
                </div>
                <div style={{ color: '#FF6B35', fontSize: 18, fontWeight: 600, marginTop: 4 }}>
                    出价 ¥{bargainPrice}
                    {originalPrice && (
                        <span
                            style={{
                                fontSize: 12,
                                color: '#999',
                                marginLeft: 6,
                                textDecoration: 'line-through',
                                fontWeight: 'normal',
                            }}
                        >
                            ¥{originalPrice}
                        </span>
                    )}
                </div>
            </div>
        </div>
        {status === 'pending' && role === 'seller' && (
            <div style={{ display: 'flex', gap: 8, marginTop: 12 }}>
                <Button size="small" block onClick={onReject}>
                    拒绝
                </Button>
                <Button size="small" block onClick={onCounter}>
                    还价
                </Button>
                <Button size="small" block type="primary" onClick={onAgree}>
                    同意
                </Button>
            </div>
        )}
        {status === 'pending' && role === 'buyer' && (
            <div style={{ marginTop: 12 }}>
                <Tag color="orange">等待对方处理</Tag>
            </div>
        )}
        {status === 'agreed' && (
            <div style={{ marginTop: 12, color: '#2E7D5B', fontWeight: 500 }}>
                已同意,请前往付款
            </div>
        )}
        {status === 'rejected' && <div style={{ marginTop: 12, color: '#999' }}>已被拒绝</div>}
        {status === 'expired' && <div style={{ marginTop: 12, color: '#999' }}>议价已过期</div>}
    </Card>
    );
};

export default BargainCard;
