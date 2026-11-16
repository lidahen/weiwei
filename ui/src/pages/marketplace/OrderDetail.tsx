import OrderStatusSteps from '@/components/marketplace/OrderStatusSteps';
import { formatPrice, formatTime, IMG_FALLBACK, staticUrl } from './utils';
import { orderCancel, orderConfirm, orderDetail, refundByOrder, reviewByOrder } from '@/services/marketplace/api';
import { PageContainer } from '@ant-design/pro-components';
import { history, useModel, useRequest, useSearchParams } from '@umijs/max';
import { Avatar, Button, Card, Col, Descriptions, Image, message, Modal, Popconfirm, Rate, Row, Space, Spin, Tag } from 'antd';
import React from 'react';
import { marketplaceTokens } from './styles/tokens';
import { useIsMobile } from '@/hooks/useIsMobile';

const statusMap: Record<string, { text: string; color: string; current: number }> = {
    unpaid: { text: '待付款', color: 'orange', current: 0 },
    paid: { text: '已付款', color: 'gold', current: 1 },
    unshipped: { text: '待发货', color: 'blue', current: 1 },
    shipped: { text: '待收货', color: 'cyan', current: 2 },
    done: { text: '已完成', color: 'green', current: 3 },
    refunding: { text: '退款中', color: 'gold', current: 2 },
    refunded: { text: '已退款', color: 'red', current: 3 },
    closed: { text: '已关闭', color: 'default', current: 0 },
};

/** 快照 JSON 安全解析（订单快照为字符串，脏数据时返回空对象而非崩溃） */
function parseSnapshot(raw: unknown): Record<string, any> {
    if (!raw) return {};
    if (typeof raw !== 'string') return raw as Record<string, any>;
    try {
        return JSON.parse(raw);
    } catch {
        return {};
    }
}

/** 评价图片 JSON 数组安全解析 */
function parseImages(raw: unknown): string[] {
    if (!raw) return [];
    try {
        const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw;
        return Array.isArray(parsed) ? parsed : [];
    } catch {
        return [];
    }
}

