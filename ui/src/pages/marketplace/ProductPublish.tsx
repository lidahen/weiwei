import UploadImages from '@/components/marketplace/UploadImages';
import {
    assistProductCopy,
    assistProductImage,
    assistProductRecognition,
    assistProductValuation,
    dictCategoryTree,
    dictCities,
    productDetail,
    productDraftDelete,
    productDraftGet,
    productDraftSave,
    productPublish,
    productUpdate,
} from '@/services/marketplace/api';
import TWT from '@/setting';
import { PageContainer } from '@ant-design/pro-components';
import { history, useParams } from '@umijs/max';
import {
    Button,
    Card,
    Cascader,
    Form,
    Image,
    Input,
    InputNumber,
    message,
    Radio,
    Select,
    Space,
    Spin,
    Typography,
} from 'antd';
import type { UploadFile } from 'antd/es/upload/interface';
import React, { useEffect, useState } from 'react';

const conditionOptions = [
    { label: '全新', value: 'brand_new' },
    { label: '99新', value: '99' },
    { label: '95新', value: '95' },
    { label: '9成新', value: '90' },
    { label: '8成新', value: '80' },
    { label: '7成及以下', value: '70_lower' },
];

const ProductPublish: React.FC = () => {
    const { id } = useParams<{ id: string }>();
    const isEdit = !!id;
    const [form] = Form.useForm();
    const [submitting, setSubmitting] = useState(false);
    const [catOptions, setCatOptions] = useState<any[]>([]);
    const [cityOptions, setCityOptions] = useState<string[]>([]);
    const [detailResp, setDetailResp] = useState<any>(null);
    const [detailLoading, setDetailLoading] = useState(isEdit);
    const [aiCopyLoading, setAiCopyLoading] = useState(false);
    const [aiImageLoading, setAiImageLoading] = useState(false);
    const [aiFillLoading, setAiFillLoading] = useState(false);
    const [valuationLoading, setValuationLoading] = useState(false);
    const [valuation, setValuation] = useState<Record<string, any> | null>(null);

    /** 将后端返回的 OSS 相对路径转为 UploadFile 对象，供图片组件回显 */
    function pathToUploadFile(path: string, idx = 0): UploadFile {
        return {
            uid: `-edit-${idx}`,
            name: path,
            status: 'done',
            url: `${TWT.static}${path}`,
            thumbUrl: `${TWT.static}${path}`,
        };
    }

    // 加载字典
    useEffect(() => {
        dictCategoryTree().then((r: any) => setCatOptions(r?.data || r || []));
        dictCities().then((r: any) => setCityOptions(r?.data || r || []));
    }, []);

    // 编辑模式：加载商品详情
    useEffect(() => {
        if (!isEdit) {
            // 加载草稿
            productDraftGet().then((r: any) => {
                const snap = r?.data ?? r;
                if (snap && typeof snap === 'string') {
                    try {
                        const parsed = JSON.parse(snap);
                        // 草稿里 images 存的是 OSS 相对路径数组，恢复时转回 UploadFile 供回显
                        if (
                            Array.isArray(parsed.images) &&
                            parsed.images.length > 0 &&
                            typeof parsed.images[0] === 'string'
                        ) {
                            parsed.images = parsed.images.map((p: string, i: number) =>
                                pathToUploadFile(p, i),
                            );
                        }
                        form.setFieldsValue(parsed);
                    } catch {
                        /* ignore */
                    }
                }
            });
            return;
        }
        setDetailLoading(true);
        productDetail(id!)
            .then((r: any) => {
                const prod = r?.data ?? r;
                setDetailResp(prod);
                if (prod) {
                    const cat = prod.catL2 ? [prod.catL1, prod.catL2] : [prod.catL1];
                    // 解析 images JSON 数组，转回 UploadFile 格式
                    let existingImages: UploadFile[] = [];
                    if (prod.images) {
                        try {
                            const paths: string[] = JSON.parse(prod.images) as string[];
                            existingImages = paths.map((p, i) => pathToUploadFile(p, i));
                        } catch {
                            /* 非 JSON，用 cover 兜底 */
                            if (prod.cover) existingImages = [pathToUploadFile(prod.cover)];
                        }
                    } else if (prod.cover) {
                        existingImages = [pathToUploadFile(prod.cover)];
                    }

                    form.setFieldsValue({
                        title: prod.title,
                        price: prod.price,
                        originalPrice: prod.originalPrice,
                        condition: prod.condition,
                        cat: cat,
                        stock: prod.stock,
                        shippingMethod: prod.shippingMethod,
                        shippingFee: prod.shippingFee,
                        city: prod.city,
                        purchaseDate: prod.purchaseDate,
                        description: prod.description,
                        images: existingImages,
                    });
                }
                setDetailLoading(false);
            })
            .catch(() => setDetailLoading(false));
    }, [id, isEdit, form]);

    /** 从 UploadFile 中提取已上传的 OSS 相对路径 */
    function getImagePath(f: UploadFile): string | undefined {
        const { response } = f as any;
        if (response) {
            // commonUpload 返回 { code: 200, data: "oss相对路径" }
            const data = response.data ?? response.imgUrl;
            if (typeof data === 'string') return data;
        }
        // 编辑模式回显的图片没有 response：从 url 还原相对路径（pathToUploadFile 拼过 TWT.static）
        const url = f.url || f.thumbUrl;
        if (url) {
            const path = url.replace(TWT.static, '').split('?')[0];
            return path || undefined;
        }
        return undefined;
    }

    const doPublish = async (values: any) => {
        setSubmitting(true);
        try {
            // 从已上传的文件中提取 OSS 相对路径
            const paths = (values.images || [])
                .filter((f: UploadFile) => f.status === 'done')
                .map(getImagePath)
                .filter(Boolean) as string[];

            if (paths.length === 0) {
                message.warning('请至少上传一张商品图片');
                return;
            }

            const cat = values.cat || [];
            const payload: Record<string, any> = {
                title: values.title,
                price: values.price,
                originalPrice: values.originalPrice || null,
                condition: values.condition,
                catL1: cat[0] || '',
                catL2: cat[1] || null,
                stock: values.stock ?? 1,
                shippingMethod: values.shippingMethod || 'express',
                shippingFee: values.shippingFee || 0,
                city: values.city || '',
                purchaseDate: values.purchaseDate || null,
                description: values.description || '',
                cover: paths[0],
                images: JSON.stringify(paths),
            };

            if (isEdit) {
                payload.productId = Number(id);
                await productUpdate(payload);
                message.success('已重新提交审核');
            } else {
                await productPublish(payload);
                message.success('发布成功，等待审核');
                // 发布成功删除草稿
                productDraftDelete().catch(() => {});
            }
            history.push('/product/my');
        } catch (e: any) {
            message.error(e?.message || '操作失败');
        } finally {
            setSubmitting(false);
        }
    };

    /** 收集 AI 接口需要的商品字段（分类 code 转中文标签） */
    function findCatLabel(options: any[], code: string): string | undefined {
        for (const o of options || []) {
            if (o.code === code) return o.name;
            const child = findCatLabel(o.children || [], code);
            if (child) return child;
        }
        return undefined;
    }

    function buildAiPayload(): Record<string, any> | null {
        const v = form.getFieldsValue();
        if (!v.title || !String(v.title).trim()) {
            message.warning('请先填写商品标题（可只写商品名），AI 将据此生成');
            return null;
        }
        const cat = v.cat || [];
        const catLabels = cat.map((c: string) => findCatLabel(catOptions, c)).filter(Boolean);
        const condLabel = conditionOptions.find((o) => o.value === v.condition)?.label;
        return {
            productName: String(v.title).trim(),
            category: catLabels.join('/') || undefined,
            condition: condLabel || undefined,
            price: v.price ?? undefined,
            keywords: v.description ? String(v.description).slice(0, 200) : undefined,
        };
    }

    /** AI 帮我写：生成候选标题 + 描述并回填表单 */
    const handleAiCopy = async () => {
        const payload = buildAiPayload();
        if (!payload) return;
        setAiCopyLoading(true);
        try {
            const r: any = await assistProductCopy(payload);
            const d = r?.data ?? r;
            const titles: string[] = d?.titles || [];
            if (titles.length > 0) {
                form.setFieldValue('title', titles[0]);
            }
            if (d?.description) {
                form.setFieldValue('description', d.description);
            }
            message.success(
                titles.length > 1
                    ? `AI 文案已生成，另附 ${titles.length - 1} 个备选标题`
                    : 'AI 文案已生成',
            );
        } catch (e: any) {
            message.error(e?.message || 'AI 文案生成失败');
        } finally {
            setAiCopyLoading(false);
        }
    };

    /** AI 配图：生成一张商品图并加入图片列表 */
    const handleAiImage = async () => {
        const payload = buildAiPayload();
        if (!payload) return;
        setAiImageLoading(true);
        try {
            const r: any = await assistProductImage(payload);
            const d = r?.data ?? r;
            if (d?.imageUrl) {
                const cur: UploadFile[] = form.getFieldValue('images') || [];
                form.setFieldValue('images', [
                    ...cur,
                    {
                        uid: `ai-${Date.now()}`,
                        name: 'AI 配图',
                        status: 'done',
                        url: d.imageUrl,
                        thumbUrl: d.imageUrl,
                    },
                ]);
                message.success('AI 配图已加入图片列表');
            } else {
                message.warning('AI 未返回图片，请重试');
            }
        } catch (e: any) {
            message.error(e?.message || 'AI 配图生成失败');
        } finally {
            setAiImageLoading(false);
        }
    };

    /** AI 识图填单：取已上传图片识别商品，仅回填空字段（不覆盖已填写内容） */
    const handleAiFill = async () => {
        const files: UploadFile[] = form.getFieldValue('images') || [];
        const paths = files
            .filter((f) => f.status === 'done')
            .map(getImagePath)
            .filter(Boolean) as string[];
        if (paths.length === 0) {
            message.warning('请先上传商品图片，AI 再据图识别填写');
            return;
        }
        setAiFillLoading(true);
        try {
            const r: any = await assistProductRecognition({ images: paths });
            const d = r?.data ?? r;
            let filled = 0;
            // 类目：code 需在类目字典中存在才回填
            const v = form.getFieldsValue();
            const catCur: string[] = v.cat || [];
            if (catCur.length === 0 && d?.catL1Code && findCatLabel(catOptions, d.catL1Code)) {
                const catVal =
                    d?.catL2Code && findCatLabel(catOptions, d.catL2Code)
                        ? [d.catL1Code, d.catL2Code]
                        : [d.catL1Code];
                form.setFieldValue('cat', catVal);
                filled++;
            }
            if (
                !v.condition &&
                d?.condition &&
                conditionOptions.some((o) => o.value === d.condition)
            ) {
                form.setFieldValue('condition', d.condition);
                filled++;
            }
            if (!v.title && d?.title) {
                form.setFieldValue('title', d.title);
                filled++;
            }
            if (!v.description && d?.description) {
                form.setFieldValue('description', d.description);
                filled++;
            }
            if (filled > 0) {
                message.success(`AI 已根据图片识别并回填 ${filled} 个字段`);
            } else {
                message.info('AI 已识别图片，但对应字段均已填写，未做覆盖');
            }
        } catch (e: any) {
            message.error(e?.message || 'AI 识图失败');
        } finally {
            setAiFillLoading(false);
        }
    };

    /** AI 估价：按当前 标题/类目/成色 查真实成交统计 */
    const handleAiValuation = async () => {
        const v = form.getFieldsValue();
        if (!v.title || !String(v.title).trim()) {
            message.warning('请先填写商品标题（AI 也会尝试自动识别品类）');
            return;
        }
        const cat = v.cat || [];
        setValuationLoading(true);
        try {
            const r: any = await assistProductValuation({
                title: String(v.title).trim(),
                description: v.description ? String(v.description).slice(0, 500) : undefined,
                catL1: cat[0],
                catL2: cat[1],
                condition: v.condition,
            });
            const d = r?.data ?? r;
            setValuation(d);
        } catch (e: any) {
            message.error(e?.message || 'AI 估价失败');
        } finally {
            setValuationLoading(false);
        }
    };

    const saveDraft = async () => {
        const values = form.getFieldsValue();
        // 草稿只存可序列化的表单值：图片统一转成 OSS 相对路径数组，
        // 避免把 UploadFile 的 response/预览大对象写进草稿，恢复后无法提取路径
        const draft = {
            ...values,
            images: (values.images || [])
                .map((f: UploadFile) => getImagePath(f))
                .filter(Boolean) as string[],
        };
        const snap = JSON.stringify(draft);
        try {
            await productDraftSave(snap);
            message.success('草稿已保存');
        } catch {
            message.error('草稿保存失败');
        }
    };

    if (detailLoading) {
        return (
            <div style={{ textAlign: 'center', padding: 100 }}>
                <Spin />
            </div>
        );
    }

    return (
        <PageContainer header={{ title: isEdit ? '编辑商品' : '发布商品', breadcrumb: {} }}>
            <Card style={{ maxWidth: 800, margin: '0 auto' }}>
                <Form
                    form={form}
                    layout="vertical"
                    onFinish={doPublish}
                    initialValues={{
                        condition: '90',
                        stock: 1,
                        shippingMethod: 'express',
                    }}
                >
                    {/* 基本信息 */}
                    <Form.Item
                        name="title"
                        label="商品标题"
                        rules={[
                            { required: true, message: '请输入商品标题' },
                            { max: 60, message: '标题不超过 60 字' },
                        ]}
                    >
                        <Input placeholder="例：iPhone 14 Pro 256G 暗紫色 95新" />
                    </Form.Item>

                    {/* AI 辅助按钮 */}
                    <Space style={{ marginBottom: 16 }} wrap>
                        <Button onClick={handleAiFill} loading={aiFillLoading}>
                            AI 识图填单
                        </Button>
                        <Button onClick={handleAiCopy} loading={aiCopyLoading}>
                            AI 帮我写
                        </Button>
                        <Button onClick={handleAiImage} loading={aiImageLoading}>
                            AI 配图
                        </Button>
                    </Space>

                    <Form.Item
                        name="cat"
                        label="商品品类"
                        rules={[{ required: true, message: '请选择品类' }]}
                    >
                        <Cascader
                            options={catOptions}
                            fieldNames={{ label: 'name', value: 'code', children: 'children' }}
                            placeholder="选择一级/二级分类"
                            changeOnSelect
                            style={{ width: '100%' }}
                        />
                    </Form.Item>

                    <Form.Item
                        name="condition"
                        label="成色"
                        rules={[{ required: true, message: '请选择成色' }]}
                    >
                        <Select options={conditionOptions} placeholder="选择成色" />
                    </Form.Item>

                    {/* 图片 */}
                    <Form.Item name="images" label="商品图片">
                        <UploadImages max={9} />
                    </Form.Item>
                    {detailResp?.cover && (
                        <div style={{ marginBottom: 16 }}>
                            <Image
                                src={`${TWT.static}${detailResp.cover}`}
                                width={100}
                                style={{ borderRadius: 8 }}
                            />
                        </div>
                    )}

                    {/* 价格 */}
                    <Space style={{ width: '100%' }} size={16} align="start">
                        <Form.Item
                            name="price"
                            label="售价 (¥)"
                            rules={[
                                { required: true, message: '请输入售价' },
                                { type: 'number', min: 0.01, message: '售价须大于 0' },
                            ]}
                            style={{ flex: 1 }}
                            extra={
                                valuation ? (
                                    <Typography.Text
                                        type="secondary"
                                        style={{ fontSize: 12, whiteSpace: 'pre-line' }}
                                    >
                                        <Typography.Text strong style={{ color: '#FF6B35' }}>
                                            {valuation.priceLow !== null &&
                                            valuation.priceLow !== undefined &&
                                            valuation.priceHigh !== null &&
                                            valuation.priceHigh !== undefined
                                                ? `AI 估价：¥${valuation.priceLow} ~ ¥${valuation.priceHigh}`
                                                : 'AI 估价：样本不足'}
                                        </Typography.Text>
                                        {valuation.median !== null &&
                                            valuation.median !== undefined &&
                                            ` · 中位 ¥${valuation.median}`}
                                        {valuation.sampleCount > 0 &&
                                            ` · 近180天成交 ${valuation.sampleCount} 件`}
                                        <br />
                                        {valuation.explanation}
                                    </Typography.Text>
                                ) : (
                                    <a onClick={handleAiValuation}>
                                        {valuationLoading
                                            ? 'AI 估价中…'
                                            : 'AI 估价（基于近期真实成交）'}
                                    </a>
                                )
                            }
                        >
                            <InputNumber
                                min={0.01}
                                precision={2}
                                prefix="¥"
                                style={{ width: '100%' }}
                                placeholder="0.00"
                            />
                        </Form.Item>
                        <Form.Item name="originalPrice" label="原价 (¥) 选填" style={{ flex: 1 }}>
                            <InputNumber
                                min={0}
                                precision={2}
                                prefix="¥"
                                style={{ width: '100%' }}
                                placeholder="0.00"
                            />
                        </Form.Item>
                    </Space>

                    {/* 库存/运费 */}
                    <Space style={{ width: '100%' }} size={16}>
                        <Form.Item
                            name="stock"
                            label="库存数量"
                            rules={[{ required: true, message: '请输入库存' }]}
                            style={{ flex: 1 }}
                        >
                            <InputNumber min={1} precision={0} style={{ width: '100%' }} />
                        </Form.Item>
                        <Form.Item
                            name="shippingMethod"
                            label="运费方式"
                            rules={[{ required: true, message: '请选择运费方式' }]}
                            style={{ flex: 1 }}
                        >
                            <Radio.Group>
                                <Radio value="express">快递</Radio>
                                <Radio value="free">包邮</Radio>
                                <Radio value="self_pickup">自提</Radio>
                            </Radio.Group>
                        </Form.Item>
                    </Space>

                    <Form.Item
                        noStyle
                        shouldUpdate={(prev, next) => prev.shippingMethod !== next.shippingMethod}
                    >
                        {({ getFieldValue }) =>
                            getFieldValue('shippingMethod') === 'express' ? (
                                <Form.Item name="shippingFee" label="运费金额 (¥)">
                                    <InputNumber
                                        min={0}
                                        precision={2}
                                        prefix="¥"
                                        style={{ width: 200 }}
                                        placeholder="0.00"
                                    />
                                </Form.Item>
                            ) : null
                        }
                    </Form.Item>

                    {/* 城市 */}
                    <Form.Item name="city" label="所在城市">
                        <Select
                            options={cityOptions.map((c) => ({ label: c, value: c }))}
                            placeholder="选择城市"
                            allowClear
                            showSearch
                        />
                    </Form.Item>

                    {/* 购买时间 */}
                    <Form.Item name="purchaseDate" label="购买时间 (选填)">
                        <Input placeholder="如：2024年6月" />
                    </Form.Item>

                    {/* 描述 */}
                    <Form.Item name="description" label="商品描述">
                        <Input.TextArea
                            rows={6}
                            placeholder="描述商品的使用情况、配件、购买渠道等信息"
                            maxLength={2000}
                            showCount
                        />
                    </Form.Item>

                    {/* 提交按钮 */}
                    <Form.Item>
                        <Space style={{ width: '100%', justifyContent: 'flex-end' }}>
                            <Button onClick={saveDraft}>保存草稿</Button>
                            <Button type="primary" htmlType="submit" loading={submitting}>
                                {isEdit ? '重新提交审核' : '提交审核'}
                            </Button>
                        </Space>
                    </Form.Item>
                </Form>
            </Card>
        </PageContainer>
    );
};

export default ProductPublish;
