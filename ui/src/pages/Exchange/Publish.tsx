import UploadImages from '@/components/marketplace/UploadImages';
import { categoryTree, itemPublish } from '@/services/exchange/api';
import { dictCities } from '@/services/marketplace/api';
import {
    CONDITION_OPTIONS,
    DELIVERY_MODE_MAP,
    DELIVERY_MODE_OPTIONS,
} from './utils';
import { PageContainer } from '@ant-design/pro-components';
import { history } from '@umijs/max';
import type { UploadFile } from 'antd/es/upload/interface';
import {
    Alert,
    Button,
    Card,
    Cascader,
    Col,
    Form,
    Input,
    InputNumber,
    Radio,
    Row,
    Select,
    Space,
    message,
} from 'antd';
import React, { useEffect, useState } from 'react';

const { TextArea } = Input;

interface FormValues {
    title: string;
    images?: UploadFile[];
    cat: string[];
    conditionLevel: string;
    estValue?: number;
    wantCats?: string[];
    wantDesc?: string;
    acceptAny: number;
    deliveryMode: string;
    city?: string;
    location?: string;
    description?: string;
}

/**
 * 发布换物物品（镜像 marketplace/ProductPublish 的类目/成色/图片上传交互 +
 * Runner/Publish 的表单与提交惯例）。发布即进 AI 初审（auditing）。
 */
