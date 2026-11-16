import { orderDetail, orderShip } from '@/services/marketplace/api';
import { formatPrice, IMG_FALLBACK, staticUrl } from './utils';
import { PageContainer } from '@ant-design/pro-components';
import { history, useRequest, useSearchParams } from '@umijs/max';
import { Button, Card, Col, Descriptions, Empty, Form, Image, Input, message, Row, Select, Space, Spin } from 'antd';
import React, { useState } from 'react';
import { marketplaceTokens } from './styles/tokens';
import { useIsMobile } from '@/hooks/useIsMobile';

/**
 * 快递公司静态选项（顺丰/中通/圆通/韵达/京东/邮政/其他）
 */
const EXPRESS_OPTIONS = [
    { value: '顺丰速运', label: '顺丰速运' },
    { value: '中通快递', label: '中通快递' },
    { value: '圆通速递', label: '圆通速递' },
    { value: '韵达快递', label: '韵达快递' },
    { value: '京东物流', label: '京东物流' },
    { value: '中国邮政', label: '中国邮政' },
    { value: '其他', label: '其他' },
];

/** 快照 JSON 安全解析（与 OrderDetail 同款：脏数据返回空对象而非崩溃） */
function parseSnapshot(raw: unknown): Record<string, any> {
    if (!raw) return {};
    if (typeof raw !== 'string') return raw as Record<string, any>;
    try {
        return JSON.parse(raw);
    } catch {
        return {};
    }
}

/**
 * 卖家发货页：
 * - useSearchParams 取 orderId，缺失时显示错误态并给返回入口（不白屏）
 * - orderDetail 回显商品快照摘要（封面/标题/价格 + 订单号/实付/买家）
 * - Form 提交 orderShip（快递公司 Select + 单号必填），成功后返回订单列表
 * - 失败提示由全局 errorHandler 统一弹出
 */
const Ship: React.FC = () => {
    const [params] = useSearchParams();
    const orderId = params.get('orderId');
    const isMobile = useIsMobile();
    const [submitting, setSubmitting] = useState(false);
    const [form] = Form.useForm();

    const { data, loading } = useRequest(() => orderDetail(orderId!), {
        ready: !!orderId,
        refreshDeps: [orderId],
    });
    const order = (data as any)?.data ?? (data as any);
    const snap = parseSnapshot(order?.productSnapshot);

    const handleSubmit = async (values: { company: string; no: string }) => {
        if (submitting) return;
        setSubmitting(true);
        try {
            await orderShip({
                orderId: Number(orderId),
                company: values.company,
                no: values.no.trim(),
            });
            message.success('发货成功');
            history.push('/user/orders?role=seller');
        } catch {
            // 全局 errorHandler 已统一弹错，这里只负责复位提交态
        } finally {
            setSubmitting(false);
        }
    };

    // orderId 缺失：错误态 + 返回入口，不白屏
    if (!orderId) {
        return (
            <PageContainer header={{ title: '卖家发货', breadcrumb: {} }}>
                <Card>
                    <Empty description="链接缺少订单信息，无法发货">
                        <Button type="primary" onClick={() => history.push('/user/orders?role=seller')}>
                            返回订单列表
                        </Button>
                    </Empty>
                </Card>
            </PageContainer>
        );
    }

    // 详情加载中
    if (loading || !order) {
        return (
            <PageContainer header={{ title: '卖家发货', breadcrumb: {} }}>
                <div style={{ textAlign: 'center', padding: 100 }}>
                    <Spin />
                </div>
            </PageContainer>
        );
    }

    return (
        <PageContainer header={{ title: '卖家发货', breadcrumb: {} }}>
            <Row gutter={isMobile ? 12 : 16}>
                <Col xs={24} lg={16}>
                    <Card title="商品信息" className="twt-fade-up" style={{ marginBottom: 16 }}>
                        <Space size={12} align="start">
                            <Image
                                src={staticUrl(snap.cover)}
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
                                    {snap.title || '-'}
                                </div>
                                <div style={{ color: marketplaceTokens.color.primary, fontWeight: 600 }}>
                                    {formatPrice(snap.price)}
                                </div>
                            </div>
                        </Space>
                    </Card>
                    <Card title="订单信息" className="twt-fade-up">
                        <Descriptions column={1} size="small">
                            <Descriptions.Item label="订单号">{order.orderNo || '-'}</Descriptions.Item>
                            <Descriptions.Item label="实付金额">
                                <span
                                    style={{
                                        color: marketplaceTokens.color.primary,
                                        fontWeight: 600,
                                    }}
                                >
                                    {formatPrice(order.totalAmount)}
                                </span>
                            </Descriptions.Item>
                            <Descriptions.Item label="买家">{order.buyerNickName || '-'}</Descriptions.Item>
                        </Descriptions>
                    </Card>
                </Col>
                <Col xs={24} lg={8}>
                    <Card title="发货信息" className="twt-fade-up">
                        <Form
                            form={form}
                            layout="vertical"
                            requiredMark={false}
                            initialValues={{ company: '顺丰速运' }}
                            onFinish={handleSubmit}
                        >
                            <Form.Item
                                label="快递公司"
                                name="company"
                                rules={[{ required: true, message: '请选择快递公司' }]}
                            >
                                <Select options={EXPRESS_OPTIONS} placeholder="请选择快递公司" />
                            </Form.Item>
                            <Form.Item
                                label="快递单号"
                                name="no"
                                rules={[
                                    { required: true, message: '请输入快递单号' },
                                    { whitespace: true, message: '快递单号不能为空格' },
                                ]}
                            >
                                <Input placeholder="请输入快递单号" maxLength={64} allowClear />
                            </Form.Item>
                            <Button
                                type="primary"
                                size="large"
                                block
                                className="twt-press"
                                htmlType="submit"
                                loading={submitting}
                                disabled={submitting}
                            >
                                确认发货
                            </Button>
                            <div
                                style={{
                                    marginTop: 8,
                                    fontSize: 12,
                                    color: marketplaceTokens.color.mute,
                                    textAlign: 'center',
                                }}
                            >
                                发货后买家可在订单详情查看物流信息
                            </div>
                        </Form>
                    </Card>
                </Col>
            </Row>
        </PageContainer>
    );
};

export default Ship;