const OrderDetail: React.FC = () => {
    const [params] = useSearchParams();
    const orderId = params.get('orderId');
    const { initialState } = useModel('@@initialState');
    // 当前查看者是否买家本人：付款/取消/确认收货是买家动作，卖家打开他人订单时隐藏（后端守卫兜底，
    // 前端隐藏避免误点必败的按钮）
    const myId = Number((initialState as any)?.currentUser?.user?.userId);
    const { data, loading, refresh } = useRequest(() => orderDetail(orderId!), {
        ready: !!orderId,
        refreshDeps: [orderId],
    });
    const order = (data as any)?.data ?? (data as any);
    // 依赖上面的 order，必须声明在其之后（否则触发 TDZ，页面渲染即抛 ReferenceError）
    const isBuyer = !!myId && Number(order?.buyerId) === myId;

    const { data: reviewResp } = useRequest(() => reviewByOrder(orderId!), {
        ready: !!orderId && order?.status === 'done',
        refreshDeps: [orderId, order?.status],
    });
    const reviewView = (reviewResp as any)?.data ?? (reviewResp as any);
    // 退款入口闸门一律用后端返回值（canApply / denyReason / activeRefundId），前端不重复实现 7 天窗口等规则
    const { data: refundCheckResp } = useRequest(() => refundByOrder(orderId!), {
        ready: !!orderId && !!order,
        refreshDeps: [orderId, order?.status],
    });
    const refundCheck = (refundCheckResp as any)?.data ?? (refundCheckResp as any);
    const isMobile = useIsMobile();

    if (loading || !order)
        return (
            <div style={{ textAlign: 'center', padding: 100 }}>
                <Spin />
            </div>
        );

    const snap = parseSnapshot(order.productSnapshot);
    const addr = parseSnapshot(order.addressSnapshot);
    const status = statusMap[order.status] || { text: order.status, color: 'default', current: 0 };

    const handleConfirmReceive = () => {
        Modal.confirm({
            title: '确认收货',
            content: '请确认已收到商品且与描述一致，确认后交易完成。',
            okText: '确认收货',
            cancelText: '再等等',
            onOk: async () => {
                try {
                    await orderConfirm(order.orderId);
                    message.success('已确认收货');
                    refresh();
                } catch (e: any) {
                    message.error(e?.msg || e?.message || '操作失败');
                }
            },
        });
    };

    /** 买家取消未支付订单（仅 unpaid 可取消），成功后刷新详情 */
    const handleCancelOrder = async () => {
        try {
            await orderCancel(order.orderId);
            message.success('订单已取消');
            refresh();
        } catch {
            // 全局 errorHandler 已统一弹错（如「订单状态不允许取消」）
        }
    };

    /** 双向评价单条展示（评价人昵称脱敏由服务端处理） */
    const renderReview = (label: string, r: any) => (
        <div>
            <Space size={12} align="start">
                <Avatar src={staticUrl(r.reviewerAvatar)} size={28}>
                    {(r.reviewerNickName || '匿')[0]}
                </Avatar>
                <span style={{ fontSize: 13 }}>{r.reviewerNickName || '匿名用户'}</span>
                <Rate disabled value={r.overallScore || 0} style={{ fontSize: 14 }} />
                <span style={{ fontSize: 12, color: '#bbb' }}>{label}</span>
            </Space>
            {r.content && <div style={{ marginTop: 8, color: '#444', whiteSpace: 'pre-wrap' }}>{r.content}</div>}
            {parseImages(r.images).length > 0 && (
                <Image.PreviewGroup>
                    <Row gutter={8} style={{ marginTop: 8 }}>
                        {parseImages(r.images).map((src, idx) => (
                            <Col key={idx}>
                                <Image
                                    src={staticUrl(src)}
                                    fallback={IMG_FALLBACK}
                                    width={72}
                                    height={72}
                                    style={{ borderRadius: 6, objectFit: 'cover' }}
                                />
                            </Col>
                        ))}
                    </Row>
                </Image.PreviewGroup>
            )}
            <div style={{ marginTop: 4, fontSize: 12, color: '#999' }}>{formatTime(r.createTime)}</div>
        </div>
    );

    return (
        <PageContainer
            header={{
                title: '订单详情',
                breadcrumb: {},
                extra: [
                    <Tag key="s" color={status.color}>
                        {status.text}
                    </Tag>,
                ],
            }}
        >
            <Row gutter={16}>
                <Col xs={24} lg={16}>
                    <Card style={{ marginBottom: 16 }} className="twt-fade-up">
                        <OrderStatusSteps
                            current={status.current}
                            status={order.status === 'closed' || order.status === 'refunded' ? 'error' : 'process'}
                        />
                    </Card>
                    <Card title="商品信息" style={{ marginBottom: 16 }} className="twt-fade-up" >
                        <Space size={16} align="start" wrap>
                            <Image
                                src={staticUrl(snap.cover)}
                                fallback={IMG_FALLBACK}
                                width={isMobile ? 96 : 120}
                                height={isMobile ? 96 : 120}
                                style={{ borderRadius: 8, objectFit: 'cover' }}
                            />
                            <div>
                                <h3 style={{ marginTop: 0 }}>{snap.title || '-'}</h3>
                                <div style={{ color: marketplaceTokens.color.primary, fontSize: 24, fontWeight: 600 }}>
                                    {formatPrice(snap.price)}
                                </div>
                                <div style={{ color: '#999', marginTop: 8 }}>x 1</div>
                            </div>
                        </Space>
                    </Card>
                    <Card title="收货信息" style={{ marginBottom: 16 }} className="twt-fade-up">
                        <Descriptions column={{ xs: 1, sm: 2 }}>
                            <Descriptions.Item label="收件人">{addr.receiver || '-'}</Descriptions.Item>
                            <Descriptions.Item label="手机">{addr.mobile || '-'}</Descriptions.Item>
                            <Descriptions.Item label="地址" span={2}>
                                {[addr.province, addr.city, addr.district, addr.detail].filter(Boolean).join(' ') || '-'}
                            </Descriptions.Item>
                            <Descriptions.Item label="买家留言" span={2}>
                                {order.buyerRemark || '无'}
                            </Descriptions.Item>
                        </Descriptions>
                    </Card>
                    {order.logistics && (
                        <Card title="物流信息" style={{ marginBottom: 16 }} className="twt-fade-up">
                            <Descriptions column={{ xs: 1, sm: 2 }}>
                                <Descriptions.Item label="快递公司">
                                    {order.logistics.company}
                                </Descriptions.Item>
                                <Descriptions.Item label="运单号">
                                    {order.logistics.no}
                                </Descriptions.Item>
                                <Descriptions.Item label="最新状态" span={2}>
                                    {order.logistics.latestNode}
                                </Descriptions.Item>
                            </Descriptions>
                        </Card>
                    )}
                    {order.status === 'done' && (
                        <Card title="评价" style={{ marginBottom: 16 }} className="twt-fade-up">
                            {reviewView?.buyerReview || reviewView?.sellerReview ? (
                                <Space direction="vertical" size={20} style={{ width: '100%' }}>
                                    {reviewView?.buyerReview &&
                                        renderReview('买家评价', reviewView.buyerReview)}
                                    {reviewView?.sellerReview &&
                                        renderReview('卖家评价', reviewView.sellerReview)}
                                </Space>
                            ) : (
                                <div style={{ color: '#999', textAlign: 'center', padding: 20 }}>
                                    暂无评价
                                </div>
                            )}
                        </Card>
                    )}
                </Col>
                <Col xs={24} lg={8}>
                    <Card title="订单信息" className="twt-fade-up">
                        <Descriptions column={1}>
                            <Descriptions.Item label="订单号">{order.orderNo}</Descriptions.Item>
                            <Descriptions.Item label="下单时间">
                                {formatTime(order.createTime)}
                            </Descriptions.Item>
                            <Descriptions.Item label="付款时间">
                                {order.paidAt ? formatTime(order.paidAt) : '-'}
                            </Descriptions.Item>
                            <Descriptions.Item label="发货时间">
                                {order.shippedAt ? formatTime(order.shippedAt) : '-'}
                            </Descriptions.Item>
                            <Descriptions.Item label="完成时间">
                                {order.doneAt ? formatTime(order.doneAt) : '-'}
                            </Descriptions.Item>
                            <Descriptions.Item label="商品金额">
                                {formatPrice(order.skuPrice)}
                            </Descriptions.Item>
                            <Descriptions.Item label="运费">{formatPrice(order.freight)}</Descriptions.Item>
                            <Descriptions.Item label="实付">
                                <span style={{ color: marketplaceTokens.color.primary, fontSize: 18, fontWeight: 600 }}>
                                    {formatPrice(order.totalAmount)}
                                </span>
                            </Descriptions.Item>
                        </Descriptions>
                    </Card>
                    <Card title="操作" style={{ marginTop: 16 }} className="twt-fade-up">
                        <Space direction="vertical" style={{ width: '100%' }}>
                            {order.status === 'unpaid' && isBuyer && (
                                <>
                                    <Button
                                        type="primary"
                                        block
                                        className="twt-press"
                                        onClick={() => history.push(`/trade/pay?orderId=${order.orderId}`)}
                                    >
                                        去付款
                                    </Button>
                                    <Popconfirm
                                        title="取消订单"
                                        description="取消后订单将关闭并释放库存，确定取消吗？"
                                        okText="确认取消"
                                        cancelText="再想想"
                                        onConfirm={handleCancelOrder}
                                    >
                                        <Button block danger className="twt-press">
                                            取消订单
                                        </Button>
                                    </Popconfirm>
                                </>
                            )}
                            {order.status === 'shipped' && isBuyer && (
                                <Button type="primary" block className="twt-press" onClick={handleConfirmReceive}>
                                    确认收货
                                </Button>
                            )}
                            {order.status === 'done' && !reviewView?.myReviewed && (
                                <Button
                                    type="primary"
                                    block
                                    className="twt-press"
                                    onClick={() =>
                                        history.push(`/trade/review?orderId=${order.orderId}`)
                                    }
                                >
                                    {reviewView?.myRole === 'seller' ? '评价买家' : '去评价'}
                                </Button>
                            )}
                            <Button
                                block
                                onClick={() =>
                                    history.push(
                                        `/im/chat?peerId=${order.sellerId}&productId=${order.productId}`,
                                    )
                                }
                            >
                                联系卖家
                            </Button>
                            {isBuyer && refundCheck?.canApply === true && (
                                <Button
                                    block
                                    onClick={() =>
                                        history.push(`/trade/refund/apply?orderId=${order.orderId}`)
                                    }
                                >
                                    申请退款
                                </Button>
                            )}
                            {isBuyer && refundCheck?.activeRefundId && (
                                <Button
                                    block
                                    onClick={() =>
                                        history.push(
                                            `/trade/refund/detail?refundId=${refundCheck.activeRefundId}`,
                                        )
                                    }
                                >
                                    查看退款进度
                                </Button>
                            )}
                            {isBuyer &&
                                refundCheck?.canApply === false &&
                                !refundCheck?.activeRefundId &&
                                refundCheck?.denyReason && (
                                    <div
                                        style={{
                                            textAlign: 'center',
                                            color: 'rgba(0,0,0,0.45)',
                                            fontSize: 12,
                                        }}
                                    >
                                        {refundCheck.denyReason}
                                    </div>
                                )}
                        </Space>
                    </Card>
                </Col>
            </Row>
        </PageContainer>
    );
};

export default OrderDetail;