const Publish: React.FC = () => {
    const [form] = Form.useForm<FormValues>();
    const [submitting, setSubmitting] = useState(false);
    const [catOptions, setCatOptions] = useState<any[]>([]);
    const [cityOptions, setCityOptions] = useState<string[]>([]);

    // 字典：换物类目树（/exchange/category/tree）+ 城市列表（复用商品字典）
    useEffect(() => {
        categoryTree()
            .then((r: any) => setCatOptions(r?.data ?? r ?? []))
            .catch(() => setCatOptions([]));
        dictCities()
            .then((r: any) => setCityOptions(r?.data ?? r ?? []))
            .catch(() => setCityOptions([]));
    }, []);

    /** 从 UploadFile 中提取已上传的 OSS 相对路径（与 ProductPublish.getImagePath 同款） */
    function getImagePath(f: UploadFile): string | undefined {
        const { response } = f as any;
        if (response) {
            // commonUpload 返回 { code: 200, data: "oss相对路径" }
            const data = response.data ?? response.imgUrl;
            if (typeof data === 'string') return data;
        }
        return undefined;
    }

    const onSubmit = async (values: FormValues) => {
        setSubmitting(true);
        try {
            // 从已上传的文件中提取 OSS 相对路径
            const paths = (values.images || [])
                .filter((f: UploadFile) => f.status === 'done')
                .map(getImagePath)
                .filter(Boolean) as string[];

            const cat = values.cat || [];
            const payload: Record<string, any> = {
                title: values.title,
                cover: paths[0] || null,
                images: paths.length > 0 ? JSON.stringify(paths) : null,
                catL1: cat[0] || '',
                catL2: cat[1] || null,
                conditionLevel: values.conditionLevel,
                estValue: values.estValue ?? null,
                wantCats:
                    values.wantCats && values.wantCats.length > 0
                        ? JSON.stringify(values.wantCats)
                        : null,
                wantDesc: values.wantDesc || null,
                acceptAny: values.acceptAny,
                deliveryMode: values.deliveryMode,
                city: values.city || null,
                location: values.location || null,
                description: values.description || null,
            };
            const r: any = await itemPublish(payload);
            const data = r?.data ?? r;
            message.success('发布成功，已进入 AI 初审，通过后将自动上架');
            if (data?.itemId) {
                history.push(`/exchange/detail/${data.itemId}`);
            } else {
                history.push('/exchange/my-items');
            }
        } catch (e: any) {
            message.error(e?.msg || e?.message || '发布失败');
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <PageContainer
            header={{ title: '发布换物物品', subTitle: 'AI 初审通过后自动上架换物中', breadcrumb: {} }}
        >
            <Card style={{ maxWidth: 800, margin: '0 auto' }}>
                <Form
                    form={form}
                    layout="vertical"
                    onFinish={onSubmit}
                    initialValues={{
                        acceptAny: 0,
                        deliveryMode: 'face',
                    }}
                >
                    <Form.Item
                        label="物品标题"
                        name="title"
                        rules={[{ required: true, max: 100, message: '请填写标题（最多 100 字）' }]}
                    >
                        <Input placeholder="例：Kindle Paperwhite 5 95新 带保护套" maxLength={100} showCount />
                    </Form.Item>

                    <Form.Item name="cat" label="物品类目" rules={[{ required: true, message: '请选择类目' }]}>
                        <Cascader
                            options={catOptions}
                            fieldNames={{ label: 'name', value: 'code', children: 'children' }}
                            placeholder="选择一级/二级分类"
                            changeOnSelect
                            style={{ width: '100%' }}
                        />
                    </Form.Item>

                    <Form.Item
                        name="conditionLevel"
                        label="成色"
                        rules={[{ required: true, message: '请选择成色' }]}
                    >
                        <Select options={CONDITION_OPTIONS} placeholder="选择成色" />
                    </Form.Item>

                    {/* 图片 */}
                    <Form.Item name="images" label="物品图片" extra="第一张将作为封面图">
                        <UploadImages max={9} />
                    </Form.Item>

                    <Row gutter={16}>
                        <Col xs={24} md={12}>
                            <Form.Item
                                name="estValue"
                                label="自估参考价值 (¥)"
                                extra="仅展示参考，不构成交易价格"
                            >
                                <InputNumber
                                    min={0}
                                    max={999999}
                                    precision={2}
                                    prefix="¥"
                                    style={{ width: '100%' }}
                                    placeholder="0.00"
                                />
                            </Form.Item>
                        </Col>
                        <Col xs={24} md={12}>
                            <Form.Item name="deliveryMode" label="交付方式" rules={[{ required: true }]}>
                                <Radio.Group>
                                    {DELIVERY_MODE_OPTIONS.map((o) => (
                                        <Radio.Button key={o.value} value={o.value}>
                                            {DELIVERY_MODE_MAP[o.value]}
                                        </Radio.Button>
                                    ))}
                                </Radio.Group>
                            </Form.Item>
                        </Col>
                    </Row>

                    {/* 期望换什么 */}
                    <Form.Item
                        name="wantCats"
                        label="期望换得的类目"
                        extra="可多选，不选 = 不限类目"
                    >
                        <Select
                            mode="multiple"
                            allowClear
                            placeholder="想换什么类目的物品"
                            maxTagCount="responsive"
                            options={catOptions.map((c: any) => ({ value: c.code, label: c.name }))}
                        />
                    </Form.Item>

                    <Form.Item
                        name="wantDesc"
                        label="期望换什么"
                        extra="自由描述你想换到的物品，便于对方洽谈（AI 匹配的原料）"
                    >
                        <TextArea rows={3} placeholder="例：想换一个降噪耳机或机械键盘" maxLength={500} showCount />
                    </Form.Item>

                    <Form.Item name="acceptAny" label="洽谈范围" rules={[{ required: true }]}>
                        <Radio.Group>
                            <Radio value={0}>仅限期望范围</Radio>
                            <Radio value={1}>接受任意洽谈</Radio>
                        </Radio.Group>
                    </Form.Item>

                    {/* 面交区域 */}
                    <Row gutter={16}>
                        <Col xs={24} md={12}>
                            <Form.Item name="city" label="所在城市">
                                <Select
                                    options={cityOptions.map((c) => ({ label: c, value: c }))}
                                    placeholder="选择城市"
                                    allowClear
                                    showSearch
                                />
                            </Form.Item>
                        </Col>
                        <Col xs={24} md={12}>
                            <Form.Item
                                name="location"
                                label="面交区域描述"
                                extra="选择面交时填写，如：福田区 XX 社区"
                            >
                                <Input placeholder="例：福田区 XX 社区" maxLength={200} />
                            </Form.Item>
                        </Col>
                    </Row>

                    <Form.Item name="description" label="物品描述">
                        <TextArea
                            rows={6}
                            placeholder="描述物品的使用情况、配件、购买渠道等信息"
                            maxLength={2000}
                            showCount
                        />
                    </Form.Item>

                    <Alert
                        message="发布后进入 AI 初审：低风险自动上架，高风险自动驳回并通知，中风险转人工审核"
                        type="info"
                        showIcon
                        style={{ marginBottom: 16 }}
                    />

                    <Form.Item>
                        <Space style={{ width: '100%', justifyContent: 'flex-end' }}>
                            <Button onClick={() => history.back()}>取消</Button>
                            <Button type="primary" htmlType="submit" loading={submitting}>
                                提交发布
                            </Button>
                        </Space>
                    </Form.Item>
                </Form>
            </Card>
        </PageContainer>
    );
};

export default Publish;
