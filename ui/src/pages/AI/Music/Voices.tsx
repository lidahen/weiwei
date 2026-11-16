/** 我的音色: 上传样本训练 + 列表 */
import {
    deleteVoice,
    listMyVoices,
    trainVoice,
    VoiceProfile,
} from '@/services/ant-design-pro/aimusic';
import { AudioOutlined, DeleteOutlined } from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import {
    Alert,
    Button,
    Card,
    Checkbox,
    Empty,
    Form,
    Input,
    InputNumber,
    List,
    message,
    Modal,
    Popconfirm,
    Space,
    Tag,
    Typography,
} from 'antd';
import { useEffect, useRef, useState } from 'react';

const { Text } = Typography;

const STATUS_LABEL: Record<number, { text: string; color: string }> = {
    0: { text: '上传中', color: 'default' },
    1: { text: '训练中', color: 'processing' },
    2: { text: '可用', color: 'success' },
    3: { text: '失败', color: 'error' },
};

const Voices: React.FC = () => {
    const [voices, setVoices] = useState<VoiceProfile[]>([]);
    const [loading, setLoading] = useState(false);
    const [modalOpen, setModalOpen] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [consent, setConsent] = useState(false);
    const [recording, setRecording] = useState(false);
    const [recordedBlob, setRecordedBlob] = useState<Blob | null>(null);
    const recorderRef = useRef<MediaRecorder | null>(null);
    const [form] = Form.useForm();

    const load = async () => {
        setLoading(true);
        try {
            const res = await listMyVoices();
            setVoices(res.records ?? []);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        load();
    }, []);

    const startRecord = async () => {
        try {
            const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
            const rec = new MediaRecorder(stream);
            const chunks: BlobPart[] = [];
            rec.ondataavailable = (e) => chunks.push(e.data);
            rec.onstop = () => {
                setRecordedBlob(new Blob(chunks, { type: 'audio/webm' }));
                stream.getTracks().forEach((t) => t.stop());
            };
            rec.start();
            recorderRef.current = rec;
            setRecording(true);
        } catch (e: any) {
            message.error('无法访问麦克风: ' + e.message);
        }
    };

    const stopRecord = () => {
        recorderRef.current?.stop();
        setRecording(false);
    };

    const onSubmit = async (vals: any) => {
        if (!consent) {
            message.warning('请先勾选授权协议');
            return;
        }
        const blob = recordedBlob;
        if (!blob) {
            message.warning('请先录音');
            return;
        }
        setSubmitting(true);
        try {
            const file = new File([blob], 'sample.webm', { type: 'audio/webm' });
            await trainVoice({
                name: vals.name,
                text: vals.text,
                durationMs: vals.durationMs,
                consentSigned: true,
                file,
            });
            message.success('训练完成,音色已可用');
            setModalOpen(false);
            setRecordedBlob(null);
            form.resetFields();
            setConsent(false);
            load();
        } catch (e: any) {
            message.error('训练失败: ' + (e?.data?.msg || e.message));
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <PageContainer title="我的音色">
            <Alert
                type="warning"
                showIcon
                message="合规要求"
                description={
                    <ul style={{ marginBottom: 0, paddingLeft: 20 }}>
                        <li>
                            仅可克隆<strong>本人声音</strong>,禁止伪造他人声音
                        </li>
                        <li>
                            必须先<strong>实时录制</strong>样本(不允许上传已有音频)
                        </li>
                        <li>样本时长 10 ~ 60 秒,内容需与下方文本一致</li>
                        <li>每日最多训练 1 次,所有生成的音频将嵌入不可听水印</li>
                    </ul>
                }
                style={{ marginBottom: 16 }}
            />

            <Card
                title={
                    <Space wrap>
                        <AudioOutlined /> 音色列表
                        <Button
                            type="primary"
                            icon={<AudioOutlined />}
                            onClick={() => setModalOpen(true)}
                        >
                            录制并训练新音色
                        </Button>
                    </Space>
                }
            >
                {voices.length === 0 && !loading ? (
                    <Empty description="还没有训练过音色" />
                ) : (
                    <List
                        loading={loading}
                        dataSource={voices}
                        renderItem={(v) => (
                            <List.Item
                                actions={[
                                    <Popconfirm
                                        key="del"
                                        title="确认删除此音色?"
                                        onConfirm={async () => {
                                            await deleteVoice(v.id);
                                            message.success('已删除');
                                            load();
                                        }}
                                    >
                                        <Button type="link" danger icon={<DeleteOutlined />}>
                                            删除
                                        </Button>
                                    </Popconfirm>,
                                ]}
                            >
                                <List.Item.Meta
                                    title={
                                        <Space>
                                            <Text strong>{v.name}</Text>
                                            <Tag color={STATUS_LABEL[v.status]?.color}>
                                                {STATUS_LABEL[v.status]?.text}
                                            </Tag>
                                        </Space>
                                    }
                                    description={
                                        <Space direction="vertical" size={2}>
                                            <Text type="secondary">音色 ID: {v.vendorVoiceId}</Text>
                                            <Text type="secondary">
                                                模型: {v.vendorModel} · 时长:{' '}
                                                {v.durationMs
                                                    ? `${(v.durationMs / 1000).toFixed(1)}s`
                                                    : '-'}
                                            </Text>
                                            {v.sampleText ? (
                                                <Text type="secondary">样本: {v.sampleText}</Text>
                                            ) : null}
                                        </Space>
                                    }
                                />
                            </List.Item>
                        )}
                    />
                )}
            </Card>

            <Modal
                title="录制样本训练克隆音色"
                open={modalOpen}
                onCancel={() => {
                    setModalOpen(false);
                    setRecordedBlob(null);
                    setConsent(false);
                }}
                onOk={() => form.submit()}
                confirmLoading={submitting}
                width={560}
                okText="开始训练"
            >
                <Form form={form} layout="vertical" onFinish={onSubmit}>
                    <Form.Item label="音色别名" name="name" rules={[{ required: true, max: 64 }]}>
                        <Input placeholder="例如:我的原声 / 温柔女声" />
                    </Form.Item>
                    <Form.Item
                        label="朗读文本(请按此文本录制)"
                        name="text"
                        rules={[{ required: true, max: 1024 }]}
                    >
                        <Input.TextArea
                            rows={3}
                            placeholder="例如:今天天气真好,我想把这首歌唱给你听"
                        />
                    </Form.Item>
                    <Form.Item label="音频时长(毫秒)" name="durationMs">
                        <InputNumber
                            min={10000}
                            max={60000}
                            step={1000}
                            style={{ width: '100%' }}
                        />
                    </Form.Item>

                    <Form.Item label="录制样本" required>
                        <Space direction="vertical" style={{ width: '100%' }}>
                            {!recordedBlob && !recording && (
                                <Button
                                    icon={<AudioOutlined />}
                                    onClick={startRecord}
                                    type="dashed"
                                    block
                                >
                                    开始录制
                                </Button>
                            )}
                            {recording && (
                                <Button danger onClick={stopRecord} block>
                                    停止录制
                                </Button>
                            )}
                            {recordedBlob && (
                                <Space>
                                    <audio controls src={URL.createObjectURL(recordedBlob)} />
                                    <Button size="small" onClick={() => setRecordedBlob(null)}>
                                        重录
                                    </Button>
                                </Space>
                            )}
                        </Space>
                    </Form.Item>

                    <Form.Item>
                        <Checkbox checked={consent} onChange={(e) => setConsent(e.target.checked)}>
                            我已阅读并同意《声音克隆授权协议》,承诺只克隆本人声音
                        </Checkbox>
                    </Form.Item>
                </Form>
            </Modal>
        </PageContainer>
    );
};

export default Voices;
