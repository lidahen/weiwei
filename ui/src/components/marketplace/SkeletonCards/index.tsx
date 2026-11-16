import { Col, Row } from 'antd';
import React from 'react';

export interface SkeletonCardsProps {
    /** 占位卡片数量 */
    count?: number;
    /** 封面占位高度（与 ProductCard 封面一致为 200） */
    coverHeight?: number;
    /** 栅格响应式断点，默认与商品列表一致 */
    xs?: number;
    sm?: number;
    md?: number;
    lg?: number;
    xl?: number;
}

/**
 * 卡片网格骨架屏：加载中的占位，与商品/内容卡片网格同构，
 * 避免加载完成时布局跳动（CLS）。配合 .twt-fade-up 入场动画使用体验最佳。
 */
const SkeletonCards: React.FC<SkeletonCardsProps> = ({
    count = 8,
    coverHeight = 200,
    xs = 24,
    sm = 12,
    md = 8,
    lg = 6,
    xl = 4,
}) => (
    <Row gutter={[16, 16]} aria-label="加载中">
        {Array.from({ length: count }).map((_, i) => (
            <Col key={i} xs={xs} sm={sm} md={md} lg={lg} xl={xl}>
                <div
                    style={{
                        background: '#fff',
                        borderRadius: 12,
                        padding: 12,
                        border: '1px solid #EAEAE8',
                        overflow: 'hidden',
                    }}
                >
                    <div className="twt-shimmer" style={{ height: coverHeight, borderRadius: 8 }} />
                    <div
                        className="twt-shimmer"
                        style={{ height: 14, borderRadius: 4, marginTop: 12, width: '85%' }}
                    />
                    <div
                        className="twt-shimmer"
                        style={{ height: 14, borderRadius: 4, marginTop: 8, width: '45%' }}
                    />
                </div>
            </Col>
        ))}
    </Row>
);

export default SkeletonCards;
