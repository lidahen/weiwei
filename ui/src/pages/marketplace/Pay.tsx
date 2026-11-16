import { mockPayOrder, payCreate, payStatus } from '@/services/marketplace/api';
import { formatPrice } from './utils';
import { PageContainer } from '@ant-design/pro-components';
import { Button, Card, Descriptions, Empty, message, Result, Space, Spin, Typography } from 'antd';
import { QRCodeCanvas } from 'qrcode.react';
import { history, useSearchParams } from '@umijs/max';
import React, { useEffect, useRef, useState } from 'react';
import { marketplaceTokens } from './styles/tokens';
import { useIsMobile } from '@/hooks/useIsMobile';

const { Text } = Typography;

const POLL_INTERVAL = 3000;
const POLL_MAX = 60; // 3 分钟内自动确认，超时后提供手动刷新

const Pay: React.FC = () => {
    const [params] = useSearchParams();
    const orderId = params.get('orderId');
    // 支付回调页回跳时 URL 会携带 payNo；从订单发起支付时只有 orderId
    const urlPayNo = params.get('payNo');
    const [mockPaying, setMockPaying] = useState(false);
    const isMobile = useIsMobile();

    const [loading, setLoading] = useState(true);
    const [payData, setPayData] = useState<any>(null);
    const [payNo, setPayNo] = useState<string | undefined>(urlPayNo || undefined);
    const [paid, setPaid] = useState(false);
    const [closed, setClosed] = useState<string | null>(null);
    const [failed, setFailed] = useState(false);
    const [expired, setExpired] = useState(false);
    // 手动刷新计数：变化时重启轮询 effect
    const [pollTick, setPollTick] = useState(0);
    const timerRef = useRef<number>();
    const pollCountRef = useRef(0);

    // 创建支付（URL 已带 payNo 的是回调回跳，不重复创建）
    useEffect(() => {
        const init = async () => {
            if (urlPayNo) {
                setLoading(false);
                return;
            }
            if (!orderId) {
                setLoading(false);
                setFailed(true);
                return;
            }
            try {
                const r: any = await payCreate({ orderId, channel: 'alipay' });
                if (r.code === 200) {
                    setPayData(r.data);
                    setPayNo(r.data?.payNo);
                } else {
                    message.error(r.msg || '创建支付失败');
                    setFailed(true);
                }
            } catch {
                setFailed(true);
            } finally {
                setLoading(false);
            }
        };
        init();
        return () => {
            if (timerRef.current) clearInterval(timerRef.current);
        };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [orderId]);

    // 轮询支付状态
    useEffect(() => {
        if (!payNo || paid || failed || closed) return;
        const poll = async () => {
            pollCountRef.current += 1;
            if (pollCountRef.current > POLL_MAX) {
                setExpired(true);
                if (timerRef.current) clearInterval(timerRef.current);
                return;
            }
            try {
                const r: any = await payStatus(payNo);
                const d = r?.data ?? r;
                // 后端支付单状态：unpaid/paid/closed/failed（status 字段 + paid 布尔兜底）
                if (d?.status === 'paid' || d?.paid) {
                    setPaid(true);
                    if (timerRef.current) clearInterval(timerRef.current);
                } else if (d?.status === 'closed' || d?.status === 'failed') {
                    setClosed(d?.fail_reason || d?.failReason || '支付单已关闭');
                    if (timerRef.current) clearInterval(timerRef.current);
                }
            } catch {
                // 忽略轮询瞬时错误
            }
        };

        poll();
        timerRef.current = window.setInterval(poll, POLL_INTERVAL);
        return () => {
            if (timerRef.current) clearInterval(timerRef.current);
        };
    }, [payNo, paid, failed, closed, pollTick]);

    const manualRefresh = () => {
        pollCountRef.current = 0;
        setExpired(false);
        setPollTick((t) => t + 1);
    };

    if (loading) {
        return (
            <PageContainer header={{ title: '收银台', breadcrumb: {} }}>
                <div style={{ textAlign: 'center', padding: '100px 0' }}>
                    <Spin size="large" tip="加载中..." />
                </div>
            </PageContainer>
        );
    }

    if (paid) {
        return (
            <PageContainer header={{ title: '收银台', breadcrumb: {} }}>
                <Result
                    status="success"
                    title="支付成功"
                    subTitle={`支付单号: ${payNo}`}
                    extra={[
                        <Button type="primary" key="orders" className="twt-press" onClick={() => history.push('/user/orders')}>
                            查看订单
                        </Button>,
                        <Button key="home" onClick={() => history.push('/')}>
                            返回首页
                        </Button>,
                    ]}
                    style={{ maxWidth: isMobile ? '100%' : 520, margin: isMobile ? '32px auto' : '60px auto' }}
                />
            </PageContainer>
        );
    }

    if (failed) {
        return (
            <PageContainer header={{ title: '收银台', breadcrumb: {} }}>
                <Result
                    status="500"
                    title="无法发起支付"
                    subTitle="缺少订单信息或创建支付单失败"
                    extra={[
                        <Button type="primary" key="home" onClick={() => history.push('/')}>
                            返回首页
                        </Button>,
                    ]}
                    style={{ maxWidth: isMobile ? '100%' : 520, margin: isMobile ? '32px auto' : '60px auto' }}
                />
            </PageContainer>
        );
    }

    if (closed) {
        return (
            <PageContainer header={{ title: '收银台', breadcrumb: {} }}>
                <Result
                    status="error"
                    title="支付未完成"
                    subTitle={closed}
                    extra={[
                        <Button type="primary" key="orders" onClick={() => history.push('/user/orders')}>
                            查看订单
                        </Button>,
                        <Button key="home" onClick={() => history.push('/')}>
                            返回首页
                        </Button>,
                    ]}
                    style={{ maxWidth: isMobile ? '100%' : 520, margin: isMobile ? '32px auto' : '60px auto' }}
                />
            </PageContainer>
        );
    }

    // 渲染二维码
    const qrCodeContent = payData?.qrCode || payData?.payUrl || payData?.extra?.qrCode || payData?.extra?.codeUrl;

    return (
        <PageContainer header={{ title: '收银台', breadcrumb: {} }}>
            <Card style={{ maxWidth: 500, margin: '40px auto', borderRadius: marketplaceTokens.radius.lg }} className="twt-fade-up">
                <Descriptions bordered column={1}>
                    <Descriptions.Item label="订单号">{payData?.orderNo || '-'}</Descriptions.Item>
                    <Descriptions.Item label="金额">
                        <span style={{ color: marketplaceTokens.color.primary, fontSize: 24, fontWeight: 600 }}>
                            {formatPrice(payData?.amount)}
                        </span>
                    </Descriptions.Item>
                    <Descriptions.Item label="支付方式">
                        {payData?.channel === 'wechat' ? '微信支付' : '支付宝'}
                    </Descriptions.Item>
                </Descriptions>

                <div style={{ textAlign: 'center', marginTop: 24 }}>
                    {qrCodeContent ? (
                        <>
                            <Text type="secondary" style={{ display: 'block', marginBottom: 12 }}>
                                请使用 {payData?.channel === 'wechat' ? '微信' : '支付宝'} 扫描下方二维码付款
                            </Text>
                            <div className="twt-fade-up">
                                <QRCodeCanvas value={qrCodeContent} size={256} level="H" includeMargin />
                            </div>
                            <div style={{ marginTop: 16 }}>
                                {expired ? (
                                    <Space direction="vertical" size={8}>
                                        <Text type="secondary">自动确认已超时，若已完成付款请手动刷新</Text>
                                        <Button size="small" onClick={manualRefresh}>
                                            我已支付，刷新状态
                                        </Button>
                                    </Space>
                                ) : (
                                    <Text type="secondary">付款后请稍候，系统将自动确认...</Text>
                                )}
                                {/* dev 模拟支付：二维码是假链接扫不了，按钮直驱履约闭环（生产 mock=false 不显示） */}
                                {payData?.mock && (
                                    <Button
                                        type="primary"
                                        block
                                        loading={mockPaying}
                                        style={{ marginTop: 12 }}
                                        onClick={async () => {
                                            if (!payNo || mockPaying) return;
                                            setMockPaying(true);
                                            try {
                                                const r: any = await mockPayOrder(payNo);
                                                if (r.code === 200) {
                                                    message.success('模拟支付成功');
                                                    setPaid(true);
                                                } else {
                                                    message.error(r.msg || '模拟支付失败');
                                                }
                                            } catch {
                                                // 全局 errorHandler 已弹错
                                            } finally {
                                                setMockPaying(false);
                                            }
                                        }}
                                    >
                                        模拟支付（开发环境）
                                    </Button>
                                )}
                            </div>
                        </>
                    ) : (
                        <Empty description="暂无支付信息" />
                    )}
                </div>
                {/* 手机端无法扫描自己屏幕上的二维码，提供直接跳转支付链接的入口 */}
                {qrCodeContent && (
                    <Button
                        block
                        style={{ marginTop: 16 }}
                        onClick={() =>
                            window.open(
                                qrCodeContent.startsWith('http') ? qrCodeContent : `https://${qrCodeContent}`,
                                '_blank',
                            )
                        }
                    >
                        在新窗口打开支付链接
                    </Button>
                )}
            </Card>
        </PageContainer>
    );
};

export default Pay;
