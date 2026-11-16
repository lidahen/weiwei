import { paymentMockPay, taskPublish } from '@/services/runner/api';
import { PageContainer } from '@ant-design/pro-components';
import { history, useSearchParams } from '@umijs/max';
import {
    Alert,
    Button,
    Card,
    Col,
    DatePicker,
    Form,
    Input,
    InputNumber,
    QRCode,
    Radio,
    Row,
    Space,
    Typography,
    message,
} from 'antd';
import dayjs, { Dayjs } from 'dayjs';
import React, { useEffect, useState } from 'react';
import { TASK_TYPE_MAP, formatPrice } from './utils';

const { TextArea } = Input;
const { Text, Title } = Typography;

interface FormValues {
    taskType: number;
    title: string;
    description?: string;
    pickupAddr: string;
    deliverAddr: string;
    deadline: Dayjs;
    expectTime?: Dayjs;
    isPaid: number;
    price?: number;
}

const Publish: React.FC = () => {
    const [params] = useSearchParams();
    const [form] = Form.useForm<FormValues>();
    const [submitting, setSubmitting] = useState(false);
    const [payResult, setPayResult] = useState<{
        taskId: number;
        payNo?: string;
        qrCode?: string;
        payUrl?: string;
        amount?: number;
        /** dev 模拟支付模式（后端 billing.pay.mock=true）：跳过真实网关 */
        mock?: boolean;
    } | null>(null);
    const [mockPaying, setMockPaying] = useState(false);

    // 失败回填（任务详情跳转过来）
    useEffect(() => {
        const taskId = params.get('taskId');
        const reason = params.get('reason');
        if (reason) {
            message.warning(`上一次操作失败：${reason}`);
        }
        if (taskId) {
            // 简化：只回填任务 ID
            console.log('Republish flow: taskId=', taskId);
        }
    }, [params]);

    const onSubmit = async (values: FormValues) => {
        setSubmitting(true);
        try {
            const payload: Record<string, any> = {
                taskType: values.taskType,
                title: values.title,
                description: values.description,
                pickupAddr: values.pickupAddr,
                deliverAddr: values.deliverAddr,
                // 后端 LocalDateTime 全局约定格式：yyyy-MM-dd HH:mm:ss（本地墙钟时间）
                // 不能用 toISOString()：ISO 串带 "T" 和 "Z"，Jackson 解析失败（500）
                deadline: values.deadline.format('YYYY-MM-DD HH:mm:ss'),
                expectTime: values.expectTime
                    ? values.expectTime.format('YYYY-MM-DD HH:mm:ss')
                    : undefined,
                isPaid: values.isPaid,
                price: values.isPaid === 1 ? values.price : 0,
            };
            const r: any = await taskPublish(payload);
            const data = r?.data ?? r;
            const taskId = data?.taskId;
            const isPaid = data?.isPaid;
            const pay = data?.pay;
            if (isPaid === 1) {
                // 后端支付单创建成功才渲染扫码页（payNo 是支付单唯一标识）
                if (pay?.payNo && !pay?.error) {
                    setPayResult({
                        taskId,
                        payNo: pay.payNo,
                        qrCode: pay.qrCode,
                        payUrl: pay.payUrl,
                        amount: pay.amount ?? values.price,
                        mock: pay.mock === true,
                    });
                    message.success('任务已发布，请扫码完成支付');
                } else {
                    // 任务已落库，仅支付单创建失败 → 引导去「我发布的」重新支付
                    message.warning(
                        data?.payMessage ||
                            pay?.error ||
                            '任务已发布，但支付单创建失败，请在「我发布的」里重新支付',
                    );
                    history.push(`/runner/task/my-list`);
                }
            } else {
                message.success('任务已发布');
                history.push(`/runner/task/my-list`);
            }
        } catch (e: any) {
            message.error(e?.msg || e?.message || '发布失败');
        } finally {
            setSubmitting(false);
        }
    };

    // 模拟支付成功（dev mock 模式：后端同步标记支付单已付 + 发布支付成功事件）
    const onMockPay = async () => {
        if (!payResult?.payNo) return;
        setMockPaying(true);
        try {
            const r: any = await paymentMockPay(payResult.payNo);
            if (r?.code && r.code !== 200) {
                message.error(r.msg || '模拟支付失败');
                return;
            }
            message.success('模拟支付成功，任务已进入互助大厅');
            history.push('/runner/task/my-list');
        } catch (e: any) {
            message.error(e?.msg || e?.message || '模拟支付失败');
        } finally {
            setMockPaying(false);
        }
    };

    // 支付二维码页
    if (payResult) {
        return (
            <PageContainer header={{ title: '扫码支付', breadcrumb: {} }}>
                <Card style={{ maxWidth: 480, margin: '0 auto' }}>
                    <Space direction="vertical" align="center" style={{ width: '100%' }}>
                        <Title level={4}>任务发布成功</Title>
                        <Text type="secondary">任务 ID：{payResult.taskId}</Text>
                        <Text type="secondary">支付单号：{payResult.payNo || '-'}</Text>
                        {payResult.qrCode && (
                            <QRCode value={payResult.qrCode} size={220} style={{ marginTop: 16 }} />
                        )}
                        <Text strong style={{ fontSize: 18, marginTop: 8 }}>
                            应付金额：{formatPrice(payResult.amount)}
                        </Text>
                        <Alert
                            message="支付完成后任务会出现在互助大厅"
                            type="info"
                            showIcon
                            style={{ width: '100%' }}
                        />
                        {payResult.mock && (
                            <Alert
                                message="当前为开发模拟支付模式（billing.pay.mock=true），不会真实收款"
                                type="warning"
                                showIcon
                                style={{ width: '100%' }}
                            />
                        )}
                        <Space>
                            {payResult.mock ? (
                                <Button type="primary" loading={mockPaying} onClick={onMockPay}>
                                    模拟支付成功（dev）
                                </Button>
                            ) : (
                                <Button
                                    type="primary"
                                    onClick={() =>
                                        history.push(
                                            `/runner/task/my-list?payNo=${payResult.payNo}`,
                                        )
                                    }
                                >
                                    我已支付
                                </Button>
                            )}
                            <Button onClick={() => history.push('/runner/task/my-list')}>
                                稍后支付
                            </Button>
                        </Space>
                    </Space>
                </Card>
            </PageContainer>
        );
    }

    return (
        <PageContainer
            header={{
                title: '发布互助任务',
                subTitle: '代取快递 / 代买东西 / 代排队',
                breadcrumb: {},
            }}
        >
            <Card>
                <Form
                    form={form}
                    layout="vertical"
                    onFinish={onSubmit}
                    initialValues={{
                        taskType: 1,
                        isPaid: 1,
                        deadline: dayjs().add(2, 'hour'),
                    }}
                >
                    <Form.Item
                        label="任务类型"
                        name="taskType"
                        rules={[{ required: true, message: '请选择任务类型' }]}
                    >
                        <Radio.Group>
                            {Object.entries(TASK_TYPE_MAP).map(([k, v]) => (
                                <Radio.Button key={k} value={Number(k)}>
                                    {v.emoji} {v.label}
                                </Radio.Button>
                            ))}
                        </Radio.Group>
                    </Form.Item>

                    <Form.Item
                        label="任务标题"
                        name="title"
                        rules={[{ required: true, max: 100, message: '请填写标题（最多 100 字）' }]}
                    >
                        <Input placeholder="如：取中通快递到 XX 小区" maxLength={100} showCount />
                    </Form.Item>

                    <Form.Item label="详细描述" name="description">
                        <TextArea
                            rows={3}
                            placeholder="补充说明（物品大小 / 紧急程度等）"
                            maxLength={500}
                            showCount
                        />
                    </Form.Item>

                    <Row gutter={16}>
                        <Col xs={24} md={12}>
                            <Form.Item
                                label="取件地址"
                                name="pickupAddr"
                                rules={[{ required: true, message: '请填写取件地址' }]}
                            >
                                <Input placeholder="如：中通快递 XX 营业点" />
                            </Form.Item>
                        </Col>
                        <Col xs={24} md={12}>
                            <Form.Item
                                label="送达地址"
                                name="deliverAddr"
                                rules={[{ required: true, message: '请填写送达地址' }]}
                            >
                                <Input placeholder="如：XX 小区 3 栋 2 单元" />
                            </Form.Item>
                        </Col>
                    </Row>

                    <Row gutter={16}>
                        <Col xs={24} md={12}>
                            <Form.Item
                                label="接单截止时间"
                                name="deadline"
                                rules={[{ required: true, message: '请选择截止时间' }]}
                            >
                                <DatePicker
                                    showTime
                                    style={{ width: '100%' }}
                                    disabledDate={(d) => d && d.isBefore(dayjs(), 'minute')}
                                />
                            </Form.Item>
                        </Col>
                        <Col xs={24} md={12}>
                            <Form.Item label="期望完成时间" name="expectTime">
                                <DatePicker
                                    showTime
                                    style={{ width: '100%' }}
                                    disabledDate={(d) => d && d.isBefore(dayjs(), 'minute')}
                                />
                            </Form.Item>
                        </Col>
                    </Row>

                    <Form.Item label="付费方式" name="isPaid" rules={[{ required: true }]}>
                        <Radio.Group>
                            <Radio value={1}>付费（推荐）</Radio>
                            <Radio value={0}>免费（无报酬）</Radio>
                        </Radio.Group>
                    </Form.Item>

                    <Form.Item shouldUpdate={(p, c) => p.isPaid !== c.isPaid} noStyle>
                        {({ getFieldValue }) => {
                            const isPaid = getFieldValue('isPaid');
                            return isPaid === 1 ? (
                                <Form.Item
                                    label="报酬金额（元）"
                                    name="price"
                                    rules={[
                                        { required: true, message: '请填写报酬' },
                                        {
                                            validator(_, v) {
                                                if (v === undefined || v === null)
                                                    return Promise.resolve();
                                                if (v <= 0)
                                                    return Promise.reject(
                                                        new Error('报酬必须 > 0'),
                                                    );
                                                if (v > 1000)
                                                    return Promise.reject(
                                                        new Error('单次最多 1000 元'),
                                                    );
                                                return Promise.resolve();
                                            },
                                        },
                                    ]}
                                >
                                    <InputNumber
                                        min={0.01}
                                        max={1000}
                                        step={0.5}
                                        precision={2}
                                        style={{ width: 200 }}
                                        addonAfter="元"
                                    />
                                </Form.Item>
                            ) : null;
                        }}
                    </Form.Item>

                    <Form.Item>
                        <Space>
                            <Button type="primary" htmlType="submit" loading={submitting}>
                                立即发布
                            </Button>
                            <Button onClick={() => history.back()}>取消</Button>
                        </Space>
                    </Form.Item>
                </Form>
            </Card>
        </PageContainer>
    );
};

export default Publish;
