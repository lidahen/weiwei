import { payStatus } from '@/services/marketplace/api';
import { formatPrice, formatTime } from './utils';
import { PageContainer } from '@ant-design/pro-components';
import { Button, Descriptions, Result, Spin, Typography } from 'antd';
import { history, useSearchParams } from '@umijs/max';
import React, { useEffect, useState } from 'react';
import { marketplaceTokens } from './styles/tokens';
import { useIsMobile } from '@/hooks/useIsMobile';

const { Text } = Typography;

const PayResult: React.FC = () => {
    const [params] = useSearchParams();
    const payNo = params.get('payNo');

    const [loading, setLoading] = useState(true);
    const [data, setData] = useState<any>(null);
    const isMobile = useIsMobile();

    useEffect(() => {
        const fetch = async () => {
            if (!payNo) {
                setLoading(false);
                return;
            }
            try {
                const r: any = await payStatus(payNo);
                // 后端 JsonResult<Map>：trade_pay 行在 data 字段（snake_case 键）
                setData(r?.data ?? r);
            } catch {
                // ignore
            } finally {
                setLoading(false);
            }
        };
        fetch();
    }, [payNo]);

    if (loading) {
        return (
            <PageContainer header={{ title: '支付结果', breadcrumb: {} }}>
                <div style={{ textAlign: 'center', padding: '100px 0' }}>
                    <Spin size="large" tip="加载中..." />
                </div>
            </PageContainer>
        );
    }

    if (!payNo) {
        return (
            <PageContainer header={{ title: '支付结果', breadcrumb: {} }}>
                <Result
                    status="info"
                    title="缺少支付单号"
                    subTitle="请从收银台或订单列表进入本页"
                    extra={
                        <Button type="primary" onClick={() => history.push('/user/orders')}>
                            查看订单
                        </Button>
                    }
                    style={{ maxWidth: 520, margin: '60px auto' }}
                />
            </PageContainer>
        );
    }

    // 后端支付单状态：paid / unpaid / closed / failed（status 字段 + paid 布尔兜底）
    const isSuccess = data?.status === 'paid' || data?.paid === true;

    return (
        <PageContainer header={{ title: '支付结果', breadcrumb: {} }}>
            <Result
                status={isSuccess ? 'success' : 'error'}
                title={isSuccess ? '支付成功' : '支付未完成'}
                subTitle={`支付单号: ${payNo}`}
                extra={[
                    <Button type="primary" key="orders" className="twt-press" onClick={() => history.push('/user/orders')}>
                        查看订单
                    </Button>,
                    <Button key="home" onClick={() => history.push('/')}>
                        返回首页
                    </Button>,
                ]}
                style={{ maxWidth: 520, margin: '60px auto' }}
            />
            {data && (
                <Descriptions
                    bordered
                    column={1}
                    size="small"
                    style={{ maxWidth: isMobile ? '100%' : 520, margin: '0 auto' }}
                    className="twt-fade-up"
                >
                    <Descriptions.Item label="订单 ID">{data.order_id ?? '-'}</Descriptions.Item>
                    <Descriptions.Item label="支付金额">
                        <span style={{ color: marketplaceTokens.color.primary, fontWeight: 600 }}>
                            {formatPrice(data.pay_amount)}
                        </span>
                    </Descriptions.Item>
                    <Descriptions.Item label="支付方式">
                        {data.pay_channel === 'wechat' ? '微信支付' : '支付宝'}
                    </Descriptions.Item>
                    <Descriptions.Item label="支付状态">
                        <Text type={isSuccess ? 'success' : 'warning'}>
                            {isSuccess ? '已支付' : '未支付'}
                        </Text>
                    </Descriptions.Item>
                    {data.paid_at && (
                        <Descriptions.Item label="支付时间">{formatTime(data.paid_at)}</Descriptions.Item>
                    )}
                    <Descriptions.Item label="商户单号">{data.out_trade_no || payNo}</Descriptions.Item>
                </Descriptions>
            )}
        </PageContainer>
    );
};

export default PayResult;
