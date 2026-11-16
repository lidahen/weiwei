import EmptyState from '@/components/marketplace/EmptyState';
import { addressList, addressRemove, addressSave, dictCities } from '@/services/marketplace/api';
import { PlusOutlined } from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import { useRequest } from '@umijs/max';
import { useIsMobile } from '@/hooks/useIsMobile';
import {
    Button,
    Card,
    Form,
    Input,
    List,
    message,
    Modal,
    Popconfirm,
    Select,
    Spin,
    Tag,
} from 'antd';
import React, { useState } from 'react';

const UserAddress: React.FC = () => {
    const [modalOpen, setModalOpen] = useState(false);
    const [form] = Form.useForm();
    const isMobile = useIsMobile();

    const { data, loading, refresh } = useRequest(addressList);
    const list: any[] = ((data as any)?.data ?? (data as any)) || [];

    const { data: cityResp } = useRequest(dictCities);
    const cities: string[] = ((cityResp as any)?.data ?? (cityResp as any)) || [];

    const handleSave = async () => {
        try {
            const values = await form.validateFields();
            await addressSave(values);
            message.success('保存成功');
            setModalOpen(false);
            form.resetFields();
            refresh();
        } catch (e) {
            // form 校验失败的也会进来,不一定是真错
        }
    };

    const handleRemove = async (id: number) => {
        try {
            await addressRemove(id);
            message.success('已删除');
            refresh();
        } catch (e) {
            message.error('删除失败');
        }
    };

    return (
        <PageContainer
            header={{
                title: '收货地址',
                breadcrumb: {},
                extra: [
                    <Button
                        key="add"
                        type="primary"
                        icon={<PlusOutlined />}
                        onClick={() => setModalOpen(true)}
                    >
                        新增地址
                    </Button>,
                ],
            }}
        >
            {loading ? (
                <div style={{ textAlign: 'center', padding: 60 }}>
                    <Spin />
                </div>
            ) : list.length === 0 ? (
                <EmptyState
                    text="还没有收货地址"
                    ctaText="新增地址"
                    onCtaClick={() => setModalOpen(true)}
                />
            ) : (
                <List
                    // 移动端（复查报告 P1-5）：单列铺满，避免收件人/手机号/删除按钮挤爆
                    grid={{ gutter: 16, column: isMobile ? 1 : 2 }}
                    dataSource={list}
                    renderItem={(item: any) => (
                        <List.Item>
                            <Card
                                title={
                                    <span>
                                        {item.receiver}{' '}
                                        <span
                                            style={{
                                                color: '#999',
                                                marginLeft: 8,
                                                fontWeight: 'normal',
                                            }}
                                        >
                                            {item.mobile}
                                        </span>
                                        {item.isDefault === 1 && (
                                            <Tag color="orange" style={{ marginLeft: 8 }}>
                                                默认
                                            </Tag>
                                        )}
                                    </span>
                                }
                                extra={
                                    <Popconfirm
                                        title="确定删除这个地址?"
                                        onConfirm={() => handleRemove(item.addressId)}
                                    >
                                        <a style={{ color: '#F87171' }}>删除</a>
                                    </Popconfirm>
                                }
                                style={{ borderRadius: 12 }}
                            >
                                <div style={{ color: '#444' }}>
                                    {item.province} {item.city} {item.district}
                                </div>
                                <div style={{ marginTop: 8 }}>{item.detail}</div>
                            </Card>
                        </List.Item>
                    )}
                />
            )}

            <Modal
                title="新增收货地址"
                open={modalOpen}
                onCancel={() => setModalOpen(false)}
                onOk={handleSave}
                okText="保存"
                cancelText="取消"
            >
                <Form
                    form={form}
                    layout="vertical"
                    initialValues={{ isDefault: 0, province: '广东省' }}
                >
                    <Form.Item
                        label="收件人"
                        name="receiver"
                        rules={[{ required: true, message: '请输入收件人' }]}
                    >
                        <Input placeholder="收件人姓名" />
                    </Form.Item>
                    <Form.Item
                        label="手机号"
                        name="mobile"
                        rules={[
                            { required: true, message: '请输入手机号' },
                            { pattern: /^1\d{10}$/, message: '手机号格式不正确' },
                        ]}
                    >
                        <Input placeholder="11 位手机号" />
                    </Form.Item>
                    <Form.Item label="省份" name="province" rules={[{ required: true }]}>
                        <Input placeholder="例: 广东省" />
                    </Form.Item>
                    <Form.Item
                        label="城市"
                        name="city"
                        rules={[{ required: true, message: '请选择城市' }]}
                    >
                        <Select
                            placeholder="选择城市"
                            options={cities.map((c) => ({ label: c, value: c }))}
                        />
                    </Form.Item>
                    <Form.Item label="区/县" name="district">
                        <Input placeholder="例: 南山区" />
                    </Form.Item>
                    <Form.Item
                        label="详细地址"
                        name="detail"
                        rules={[{ required: true, message: '请输入详细地址' }]}
                    >
                        <Input.TextArea rows={2} placeholder="街道、楼栋、门牌号" />
                    </Form.Item>
                    <Form.Item label="是否默认" name="isDefault">
                        <Select
                            options={[
                                { label: '设为默认地址', value: 1 },
                                { label: '不设为默认', value: 0 },
                            ]}
                        />
                    </Form.Item>
                </Form>
            </Modal>
        </PageContainer>
    );
};

export default UserAddress;
