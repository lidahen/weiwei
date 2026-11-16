import { addressList, orderCreate, productDetail } from '@/services/marketplace/api';
import { formatPrice, IMG_FALLBACK, staticUrl } from './utils';
import { PageContainer } from '@ant-design/pro-components';
import { history, useRequest, useSearchParams } from '@umijs/max';
import { Button, Card, Col, Descriptions, Empty, Image, Input, message, Row, Space, Tag } from 'antd';
import React, { useState } from 'react';
import { marketplaceTokens } from './styles/tokens';
import { useIsMobile } from '@/hooks/useIsMobile';

/**
 * 结算页（确认订单）：
 * - 左：收货地址选择（默认选中 isDefault，无地址引导去 /user/address）+ 买家留言
 *   —— self_pickup（同城面交）商品无需收货地址：不展示地址引导，下单不传 addressId（后端忽略地址）
 * - 右：商品摘要 + 费用明细（price / freight / 合计）+ 提交按钮
 * - 提交中 loading + disabled，防止重复下单（资损面）
 * - 成功后跳订单详情；失败提示由全局 errorHandler 统一弹出
 */
const Checkout: React.FC = () => {
    const [params] = useSearchParams();
    const productId = params.get('productId');
    const isMobile = useIsMobile();

    const [selectedAddressId, setSelectedAddressId] = useState<number | undefined>();
    const [buyerRemark, setBuyerRemark] = useState('');
    const [submitting, setSubmitting] = useState(false);

    // 商品详情与地址列表并行拉取
    const { data: productResp, loading: productLoading } = useRequest(
        () => productDetail(productId!),
        { ready: !!productId, refreshDeps: [productId] },
    );
    const { data: addrResp, loading: addrLoading } = useRequest(() => addressList());

    const product = (productResp as any)?.data ?? (productResp as any);
    const rawAddr = (addrResp as any)?.data ?? (addrResp as any);
    const addresses: any[] = Array.isArray(rawAddr) ? rawAddr : [];

    // 同城面交（self_pickup）：规格允许无地址下单，地址非必选
    const isSelfPickup = product?.shippingMethod === 'self_pickup';

    // 默认选中 isDefault 地址（用户未手动选择时）；面交商品仅在有地址时保留可选展示
    const defaultAddr = addresses.find((a: any) => a?.isDefault) || addresses[0];
    const selectedId = selectedAddressId ?? defaultAddr?.addressId;
    const selectedAddress = addresses.find((a: any) => a.addressId === selectedId);

    // 费用明细：商品金额 + 运费（包邮/自提均为 0）
    const priceNum = Number(product?.price) || 0;
    const freightNum =
        !product || product.shippingMethod === 'free' || product.shippingMethod === 'self_pickup'
            ? 0
            : Number(product.shippingFee) || 0;
    const freightLabel =
        product?.shippingMethod === 'self_pickup'
            ? '自提'
            : freightNum === 0
            ? '包邮'
            : formatPrice(freightNum);
    const totalAmount = priceNum + freightNum;

    const pageLoading = !!productId && (productLoading || (addrLoading && !addrResp));

    const handleSubmit = async () => {
        if (submitting) return;
        if (!product) return;
        // 面交商品不强制地址；快递商品必须先选地址
        if (!isSelfPickup && !selectedAddress) {
            message.warning('请先选择收货地址');
            return;
        }
        setSubmitting(true);
        try {
            const r: any = await orderCreate({
                productId: Number(productId),
                // 面交不传 addressId（后端忽略地址）；有选中地址时也一并带上，便于订单留存
                addressId: isSelfPickup ? undefined : selectedAddress?.addressId,
                buyerRemark: buyerRemark.trim() || undefined,
            });
            const payload = r?.data ?? r;
            const orderId = payload?.orderId;
            if (!orderId) {
                // 业务错误未抛异常时兜底提示
                message.error(r?.msg || '下单失败，请稍后再试');
                return;
            }
            message.success('下单成功');
            history.push(`/trade/order/detail?orderId=${orderId}`);
        } catch {
            // 全局 errorHandler 已统一弹错，这里只负责复位提交态
        } finally {
            setSubmitting(false);
        }
    };

    const addressCardStyle = (active: boolean): React.CSSProperties => ({
        border: `1px solid ${active ? marketplaceTokens.color.primary : marketplaceTokens.color.line}`,
        borderRadius: marketplaceTokens.radius.md,
        padding: 12,
        cursor: 'pointer',
        background: active ? marketplaceTokens.color.primarySoft : marketplaceTokens.color.white,
        transition: `border-color ${marketplaceTokens.motion.fast}s, background ${marketplaceTokens.motion.fast}s`,
    });

    if (!productId) {
        return (
            <PageContainer header={{ title: '确认订单', breadcrumb: {} }}>
                <Card>
                    <Empty description="链接缺少商品信息">
                        <Button type="primary" onClick={() => history.push('/')}>
                            返回首页
                        </Button>
                    </Empty>
                </Card>
            </PageContainer>
        );
    }

    if (pageLoading) {
        // 骨架屏：与 SkeletonCards 同款 shimmer 风格，按结算页双栏布局占位
        return (
            <PageContainer header={{ title: '确认订单', breadcrumb: {} }}>
                <Row gutter={isMobile ? 12 : 16} aria-label="加载中">
                    <Col xs={24} lg={16}>
                        <Card>
                            <div className="twt-shimmer" style={{ height: 18, width: '35%', borderRadius: 4 }} />
                            <div className="twt-shimmer" style={{ height: 76, borderRadius: 12, marginTop: 16 }} />
                            <div className="twt-shimmer" style={{ height: 76, borderRadius: 12, marginTop: 12 }} />
                            <div className="twt-shimmer" style={{ height: 110, borderRadius: 12, marginTop: 16 }} />
                        </Card>
                    </Col>
                    <Col xs={24} lg={8}>
                        <Card>
                            <div className="twt-shimmer" style={{ height: 88, borderRadius: 12 }} />
                            <div className="twt-shimmer" style={{ height: 130, borderRadius: 12, marginTop: 16 }} />
                            <div className="twt-shimmer" style={{ height: 48, borderRadius: 12, marginTop: 16 }} />
                        </Card>
                    </Col>
                </Row>
            </PageContainer>
        );
    }

    if (!product) {
        return (
            <PageContainer header={{ title: '确认订单', breadcrumb: {} }}>
                <Card>
                    <Empty description="商品不存在或已下架">
                        <Button type="primary" onClick={() => history.push('/')}>
                            返回首页
                        </Button>
                    </Empty>
                </Card>
            </PageContainer>
        );
    }

    return (
        <PageContainer header={{ title: '确认订单', breadcrumb: {} }}>
            <Row gutter={isMobile ? 12 : 16}>
                <Col xs={24} lg={16}>
                    <Card
                        title={isSelfPickup ? '收货方式' : '收货地址'}
                        className="twt-fade-up"
                        style={{ marginBottom: 16 }}
                        extra={
                            addresses.length > 0 && !isSelfPickup ? (
                                <Button type="link" size="small" onClick={() => history.push('/user/address')}>
                                    管理地址
                                </Button>
                            ) : undefined
                        }
                    >
                        {isSelfPickup ? (
                            <div
                                style={{
                                    color: marketplaceTokens.color.sub,
                                    fontSize: 13,
                                    display: 'flex',
                                    alignItems: 'center',
                                    gap: 8,
                                }}
                            >
                                <Tag color="green" style={{ marginInlineEnd: 0 }}>
                                    同城面交
                                </Tag>
                                无需收货地址，下单后请与卖家约定当面交付的时间与地点
                            </div>
                        ) : addresses.length === 0 ? (
                            <Empty description="还没有收货地址，先添加一个吧">
                                <Button
                                    type="primary"
                                    className="twt-press"
                                    onClick={() => history.push('/user/address')}
                                >
                                    去添加地址
                                </Button>
                            </Empty>
                        ) : (
                            <Space direction="vertical" size={12} style={{ width: '100%' }}>
                                {addresses.map((a: any) => (
                                    <div
                                        key={a.addressId}
                                        style={addressCardStyle(a.addressId === selectedId)}
                                        onClick={() => setSelectedAddressId(a.addressId)}
                                    >
                                        <Space size={8} align="center" wrap>
                                            <span style={{ fontWeight: 500 }}>{a.receiver || '-'}</span>
                                            <span style={{ color: marketplaceTokens.color.sub }}>{a.mobile}</span>
                                            {a.isDefault ? (
                                                <Tag color="orange" style={{ marginInlineEnd: 0 }}>
                                                    默认
                                                </Tag>
                                            ) : null}
                                        </Space>
                                        <div style={{ marginTop: 4, color: marketplaceTokens.color.sub, fontSize: 13 }}>
                                            {[a.province, a.city, a.district, a.detail].filter(Boolean).join(' ') || '-'}
                                        </div>
                                    </div>
                                ))}
                            </Space>
                        )}
                    </Card>
                    <Card title="买家留言" className="twt-fade-up">
                        <Input.TextArea
                            rows={3}
                            maxLength={200}
                            showCount
                            value={buyerRemark}
                            onChange={(e) => setBuyerRemark(e.target.value)}
                            placeholder="选填，给卖家留言（最多200字）"
                        />
                    </Card>
                </Col>
                <Col xs={24} lg={8}>
                    <Card title="商品信息" className="twt-fade-up" style={{ marginBottom: 16 }}>
                        <Space size={12} align="start">
                            <Image
                                src={staticUrl(product.cover)}
                                fallback={IMG_FALLBACK}
                                width={isMobile ? 64 : 80}
                                height={isMobile ? 64 : 80}
                                style={{ borderRadius: 8, objectFit: 'cover' }}
                                preview={false}
                            />
                            <div style={{ flex: 1, minWidth: 0 }}>
                                <div
                                    style={{
                                        fontSize: 13,
                                        marginBottom: 4,
                                        display: '-webkit-box',
                                        WebkitLineClamp: 2,
                                        WebkitBoxOrient: 'vertical',
                                        overflow: 'hidden',
                                    }}
                                >
                                    {product.title || '-'}
                                </div>
                                <div style={{ color: marketplaceTokens.color.primary, fontWeight: 600 }}>
                                    {formatPrice(product.price)}
                                </div>
                                <div style={{ color: marketplaceTokens.color.mute, fontSize: 12 }}>x 1</div>
                            </div>
                        </Space>
                    </Card>
                    <Card title="费用明细" className="twt-fade-up" style={{ marginBottom: 16 }}>
                        <Descriptions column={1} size="small">
                            <Descriptions.Item label="商品金额">{formatPrice(priceNum)}</Descriptions.Item>
                            <Descriptions.Item label="运费">{freightLabel}</Descriptions.Item>
                            <Descriptions.Item label="合计">
                                <span
                                    style={{
                                        color: marketplaceTokens.color.primary,
                                        fontSize: 18,
                                        fontWeight: 600,
                                    }}
                                >
                                    {formatPrice(totalAmount)}
                                </span>
                            </Descriptions.Item>
                        </Descriptions>
                    </Card>
                    <Card className="twt-fade-up">
                        <Button
                            type="primary"
                            size="large"
                            block
                            className="twt-press"
                            loading={submitting}
                            disabled={submitting || (!isSelfPickup && !selectedAddress)}
                            onClick={handleSubmit}
                        >
                            提交订单
                        </Button>
                        <div
                            style={{
                                marginTop: 8,
                                fontSize: 12,
                                color: marketplaceTokens.color.mute,
                                textAlign: 'center',
                            }}
                        >
                            提交后请在订单详情页完成支付
                        </div>
                    </Card>
                </Col>
            </Row>
        </PageContainer>
    );
};

export default Checkout;
