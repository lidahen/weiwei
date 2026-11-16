import { PlusOutlined, ReloadOutlined, UserOutlined } from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import {
    Button,
    Card,
    Col,
    Empty,
    Form,
    Image,
    Input,
    InputNumber,
    message,
    Modal,
    Popconfirm,
    Row,
    Select,
    Space,
    Tag,
    Typography,
} from 'antd';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useIsMobile } from '@/hooks/useIsMobile';
import { listPlatformVoices, PlatformVoice } from '@/services/ant-design-pro/aimusic';
import {
    generateLook,
    listPersonas,
    removePersona,
    savePersona,
    AiPersona,
} from './service';

const PersonaManager: React.FC = () => {
    const isMobile = useIsMobile();
    const [rows, setRows] = useState<AiPersona[]>([]);
    const [loading, setLoading] = useState(false);
    const [modalOpen, setModalOpen] = useState(false);
    const [lookModalOpen, setLookModalOpen] = useState(false);
    const [current, setCurrent] = useState<AiPersona | null>(null);
    const [saving, setSaving] = useState(false);
    const [platformVoices, setPlatformVoices] = useState<PlatformVoice[]>([]);
    const [voicesLoading, setVoicesLoading] = useState(false);
    const [form] = Form.useForm();
    const [lookForm] = Form.useForm();
    const lookPollRef = useRef<any>(null);

    // 人设编辑弹窗打开时拉取商汤系统音色（供「快速选择音色」下拉）
    useEffect(() => {
        if (!modalOpen || platformVoices.length > 0) {
            return;
        }
        setVoicesLoading(true);
        listPlatformVoices('system')
            .then((res) => {
                setPlatformVoices(res?.voices || []);
            })
            .catch(() => {
                // 音色列表失败不阻塞人设编辑，保留手动填写
            })
            .finally(() => setVoicesLoading(false));
    }, [modalOpen, platformVoices.length]);

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const res = await listPersonas();
            if (res?.code === 200) {
                setRows(res.data?.rows || []);
            }
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        load();
        return () => clearInterval(lookPollRef.current);
    }, [load]);

    const openEdit = useCallback(
        (persona?: AiPersona) => {
            setCurrent(persona || null);
            form.setFieldsValue(
                persona || {
                    name: '',
                    description: '',
                    style_fewshot: '',
                    banned_words: '',
                    voice_id: '',
                    voice_name: '',
                },
            );
            setModalOpen(true);
        },
        [form],
    );

    const doSave = useCallback(async () => {
        const values = await form.validateFields();
        setSaving(true);
        try {
            const res = await savePersona({ ...values, persona_id: current?.persona_id });
            if (res?.code === 200) {
                message.success('已保存');
                setModalOpen(false);
                load();
            } else {
                message.error(res?.msg || '保存失败');
            }
        } finally {
            setSaving(false);
        }
    }, [current, form, load]);

    const doRemove = useCallback(
        async (personaId: number) => {
            const res = await removePersona(personaId);
            if (res?.code === 200) {
                message.success('已删除');
                load();
            } else {
                message.error(res?.msg || '删除失败');
            }
        },
        [load],
    );

    const doGenerateLook = useCallback(async () => {
        const values = await lookForm.validateFields();
        const target = current;
        if (!target) {
            return;
        }
        const res = await generateLook({
            persona_id: target.persona_id,
            appearance_prompt: values.appearance_prompt,
            count: values.count || 3,
        });
        if (res?.code !== 200) {
            message.error(res?.msg || '提交失败');
            return;
        }
        message.info('定妆照生成中（约 1-2 分钟），完成后自动刷新');
        setLookModalOpen(false);
        // 轮询人设列表直到该人设定妆照数量变化（此前是 90s 的 setTimeout 盲等）
        clearInterval(lookPollRef.current);
        const baseCount = (target.look_image_urls || []).length;
        const startedAt = Date.now();
        lookPollRef.current = setInterval(async () => {
            try {
                const r = await listPersonas();
                if (r?.code !== 200) {
                    return;
                }
                const updated = (r.data?.rows || []).find((p) => p.persona_id === target.persona_id);
                if (updated && (updated.look_image_urls || []).length > baseCount) {
                    clearInterval(lookPollRef.current);
                    setRows(r.data?.rows || []);
                    message.success('定妆照已生成');
                } else if (Date.now() - startedAt > 5 * 60_000) {
                    clearInterval(lookPollRef.current);
                    message.info('生成时间较长，请稍后手动刷新查看');
                }
            } catch {
                // 单次轮询失败忽略，下一轮再试；总超时兜底
                if (Date.now() - startedAt > 5 * 60_000) {
                    clearInterval(lookPollRef.current);
                    message.info('请稍后手动刷新查看定妆照结果');
                }
            }
        }, 10_000);
    }, [current, lookForm]);

    return (
        <PageContainer
            title={false}
            extra={[
                <Button key="refresh" icon={<ReloadOutlined />} onClick={load} />,
                <Button key="new" type="primary" icon={<PlusOutlined />} onClick={() => openEdit()}>
                    新建人设
                </Button>,
            ]}
        >
            {rows.length === 0 && !loading ? (
                <Empty description="还没有人设：人设 = 账号定位 + 文案风格样例 + 定妆照 + 克隆音色" />
            ) : (
                <Row gutter={[16, 16]}>
                    {rows.map((p) => (
                        <Col xs={24} sm={12} lg={8} key={p.persona_id}>
                            <Card
                                loading={loading}
                                title={
                                    <Space>
                                        <UserOutlined />
                                        {p.name}
                                        {p.voice_id && <Tag color="green">音色</Tag>}
                                    </Space>
                                }
                                actions={[
                                    <Button key="edit" type="link" onClick={() => openEdit(p)}>
                                        编辑
                                    </Button>,
                                    <Button
                                        key="look"
                                        type="link"
                                        onClick={() => {
                                            setCurrent(p);
                                            lookForm.setFieldsValue({ appearance_prompt: p.description, count: 3 });
                                            setLookModalOpen(true);
                                        }}
                                    >
                                        定妆照
                                    </Button>,
                                    <Popconfirm key="del" title="确认删除该人设？" onConfirm={() => doRemove(p.persona_id)}>
                                        <Button type="link" danger>
                                            删除
                                        </Button>
                                    </Popconfirm>,
                                ]}
                            >
                                <Typography.Paragraph type="secondary" ellipsis={{ rows: 2 }} style={{ marginBottom: 8 }}>
                                    {p.description || '（无定位描述）'}
                                </Typography.Paragraph>
                                {(p.look_image_urls || []).length > 0 && (
                                    <Image.PreviewGroup>
                                        <Space wrap>
                                            {p.look_image_urls!.slice(0, 3).map((u) => (
                                                <Image
                                                    key={u}
                                                    src={u}
                                                    width={56}
                                                    height={90}
                                                    style={{ objectFit: 'cover', borderRadius: 4 }}
                                                />
                                            ))}
                                        </Space>
                                    </Image.PreviewGroup>
                                )}
                            </Card>
                        </Col>
                    ))}
                </Row>
            )}

            <Modal
                title={current ? '编辑人设' : '新建人设'}
                open={modalOpen}
                onCancel={() => setModalOpen(false)}
                onOk={doSave}
                confirmLoading={saving}
                width={isMobile ? '94%' : 640}
            >
                <Form form={form} layout="vertical">
                    <Form.Item name="name" label="人设名称" rules={[{ required: true }]}>
                        <Input placeholder="如：AI工具 Efficiency 哥" />
                    </Form.Item>
                    <Form.Item name="description" label="账号定位">
                        <Input.TextArea rows={2} placeholder="如：面向普通人的 AI 工具测评与效率技巧，真诚不吹" />
                    </Form.Item>
                    <Form.Item
                        name="style_fewshot"
                        label="文案风格样例（粘贴你历史爆款的片段，AI 模仿其句式语气）"
                    >
                        <Input.TextArea rows={4} />
                    </Form.Item>
                    <Form.Item name="banned_words" label="禁用词（逗号分隔）">
                        <Input placeholder="如：最,第一,绝对, guaranteed" />
                    </Form.Item>
                    <Form.Item label="快速选择音色（商汤系统音色）">
                        <Select
                            allowClear
                            showSearch
                            optionFilterProp="label"
                            loading={voicesLoading}
                            placeholder="搜索或选择后自动填入下方音色 ID"
                            options={platformVoices.map((v) => ({
                                label: `${v.voice_name}${v.description?.[0] ? ` · ${v.description[0]}` : ''}`,
                                value: v.voice_id,
                            }))}
                            onChange={(v) => form.setFieldValue('voice_id', v ?? '')}
                        />
                    </Form.Item>
                    <Form.Item
                        name="voice_id"
                        label={
                            <Space>
                                配音音色 ID（可选）
                                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                                    填商汤系统音色 ID（如 female_0006_a 温柔御姐 / male_0028_a 可靠青叔），
                                    或在「AI 音乐 → 我的音色」克隆后回填 speech: 开头的 ID（需硅基流动余额）
                                </Typography.Text>
                            </Space>
                        }
                    >
                        <Input placeholder="商汤音色 ID 或 SiliconFlow speech: uri" />
                    </Form.Item>
                </Form>
            </Modal>

            <Modal
                title={`生成定妆照（${current?.name || ''}）`}
                open={lookModalOpen}
                onCancel={() => setLookModalOpen(false)}
                onOk={doGenerateLook}
                width={isMobile ? '94%' : 520}
            >
                <Form form={lookForm} layout="vertical">
                    <Form.Item
                        name="appearance_prompt"
                        label="人物外观描述"
                        rules={[{ required: true, message: '描述人物形象' }]}
                    >
                        <Input.TextArea
                            rows={3}
                            placeholder="如：25-30 岁中国男性，短发，戴细框眼镜，休闲卫衣，亲和力强的科技博主形象"
                        />
                    </Form.Item>
                    <Form.Item name="count" label="生成张数（不同机位，取前 5 张用于视频参考）">
                        <InputNumber min={1} max={5} style={{ width: 120 }} />
                    </Form.Item>
                </Form>
            </Modal>
        </PageContainer>
    );
};

export default PersonaManager;
