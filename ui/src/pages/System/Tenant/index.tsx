import React, { useEffect, useRef, useState } from 'react';

import { proTableConfigs } from '@/setting';
import { auth, system } from '@/utils/twelvet';
import { ReloadOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import {
    Button,
    Descriptions,
    Drawer,
    Form,
    InputNumber,
    Modal,
    QRCode,
    Radio,
    Select,
    Space,
    Table,
    Tag,
    message,
} from 'antd';
import dayjs from 'dayjs';
import {
    createRenewOrder,
    getAiUsage,
    getRenewStatus,
    listAll,
    mockPayRenew,
    renew,
} from './service';

// 套餐下拉项（与后端 PlanCatalog 对齐，价格见 Plan.monthlyPrice）
const PLAN_OPTIONS = [
    { value: 'free', label: '免费版（100 商品 / 50 AI·日）— ¥0', quota: 100 },
    { value: 'basic', label: '基础版（1000 商品 / 500 AI·日）— ¥99/月', quota: 1000 },
    { value: 'pro', label: 'Pro 版（不限）— ¥299/月', quota: 0 },
];

// 套餐配色
const planColor = (code?: string) => {
    switch (code) {
        case 'pro':
            return 'gold';
        case 'basic':
            return 'blue';
        case 'free':
        default:
            return 'default';
    }
};

// AI 配额展示（与商品配额同款配色语义）
const renderAiQuota = (max?: number, used?: number) => {
    const quota = max ?? 0;
    if (quota === 0) {
        return (
            <span>
                <span style={{ color: '#52c41a', fontWeight: 500 }}>不限</span>
                <span style={{ color: '#999', marginLeft: 8 }}>今日 {used ?? 0} 次</span>
            </span>
        );
    }
    const ratio = (used ?? 0) / quota;
    const color = ratio >= 1 ? '#ff4d4f' : ratio >= 0.8 ? '#faad14' : '#52c41a';
    return (
        <span style={{ color, fontWeight: 500 }}>
            {used ?? 0} / {quota}
        </span>
    );
};

const Tenant: React.FC = () => {
    const [renewModal, setRenewModal] = useState<{
        visible: boolean;
        record?: SystemTenant.PageListItem;
    }>({ visible: false });

    const [loadingModal, setLoadingModal] = useState<boolean>(false);

    // 扫码续费状态（P2）：form 表单阶段 → qr 等待支付阶段
    const [payState, setPayState] = useState<{
        step: 'form' | 'qr';
        payNo?: string;
        qrCode?: string;
        mock?: boolean;
        amount?: number;
    }>({ step: 'form' });

    const [mockPaying, setMockPaying] = useState<boolean>(false);

    const [detailModal, setDetailModal] = useState<{
        visible: boolean;
        record?: SystemTenant.PageListItem;
    }>({ visible: false });

    // 租户详情弹窗：弹窗与状态早已存在（见文件末尾 <Modal title="租户详情">），
    // 此前唯独缺这个处理函数，导致「详情」按钮点击必抛
    // ReferenceError: openDetail is not defined —— tsc 能捕获，但 max build 不做类型检查。
    const openDetail = (row: SystemTenant.PageListItem) => {
        setDetailModal({ visible: true, record: row });
    };

    // AI 用量 Drawer（近 30 天明细 + 合计）
    const [usageDrawer, setUsageDrawer] = useState<{
        visible: boolean;
        tenantCode?: string;
        loading: boolean;
        rows: { statDate: string; calls: number; promptTokens: number; completionTokens: number }[];
        total?: { calls: number; promptTokens: number; completionTokens: number };
    }>({ visible: false, loading: false, rows: [] });

    const openUsage = async (row: SystemTenant.PageListItem) => {
        setUsageDrawer({ visible: true, tenantCode: row.tenantCode, loading: true, rows: [] });
        try {
            const { data } = await getAiUsage(row.tenantCode, 30);
            setUsageDrawer({
                visible: true,
                tenantCode: row.tenantCode,
                loading: false,
                rows: data?.rows ?? [],
                total: data?.total,
            });
        } catch (e) {
            setUsageDrawer((prev) => ({ ...prev, loading: false }));
            message.error('AI 用量拉取失败');
        }
    };

    const acForm = useRef<ActionType>();
    const [form] = Form.useForm();
    const pollTimer = useRef<ReturnType<typeof setInterval>>();

    const formItemLayout = {
        labelCol: { xs: { span: 6 }, sm: { span: 5 } },
        wrapperCol: { xs: { span: 16 }, sm: { span: 16 } },
    };

    // 轮询清理：弹窗关闭/组件卸载
    const stopPolling = () => {
        if (pollTimer.current) {
            clearInterval(pollTimer.current);
            pollTimer.current = undefined;
        }
    };
    useEffect(() => stopPolling, []);

    // Bigfish dev（regenerator 转译）环境实测：弹窗 open 状态偶发不随 setState 关闭/重开。
    // 状态更新保留（正确路径），另加 DOM 兜底强制开关（两端都做，状态正常时该操作无副作用）。
    const forceModalDom = (visible: boolean) => {
        setTimeout(() => {
            const wrap = document.querySelector<HTMLElement>('.ant-modal-wrap');
            if (wrap) {
                const cur = getComputedStyle(wrap).display;
                const want = visible ? 'block' : 'none';
                if (cur !== want) {
                    wrap.style.display = want;
                }
            }
        }, 120);
    };

    /**
     * 打开续费弹窗
     */
    const openRenew = (record: SystemTenant.PageListItem) => {
        form.resetFields();
        form.setFieldsValue({
            planCode: record.planCode,
            months: 1,
            payMode: 'direct',
            channel: 'alipay',
        });
        setPayState({ step: 'form' });
        stopPolling();
        setRenewModal({ visible: true, record });
        forceModalDom(true);
    };

    /**
     * 关闭续费弹窗
     */
    const closeRenew = () => {
        stopPolling();
        setRenewModal({ visible: false });
        setPayState({ step: 'form' });
        forceModalDom(false);
    };

    /**
     * 开始轮询支付状态（2s 一次，最多 90 次 ≈ 3 分钟）
     */
    const startPolling = (payNo: string) => {
        stopPolling();
        let tries = 0;
        pollTimer.current = setInterval(async () => {
            tries += 1;
            if (tries > 90) {
                stopPolling();
                message.warning('查询超时，请稍后手动刷新查看');
                return;
            }
            try {
                const { code, data } = await getRenewStatus(payNo);
                if (code === 200 && data?.applied) {
                    stopPolling();
                    message.success(
                        `支付成功：${data.tenantCode} → ${data.planCode?.toUpperCase()} +${
                            data.months
                        }月`,
                    );
                    setPayState({ step: 'form' });
                    // 函数式更新：避免与轮询闭包外的状态竞态（实测直接赋值偶发不关闭）
                    setRenewModal((prev) => ({ ...prev, visible: false }));
                    forceModalDom(false);
                    acForm?.current?.reload();
                }
            } catch (e) {
                // 轮询失败静默，下轮继续
            }
        }, 2000);
    };

    /**
     * 提交续费（直接续费 / 扫码支付两分支）
     */
    const onRenew = () => {
        form.validateFields()
            .then(async (fields) => {
                if (!renewModal.record) return;
                try {
                    setLoadingModal(true);
                    if (fields.payMode === 'scan') {
                        const { code, msg, data } = await createRenewOrder(
                            renewModal.record.tenantCode,
                            {
                                planCode: fields.planCode,
                                months: fields.months,
                                channel: fields.channel,
                            },
                        );
                        if (code !== 200) {
                            return message.error(msg);
                        }
                        setPayState({
                            step: 'qr',
                            payNo: data.payNo,
                            qrCode: data.qrCode,
                            mock: data.mock,
                            amount: data.amount,
                        });
                        startPolling(data.payNo);
                        return;
                    }
                    const { code, msg } = await renew(renewModal.record.tenantCode, fields);
                    if (code !== 200) {
                        return message.error(msg);
                    }
                    message.success(
                        `续费成功：${renewModal.record.tenantCode} → ${fields.planCode} +${fields.months}月`,
                    );
                    closeRenew();
                    acForm?.current?.reload();
                } catch (e) {
                    system.error(e);
                } finally {
                    setLoadingModal(false);
                }
            })
            .catch((e) => {
                system.error(e);
            });
    };

    /**
     * 模拟支付成功（dev mock 模式演示闭环）
     * 后端 mock-pay 是同步履约（标支付+续费同一事务），成功即可在事件上下文直接关弹窗刷新，
     * 不依赖轮询闭包里的 setState（实测该路径状态更新偶发不生效）
     */
    const onMockPay = async () => {
        if (!payState.payNo) return;
        try {
            setMockPaying(true);
            const { code, msg, data } = await mockPayRenew(payState.payNo);
            if (code !== 200) {
                return message.error(msg);
            }
            message.success(`支付成功：${renewModal.record?.tenantCode ?? ''} 套餐已续费`);
            stopPolling();
            setPayState({ step: 'form' });
            setRenewModal((prev) => ({ ...prev, visible: false }));
            forceModalDom(false);
            acForm?.current?.reload();
            return data;
        } catch (e) {
            system.error(e);
        } finally {
            setMockPaying(false);
        }
    };

    // 列表列
    const columns: ProColumns<SystemTenant.PageListItem>[] = [
        {
            title: '租户编码',
            width: 140,
            valueType: 'text',
            dataIndex: 'tenantCode',
        },
        {
            title: '租户名称',
            width: 160,
            valueType: 'text',
            dataIndex: 'tenantName',
        },
        {
            title: '套餐',
            width: 100,
            valueType: 'text',
            dataIndex: 'planCode',
            render: (_, row) => (
                <Tag color={planColor(row.planCode)}>{row.planCode?.toUpperCase()}</Tag>
            ),
        },
        {
            title: '到期时间',
            width: 200,
            valueType: 'text',
            dataIndex: 'expireAt',
            render: (_, row) => {
                if (!row.expireAt) return <span style={{ color: '#999' }}>永不过期</span>;
                const exp = dayjs(row.expireAt);
                const expired = exp.isBefore(dayjs());
                return (
                    <span style={{ color: expired ? '#ff4d4f' : undefined }}>
                        {exp.format('YYYY-MM-DD HH:mm:ss')}
                        {expired && (
                            <Tag color="red" style={{ marginLeft: 8 }}>
                                已过期
                            </Tag>
                        )}
                    </span>
                );
            },
        },
        {
            title: '商品配额',
            width: 180,
            valueType: 'text',
            dataIndex: 'quotaProductMax',
            render: (_, row) => {
                const max = row.quotaProductMax ?? 0;
                if (max === 0) return <span style={{ color: '#52c41a' }}>不限</span>;
                const used = row.productCount ?? 0;
                const ratio = used / max;
                const color = ratio >= 1 ? '#ff4d4f' : ratio >= 0.8 ? '#faad14' : '#52c41a';
                return (
                    <span style={{ color, fontWeight: 500 }}>
                        {used} / {max}
                    </span>
                );
            },
        },
        {
            title: 'AI 配额(今日)',
            width: 150,
            valueType: 'text',
            dataIndex: 'quotaAiCallDay',
            render: (_, row) => renderAiQuota(row.quotaAiCallDay, row.aiCallToday),
        },
        {
            title: '操作',
            fixed: 'right',
            width: 200,
            valueType: 'option',
            dataIndex: 'operation',
            render: (_, row) => {
                return (
                    <Space>
                        <a onClick={() => openDetail(row)}>详情</a>
                        <a onClick={() => openUsage(row)}>AI 用量</a>
                        <a onClick={() => openRenew(row)} hidden={auth('system:tenant:edit')}>
                            <ReloadOutlined /> 续费/升级
                        </a>
                    </Space>
                );
            },
        },
    ];

    const payMode = Form.useWatch('payMode', form);

    return (
        <PageContainer>
            <ProTable<SystemTenant.PageListItem>
                {...proTableConfigs}
                pagination={false}
                actionRef={acForm}
                rowKey="tenantCode"
                columns={columns}
                search={false}
                request={async () => {
                    const data = await listAll();
                    return Promise.resolve({
                        data,
                        success: true,
                        total: data.length,
                    });
                }}
                toolBarRender={() => [
                    <Button
                        key="refresh"
                        icon={<ReloadOutlined />}
                        onClick={() => acForm?.current?.reload()}
                    >
                        刷新
                    </Button>,
                ]}
            />

            {/* 续费弹窗（form 表单阶段 / qr 等待支付阶段） */}
            <Modal
                title={`续费 / 升级套餐 — ${renewModal.record?.tenantCode ?? ''}`}
                open={renewModal.visible}
                okText={
                    payState.step === 'form'
                        ? payMode === 'scan'
                            ? '生成支付二维码'
                            : '确认续费'
                        : '关闭并刷新'
                }
                confirmLoading={payState.step === 'form' && loadingModal}
                destroyOnClose
                width={520}
                onOk={() => {
                    if (payState.step === 'qr') {
                        closeRenew();
                        acForm?.current?.reload();
                        return;
                    }
                    onRenew();
                }}
                onCancel={closeRenew}
            >
                {payState.step === 'form' && (
                    <Form name="TenantRenew" form={form}>
                        <Form.Item {...formItemLayout} label="当前套餐">
                            <Tag color={planColor(renewModal.record?.planCode)}>
                                {renewModal.record?.planCode?.toUpperCase() ?? '-'}
                            </Tag>
                            <span style={{ marginLeft: 8, color: '#999' }}>
                                到期：
                                {renewModal.record?.expireAt
                                    ? dayjs(renewModal.record.expireAt).format('YYYY-MM-DD')
                                    : '永不过期'}
                            </span>
                        </Form.Item>

                        <Form.Item
                            {...formItemLayout}
                            label="目标套餐"
                            name="planCode"
                            rules={[{ required: true, message: '请选择套餐' }]}
                        >
                            <Select options={PLAN_OPTIONS} placeholder="选择套餐" />
                        </Form.Item>

                        <Form.Item
                            {...formItemLayout}
                            label="续费月数"
                            name="months"
                            rules={[{ required: true, message: '请输入续费月数' }]}
                        >
                            <InputNumber
                                min={1}
                                max={120}
                                placeholder="月数"
                                style={{ width: '100%' }}
                            />
                        </Form.Item>

                        <Form.Item
                            {...formItemLayout}
                            label="支付方式"
                            name="payMode"
                            rules={[{ required: true }]}
                        >
                            <Radio.Group
                                options={[
                                    { value: 'direct', label: '直接续费（无支付）' },
                                    { value: 'scan', label: '扫码支付' },
                                ]}
                            />
                        </Form.Item>

                        <Form.Item
                            {...formItemLayout}
                            label="支付渠道"
                            name="channel"
                            hidden={payMode !== 'scan'}
                        >
                            <Select
                                options={[
                                    { value: 'alipay', label: '支付宝' },
                                    { value: 'wechat', label: '微信支付' },
                                ]}
                            />
                        </Form.Item>

                        <Form.Item {...formItemLayout} label="说明">
                            <span style={{ color: '#999', fontSize: 12 }}>
                                续费语义：到期时间 = max(当前到期, 现在) +
                                续费月数（提前续费不浪费）
                            </span>
                        </Form.Item>
                    </Form>
                )}

                {payState.step === 'qr' && (
                    <div style={{ textAlign: 'center', padding: '8px 0' }}>
                        <p>
                            应付金额：
                            <span style={{ fontSize: 20, color: '#fa541c', fontWeight: 600 }}>
                                ¥{payState.amount}
                            </span>
                            　请使用对应 App 扫码支付
                        </p>
                        <QRCode value={payState.qrCode ?? ''} size={200} />
                        <p style={{ marginTop: 12, color: '#999' }}>支付单号：{payState.payNo}</p>
                        <p style={{ color: '#999', fontSize: 12 }}>
                            支付成功后本页会自动确认并刷新（约 2 秒轮询一次）
                        </p>
                        {payState.mock && (
                            <Button
                                type="primary"
                                ghost
                                loading={mockPaying}
                                onClick={onMockPay}
                                style={{ marginTop: 8 }}
                            >
                                模拟支付成功（dev）
                            </Button>
                        )}
                    </div>
                )}
            </Modal>

            {/* 详情弹窗 */}
            <Modal
                title={`租户详情 — ${detailModal.record?.tenantCode ?? ''}`}
                open={detailModal.visible}
                footer={<Button onClick={() => setDetailModal({ visible: false })}>关闭</Button>}
                width={600}
                onCancel={() => setDetailModal({ visible: false })}
            >
                {detailModal.record && (
                    <Descriptions column={1} bordered size="small">
                        <Descriptions.Item label="租户编码">
                            {detailModal.record.tenantCode}
                        </Descriptions.Item>
                        <Descriptions.Item label="租户名称">
                            {detailModal.record.tenantName}
                        </Descriptions.Item>
                        <Descriptions.Item label="当前套餐">
                            <Tag color={planColor(detailModal.record.planCode)}>
                                {detailModal.record.planCode?.toUpperCase()}
                            </Tag>
                        </Descriptions.Item>
                        <Descriptions.Item label="到期时间">
                            {detailModal.record.expireAt
                                ? dayjs(detailModal.record.expireAt).format('YYYY-MM-DD HH:mm:ss')
                                : '永不过期'}
                        </Descriptions.Item>
                        <Descriptions.Item label="商品配额">
                            {detailModal.record.quotaProductMax === 0
                                ? '不限'
                                : `${detailModal.record.productCount} / ${detailModal.record.quotaProductMax}`}
                        </Descriptions.Item>
                        <Descriptions.Item label="AI 配额(今日)">
                            {detailModal.record.quotaAiCallDay === 0
                                ? `不限（今日 ${detailModal.record.aiCallToday ?? 0} 次）`
                                : `${detailModal.record.aiCallToday ?? 0} / ${
                                      detailModal.record.quotaAiCallDay
                                  }`}
                        </Descriptions.Item>
                    </Descriptions>
                )}
            </Modal>
            {/* AI 用量 Drawer（近 30 天） */}
            <Drawer
                title={`AI 用量（近 30 天）— ${usageDrawer.tenantCode ?? ''}`}
                open={usageDrawer.visible}
                width={560}
                onClose={() => setUsageDrawer((prev) => ({ ...prev, visible: false }))}
            >
                <Table
                    rowKey="statDate"
                    size="small"
                    loading={usageDrawer.loading}
                    pagination={false}
                    dataSource={usageDrawer.rows}
                    columns={[
                        { title: '日期', dataIndex: 'statDate', width: 120 },
                        { title: '调用次数', dataIndex: 'calls', width: 100, align: 'right' },
                        {
                            title: '输入 tokens',
                            dataIndex: 'promptTokens',
                            width: 120,
                            align: 'right',
                        },
                        {
                            title: '输出 tokens',
                            dataIndex: 'completionTokens',
                            width: 120,
                            align: 'right',
                        },
                    ]}
                    summary={() => (
                        <Table.Summary.Row style={{ fontWeight: 600 }}>
                            <Table.Summary.Cell index={0}>合计</Table.Summary.Cell>
                            <Table.Summary.Cell index={1} align="right">
                                {usageDrawer.total?.calls ?? 0}
                            </Table.Summary.Cell>
                            <Table.Summary.Cell index={2} align="right">
                                {usageDrawer.total?.promptTokens ?? 0}
                            </Table.Summary.Cell>
                            <Table.Summary.Cell index={3} align="right">
                                {usageDrawer.total?.completionTokens ?? 0}
                            </Table.Summary.Cell>
                        </Table.Summary.Row>
                    )}
                />
                {usageDrawer.rows.length === 0 && !usageDrawer.loading && (
                    <div style={{ color: '#999', marginTop: 16 }}>近 30 天暂无 AI 调用记录</div>
                )}
            </Drawer>
        </PageContainer>
    );
};

export default Tenant;
