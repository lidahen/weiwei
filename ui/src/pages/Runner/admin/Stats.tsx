import { adminStatistics } from '@/services/runner/api';
import { PageContainer } from '@ant-design/pro-components';
import { useRequest } from '@umijs/max';
import { Card, Col, Row, Spin, Statistic } from 'antd';
import React from 'react';

const Stats: React.FC = () => {
    const { data, loading } = useRequest(adminStatistics);

    if (loading) return <Spin />;
    const d: any = (data as any)?.data ?? data ?? {};

    return (
        <PageContainer header={{ title: '互助数据看板', subTitle: '关键指标', breadcrumb: {} }}>
            <Row gutter={16}>
                <Col span={6}>
                    <Card>
                        <Statistic title="任务总数" value={d.totalTasks || 0} valueStyle={{ color: '#3B82F6' }} />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card>
                        <Statistic
                            title="待接单"
                            value={d.pendingCount || 0}
                            valueStyle={{ color: '#FAAD14' }}
                        />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card>
                        <Statistic
                            title="进行中"
                            value={d.activeCount || 0}
                            valueStyle={{ color: '#13C2C2' }}
                        />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card>
                        <Statistic
                            title="已完成"
                            value={d.completedCount || 0}
                            valueStyle={{ color: '#52C41A' }}
                        />
                    </Card>
                </Col>
            </Row>
            <Row gutter={16} style={{ marginTop: 16 }}>
                <Col span={12}>
                    <Card title="累计 GMV（付费任务）">
                        <Statistic
                            value={d.totalGmv || 0}
                            prefix="¥"
                            precision={2}
                            valueStyle={{ color: '#FF6B35', fontSize: 32 }}
                        />
                    </Card>
                </Col>
                <Col span={12}>
                    <Card title="完成率">
                        <Statistic
                            value={
                                d.totalTasks
                                    ? (((d.completedCount || 0) / d.totalTasks) * 100).toFixed(1)
                                    : 0
                            }
                            suffix="%"
                            valueStyle={{ color: '#722ED1' }}
                        />
                    </Card>
                </Col>
            </Row>
        </PageContainer>
    );
};

export default Stats;
