import { createStyles } from 'antd-style';
import {
    AppstoreOutlined,
    DeleteOutlined,
    DownloadOutlined,
    EditOutlined,
    PictureOutlined,
    PlusOutlined,
    ReloadOutlined,
    TeamOutlined,
    UploadOutlined,
} from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import {
    Button,
    Card,
    Col,
    Collapse,
    Empty,
    Form,
    Image,
    Input,
    InputNumber,
    message,
    Modal,
    Popconfirm,
    Row,
    Segmented,
    Select,
    Space,
    Switch,
    Tooltip,
    Typography,
    Upload,
} from 'antd';
import type { UploadFile } from 'antd/es/upload/interface';
import React, { useEffect, useMemo, useRef, useState } from 'react';
import { listPersonas, setPersonaLook } from '@/pages/AI/Creation/service';
import { system } from '@/utils/twelvet';
import TWT from '@/setting';
import { delImageTask, generateImage, getImageTaskStatus, pageQueryImageTask, uploadImage } from './service';
import ModelSelect from '@/components/ModelSelect';
import TaskCenter from '@/components/TaskCenter';

// Agnes 官方档位 × 宽高比 → 原生输出尺寸（docs/ai/模型调用文档说明.md），
// 前端直接换算成精确尺寸提交，避免上游二次归一化
const SIZE_TABLE: Record<string, Record<string, string>> = {
    '1K': {
        '1:1': '1024x1024', '4:3': '1152x864', '3:4': '864x1152', '16:9': '1312x736',
        '9:16': '736x1312', '3:2': '1248x832', '2:3': '832x1248', '21:9': '1568x672',
    },
    '2K': {
        '1:1': '2048x2048', '4:3': '2304x1728', '3:4': '1728x2304', '16:9': '2624x1472',
        '9:16': '1472x2624', '3:2': '2496x1664', '2:3': '1664x2496', '21:9': '3136x1344',
    },
    '3K': {
        '1:1': '3072x3072', '4:3': '3456x2592', '3:4': '2592x3456', '16:9': '3936x2208',
        '9:16': '2208x3936', '3:2': '3744x2496', '2:3': '2496x3744', '21:9': '4704x2016',
    },
    '4K': {
        '1:1': '4096x4096', '4:3': '4608x3456', '3:4': '3456x4608', '16:9': '5248x2944',
        '9:16': '2944x5248', '3:2': '4992x3328', '2:3': '3328x4992', '21:9': '6272x2688',
    },
};

const RATIOS = [
    { value: '1:1', w: 24, h: 24 },
    { value: '4:3', w: 28, h: 21 },
    { value: '3:4', w: 21, h: 28 },
    { value: '16:9', w: 32, h: 18 },
    { value: '9:16', w: 18, h: 32 },
    { value: '3:2', w: 30, h: 20 },
    { value: '2:3', w: 20, h: 30 },
    { value: '21:9', w: 35, h: 15 },
];

const QUALITIES = [
    { label: '1K 标准', value: '1K' },
    { label: '2K 高清', value: '2K' },
    { label: '3K 超清', value: '3K' },
    { label: '4K 极清', value: '4K' },
];

// ── 模型尺寸能力 ──
// 部分模型（如商汤图片）只接受固定的几个输出尺寸，传其它尺寸会被上游拒。
// 后端会自动就近适配，前端这里同步同一套规则，用于收敛选项 + 展示真实输出尺寸。
const SIZE_PATTERN = /^(\d{2,5})x(\d{2,5})$/i;

/** 与后端 normalize_image_size 同口径：比例最接近优先，并列时面积最接近 */
const normalizeSizeForModel = (size: string, supported: string[]) => {
    if (!supported.length || supported.includes(size)) return size;
    const m = SIZE_PATTERN.exec(size || '');
    if (!m) return supported[0];
    const tw = Number(m[1]);
    const th = Number(m[2]);
    const targetRatio = tw / th;
    const targetArea = tw * th;
    let best = supported[0];
    let bestRatioDiff = Number.POSITIVE_INFINITY;
    let bestAreaDiff = Number.POSITIVE_INFINITY;
    supported.forEach((s) => {
        const sm = SIZE_PATTERN.exec(s);
        if (!sm) return;
        const w = Number(sm[1]);
        const h = Number(sm[2]);
        if (!w || !h) return;
        const ratioDiff = Math.abs(w / h - targetRatio);
        const areaDiff = Math.abs(w * h - targetArea);
        if (ratioDiff < bestRatioDiff || (ratioDiff === bestRatioDiff && areaDiff < bestAreaDiff)) {
            best = s;
            bestRatioDiff = ratioDiff;
            bestAreaDiff = areaDiff;
        }
    });
    return best;
};

/** 该宽高比是否被模型尺寸白名单覆盖（容差 2%） */
const ratioSupported = (ratioValue: string, supported: string[]) => {
    if (!supported.length) return true;
    const [a, b] = ratioValue.split(':').map(Number);
    if (!a || !b) return true;
    const target = a / b;
    return supported.some((s) => {
        const [w, h] = s.split('x').map(Number);
        return !!w && !!h && Math.abs(w / h - target) < 0.02;
    });
};

const PENDING_STATUSES = ['queued', 'pending', 'retrying', 'processing'];

const GEN_TYPE_LABELS: Record<string, string> = {
    text2img: '文生图',
    img2img: '图生图',
    multi2img: '多图合成',
};

const useStyles = createStyles(({ token, css }) => {
    return {
        header: css`
            border-radius: ${token.borderRadiusLG}px;
            background: linear-gradient(120deg, ${token.colorPrimary} 0%, #6d8a77 100%);
            color: #fff;
            padding: 24px 28px;
            margin-bottom: 16px;
            @media (max-width: 767px) {
                padding: 18px 16px;
            }
        `,
        headerTitle: css`
            font-size: 20px;
            font-weight: 600;
            margin: 0 0 4px;
        `,
        headerDesc: css`
            margin: 0;
            opacity: 0.85;
            font-size: 13px;
        `,
        ratioTile: css`
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: 6px;
            padding: 10px 4px 8px;
            border-radius: ${token.borderRadius}px;
            border: 1px solid ${token.colorBorderSecondary};
            cursor: pointer;
            transition: all 0.2s;
            background: ${token.colorBgContainer};
            &:hover {
                border-color: ${token.colorPrimary};
            }
        `,
        ratioTileActive: css`
            border-color: ${token.colorPrimary};
            background: ${token.colorPrimaryBg};
            box-shadow: 0 0 0 1px ${token.colorPrimary} inset;
        `,
        ratioShape: css`
            border-radius: 2px;
            background: currentColor;
            opacity: 0.55;
        `,
        ratioLabel: css`
            font-size: 12px;
            color: ${token.colorTextSecondary};
        `,
        galleryCard: css`
            border-radius: ${token.borderRadiusLG}px;
            overflow: hidden;
            background: ${token.colorBgContainer};
            border: 1px solid ${token.colorBorderSecondary};
            position: relative;
            transition: box-shadow 0.2s, transform 0.2s;
            &:hover {
                box-shadow: ${token.boxShadowTertiary};
                transform: translateY(-2px);
            }
        `,
        galleryAction: css`
            position: absolute;
            top: 8px;
            right: 8px;
            z-index: 2;
        `,
    };
});

/**
 * AI 图片生成模块
 */
const ImagePage: React.FC = () => {
    const { styles } = useStyles();
    const [form] = Form.useForm();

    const [loading, setLoading] = useState(false);
    // 生成类型：text2img / img2img / multi2img
    const [genType, setGenType] = useState<string>('text2img');
    // 一键设为定妆照：选人设弹窗
    const [lookPicker, setLookPicker] = useState<{ open: boolean; url: string }>({ open: false, url: '' });
    const [lookPersonas, setLookPersonas] = useState<{ persona_id: number; name: string }[]>([]);
    const [lookPersonaId, setLookPersonaId] = useState<number | undefined>();
    const [lookSetting, setLookSetting] = useState(false);
    // 宽高比 + 清晰度档位（换算成精确尺寸提交）
    const [ratio, setRatio] = useState<string>('1:1');
    const [quality, setQuality] = useState<string>('2K');
    // 生成结果（受控预览：任务完成自动弹出，之后点画廊卡片手动看）
    const [resultUrl, setResultUrl] = useState('');
    const [previewOpen, setPreviewOpen] = useState(false);
    // 任务中心：进行中 + 失败的任务（取最新一页前端过滤）
    const [tasks, setTasks] = useState<Record<string, any>[]>([]);
    // 上传状态
    const [fileList, setFileList] = useState<UploadFile[]>([]);
    const [uploadedImageUrl, setUploadedImageUrl] = useState('');
    const [uploading, setUploading] = useState(false);
    // 多图上传：按文件 uid 记录上传后的 URL，避免并发上传完成顺序与选择顺序不一致
    const [multiUrls, setMultiUrls] = useState<Record<string, string>>({});
    // 作品画廊（仅已完成作品，服务端按 status=completed 过滤）
    const [gallery, setGallery] = useState<Record<string, any>[]>([]);
    const [galleryTotal, setGalleryTotal] = useState(0);
    const [galleryPage, setGalleryPage] = useState(1);
    const galleryPageSize = 8;
    // 画廊按生成类型筛选（'all' = 全部）
    const [genTypeFilter, setGenTypeFilter] = useState<string>('all');
    const pageStateRef = useRef({ page: 1 });

    // 当前模型声明的输出尺寸白名单（空 = 不限制，走 Agnes 全量档位）
    const [modelSizes, setModelSizes] = useState<string[]>([]);

    const availableRatios = useMemo(() => {
        if (!modelSizes.length) return RATIOS;
        const matched = RATIOS.filter((r) => ratioSupported(r.value, modelSizes));
        return matched.length ? matched : RATIOS;
    }, [modelSizes]);

    // 清晰度按「归一化后真实输出尺寸」去重：多个档位落到同一尺寸时只保留最小档
    const availableQualities = useMemo(() => {
        if (!modelSizes.length) return QUALITIES;
        const seen = new Set<string>();
        const list = QUALITIES.filter((q) => {
            const size = SIZE_TABLE[q.value]?.[ratio];
            if (!size) return false;
            const out = normalizeSizeForModel(size, modelSizes);
            if (seen.has(out)) return false;
            seen.add(out);
            return true;
        });
        return list.length ? list : QUALITIES;
    }, [modelSizes, ratio]);

    const currentSize = SIZE_TABLE[quality]?.[ratio] ?? '';
    // 模型不支持的尺寸会被就近适配，这里显示的是真实输出尺寸
    const actualSize = modelSizes.length
        ? normalizeSizeForModel(currentSize, modelSizes)
        : currentSize;

    // 换模型后当前比例/清晰度可能已不可用，自动落到可选项（清晰度取最大的可用档）
    useEffect(() => {
        if (!availableRatios.some((r) => r.value === ratio)) {
            setRatio(availableRatios[0].value);
        }
    }, [availableRatios, ratio]);
    useEffect(() => {
        if (!availableQualities.some((q) => q.value === quality)) {
            setQuality(availableQualities[availableQualities.length - 1].value);
        }
    }, [availableQualities, quality]);

    const multiUrlList = fileList.map((f) => multiUrls[f.uid]).filter(Boolean);

    /** 加载任务中心：取最新一页，非 completed 的（进行中/失败）留在条上 */
    const loadTasks = async () => {
        try {
            const { code, data } = await pageQueryImageTask({ current: 1, pageSize: 30 });
            if (code !== 200) return;
            setTasks((data?.records || []).filter((t: Record<string, any>) => t.status !== 'completed'));
        } catch (e) {
            system.error(e);
        }
    };

    /** 加载作品画廊（仅已完成，服务端过滤） */
    const loadGallery = async (page = pageStateRef.current.page) => {
        try {
            // 后端 TableSupport 分页参数为 current / pageSize（PageHelper）
            const params: Record<string, any> = { current: page, pageSize: galleryPageSize, status: 'completed' };
            if (genTypeFilter !== 'all') {
                params.genType = genTypeFilter;
            }
            const { code, data } = await pageQueryImageTask(params);
            if (code !== 200) return;
            pageStateRef.current.page = page;
            setGalleryPage(page);
            setGallery(data?.records || []);
            setGalleryTotal(data?.total || 0);
        } catch (e) {
            system.error(e);
        }
    };

    const loadAll = (page?: number) => {
        loadTasks();
        loadGallery(page);
    };

    useEffect(() => {
        loadAll(1);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);
    useEffect(() => {
        // 切换生成类型筛选后回到第一页
        loadGallery(1);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [genTypeFilter]);
    useEffect(() => {
        // 存在进行中的任务时定时刷新（任务中心 + 画廊，完成后自然归档）
        const timer = setInterval(() => {
            if (tasks.some((t) => PENDING_STATUSES.includes(t.status))) {
                loadAll();
            }
        }, 6000);
        return () => clearInterval(timer);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [tasks]);

    /** 处理图片上传，返回完整 URL */
    const handleUpload = async (file: File) => {
        try {
            setUploading(true);
            const formData = new FormData();
            formData.append('file', file);
            const { code, msg, data } = await uploadImage(formData);
            if (code !== 200) {
                message.error(msg || '上传失败');
                return null;
            }
            return `${TWT.static}/${data}`;
        } catch (e) {
            system.error(e);
            return null;
        } finally {
            setUploading(false);
        }
    };

    /** 轮询图片生成任务状态 */
    const pollTaskStatus = async (taskId: number) => {
        return new Promise<void>((resolve) => {
            const timer = setInterval(async () => {
                try {
                    const { code, data } = await getImageTaskStatus(taskId);
                    if (code !== 200) {
                        clearInterval(timer);
                        return resolve();
                    }
                    const taskData = data?.data ?? data;
                    const status = taskData?.status;
                    const url = taskData?.result_url ?? taskData?.resultUrl;
                    if (status === 'completed') {
                        clearInterval(timer);
                        if (url) {
                            // 完成即自动弹出预览，替代原「生成结果」大卡片
                            setResultUrl(url);
                            setPreviewOpen(true);
                            message.success('图片生成完成');
                        } else {
                            message.error('任务完成但未返回图片链接');
                        }
                        return resolve();
                    }
                    if (status === 'failed') {
                        clearInterval(timer);
                        message.error(taskData?.error_msg || '图片生成失败');
                        return resolve();
                    }
                } catch (e) {
                    clearInterval(timer);
                    system.error(e);
                    return resolve();
                }
            }, 3000);
        });
    };

    /** 提交生成图片 */
    const handleGenerate = async () => {
        const fields = await form.validateFields().catch(() => null);
        if (!fields) return;
        try {
            setLoading(true);

            const params: Record<string, any> = {
                genType,
                prompt: fields.prompt,
                size: actualSize,
                enhancePrompt: fields.enhancePrompt ?? true,
            };
            // 用户在前端选的模型 id(可空, 不传走 ai_model 表 default_flag=1)
            if (fields.modelId) {
                params.modelId = fields.modelId;
            }
            if (fields.seed !== undefined && fields.seed !== null) {
                params.seed = fields.seed;
            }

            if (genType === 'img2img') {
                if (!uploadedImageUrl) {
                    message.error('请先上传输入图片');
                    return;
                }
                params.inputImage = uploadedImageUrl;
            } else if (genType === 'multi2img') {
                if (multiUrlList.length < 2) {
                    message.error('多图合成至少需要 2 张参考图');
                    return;
                }
                params.inputImage = JSON.stringify(multiUrlList);
            }

            const { code, msg, data } = await generateImage(params);
            if (code !== 200) {
                return message.error(msg);
            }
            message.success(msg || '任务已创建，正在生成中...');
            setResultUrl('');
            setPreviewOpen(false);

            // Java AiServiceClient.unwrapBody() 已解开外层 {code, msg, data}；
            // 兼容部分场景下可能出现的 data.data 双层嵌套
            const taskData = data?.data ?? data;
            const taskId = taskData?.task_id ?? taskData?.taskId;
            if (!taskId) {
                message.error('未获取到任务ID，生成失败');
                return;
            }
            await pollTaskStatus(taskId);
            loadAll(1);
        } catch (e) {
            system.error(e);
        } finally {
            setLoading(false);
        }
    };

    /** 删除图片任务（任务中心失败任务 / 画廊卡片共用） */
    const refRemove = async (taskId: string | number | undefined) => {
        try {
            if (!taskId) return true;
            const { code, msg } = await delImageTask(taskId);
            if (code !== 200) {
                return message.error(msg);
            }
            message.success(msg);
            loadAll();
        } catch (e) {
            system.error(e);
        }
    };

    // 一键设为定妆照：打开选人设弹窗（人设列表懒加载一次）
    const openLookPicker = async (url: string) => {
        setLookPicker({ open: true, url });
        setLookPersonaId(undefined);
        if (lookPersonas.length === 0) {
            try {
                const res = await listPersonas();
                if (res?.code === 200) {
                    setLookPersonas((res.data?.rows || []).map((p) => ({
                        persona_id: p.persona_id,
                        name: p.name,
                    })));
                }
            } catch {
                message.error('人设列表加载失败');
            }
        }
    };

    const doSetPersonaLook = async () => {
        if (!lookPersonaId) {
            message.warning('请选择人设');
            return;
        }
        setLookSetting(true);
        try {
            const res = await setPersonaLook({
                persona_id: lookPersonaId,
                image_url: lookPicker.url,
                primary: true,
            });
            if (res?.code === 200) {
                const name = lookPersonas.find((p) => p.persona_id === lookPersonaId)?.name;
                message.success(`已设为「${name || '人设'}」的主定妆照，之后分镜生图会以它为参考保持人物一致`);
                setLookPicker({ open: false, url: '' });
            } else {
                message.error(res?.msg || '设置失败');
            }
        } catch {
            message.error('设置失败，请重试');
        } finally {
            setLookSetting(false);
        }
    };

    /** 下载图片 */
    const handleDownload = (url: string) => {
        if (!url) {
            message.warning('暂无图片可下载');
            return;
        }
        const link = document.createElement('a');
        link.href = url;
        link.download = `generated-image-${Date.now()}.png`;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
    };

    const renderRatioPicker = () => (
        <Row gutter={[8, 8]}>
            {availableRatios.map((r) => (
                <Col key={r.value} span={6}>
                    <div
                        className={`${styles.ratioTile} ${ratio === r.value ? styles.ratioTileActive : ''}`}
                        onClick={() => setRatio(r.value)}
                    >
                        <div
                            className={styles.ratioShape}
                            style={{ width: r.w, height: r.h, color: ratio === r.value ? '#4A6B57' : undefined }}
                        />
                        <span className={styles.ratioLabel}>{r.value}</span>
                    </div>
                </Col>
            ))}
        </Row>
    );

    return (
        <PageContainer header={{ title: null, breadcrumb: {} }}>
            <div className={styles.header}>
                <h2 className={styles.headerTitle}>AI 绘图工作台</h2>
                <p className={styles.headerDesc}>
                    文生图 / 图生图 / 多图合成，支持 1K-4K 档位与 8 种宽高比，智能提示词增强让描述一步到位
                </p>
            </div>
            <Row gutter={[16, 16]}>
                {/* 左侧：创作面板 */}
                <Col xs={24} xl={8}>
                    <Card title="创作" bordered={false} style={{ borderRadius: 12 }}>
                        <Form form={form} layout="vertical">
                            <Segmented
                                block
                                value={genType}
                                onChange={(v) => {
                                    setGenType(v as string);
                                    setFileList([]);
                                    setUploadedImageUrl('');
                                    setMultiUrls({});
                                }}
                                options={[
                                    { label: '文生图', value: 'text2img', icon: <PictureOutlined /> },
                                    { label: '图生图', value: 'img2img', icon: <EditOutlined /> },
                                    { label: '多图合成', value: 'multi2img', icon: <AppstoreOutlined /> },
                                ]}
                            />

                            {genType === 'img2img' && (
                                <Form.Item label="参考图" required style={{ marginTop: 16 }}>
                                    <Upload
                                        listType="picture-card"
                                        maxCount={1}
                                        fileList={fileList}
                                        beforeUpload={(file) => {
                                            if (!file.type.startsWith('image/')) {
                                                message.error('只能上传图片文件');
                                                return false;
                                            }
                                            if (file.size / 1024 / 1024 >= 10) {
                                                message.error('图片大小不能超过10MB');
                                                return false;
                                            }
                                            setFileList([file as unknown as UploadFile]);
                                            handleUpload(file).then((url) => url && setUploadedImageUrl(url));
                                            return false;
                                        }}
                                        onRemove={() => {
                                            setFileList([]);
                                            setUploadedImageUrl('');
                                        }}
                                    >
                                        {fileList.length === 0 && (
                                            <div>
                                                <UploadOutlined />
                                                <div style={{ marginTop: 8 }}>上传</div>
                                            </div>
                                        )}
                                    </Upload>
                                </Form.Item>
                            )}

                            {genType === 'multi2img' && (
                                <Form.Item label="参考图（2-3 张，第一张为主图）" required style={{ marginTop: 16 }}>
                                    <Upload
                                        listType="picture-card"
                                        maxCount={3}
                                        fileList={fileList}
                                        beforeUpload={(file) => {
                                            if (!file.type.startsWith('image/')) {
                                                message.error('只能上传图片文件');
                                                return false;
                                            }
                                            if (file.size / 1024 / 1024 >= 10) {
                                                message.error('图片大小不能超过10MB');
                                                return false;
                                            }
                                            const uid = `${file.uid}`;
                                            setFileList((prev) => [...prev, file as unknown as UploadFile]);
                                            handleUpload(file).then((url) => url && setMultiUrls((prev) => ({ ...prev, [uid]: url })));
                                            return false;
                                        }}
                                        onRemove={(removed) => {
                                            setFileList((prev) => prev.filter((f) => f.uid !== removed.uid));
                                            setMultiUrls((prev) => {
                                                const next = { ...prev };
                                                delete next[removed.uid];
                                                return next;
                                            });
                                        }}
                                    >
                                        {fileList.length < 3 && (
                                            <div>
                                                <PlusOutlined />
                                                <div style={{ marginTop: 8 }}>上传</div>
                                            </div>
                                        )}
                                    </Upload>
                                </Form.Item>
                            )}

                            <Form.Item
                                label="画面描述"
                                name="prompt"
                                rules={[{ required: true, message: '请输入画面描述' }]}
                            >
                                <Input.TextArea
                                    rows={4}
                                    showCount
                                    maxLength={800}
                                    placeholder={
                                        genType === 'text2img'
                                            ? '描述你想生成的画面，如：日出时分薄雾峡谷上方的发光浮空城市，电影级写实风格'
                                            : genType === 'img2img'
                                              ? '描述想对参考图做什么变换，如：改成赛博朋克夜景，保留原图构图'
                                              : '描述如何组合参考图，如：第一张为主角，第二张为产品参考，生成商业海报'
                                    }
                                />
                            </Form.Item>

                            <Form.Item label="生成模型" name="modelId" tooltip="默认走推荐模型">
                                <ModelSelect
                                    modelType="IMAGE"
                                    onModelInfo={(m) => {
                                        const next = m?.sizes ?? [];
                                        setModelSizes((prev) =>
                                            prev.join(',') === next.join(',') ? prev : next,
                                        );
                                    }}
                                />
                            </Form.Item>

                            <Form.Item label="智能提示词增强" name="enhancePrompt" initialValue={true} valuePropName="checked">
                                <Switch checkedChildren="开" unCheckedChildren="关" />
                            </Form.Item>

                            <Form.Item label="宽高比">
                                {renderRatioPicker()}
                                {modelSizes.length > 0 && (
                                    <div
                                        style={{
                                            fontSize: 12,
                                            color: 'rgba(0,0,0,0.45)',
                                            marginTop: 8,
                                            lineHeight: 1.6,
                                        }}
                                    >
                                        当前模型仅支持 {availableRatios.map((r) => r.value).join(' / ')} 比例，
                                        可选输出尺寸：{modelSizes.join(' / ')}，已据此收敛选项。
                                    </div>
                                )}
                            </Form.Item>

                            <Form.Item label={`清晰度 · 输出 ${actualSize}`}>
                                <Segmented
                                    block
                                    options={availableQualities}
                                    value={quality}
                                    onChange={(v) => setQuality(v as string)}
                                />
                            </Form.Item>

                            <Collapse
                                ghost
                                items={[
                                    {
                                        key: 'advanced',
                                        label: '高级设置',
                                        children: (
                                            <Form.Item label="Seed（复现结果）" name="seed">
                                                <InputNumber min={0} max={4294967295} style={{ width: '100%' }} placeholder="留空随机" />
                                            </Form.Item>
                                        ),
                                    },
                                ]}
                            />

                            <Button type="primary" size="large" block loading={loading || uploading} onClick={handleGenerate}>
                                {uploading ? '上传图片中...' : '立即生成'}
                            </Button>
                        </Form>
                    </Card>
                </Col>

                {/* 右侧：任务中心 + 作品画廊 */}
                <Col xs={24} xl={16}>
                    <TaskCenter
                        tasks={tasks}
                        onDelete={(task) => refRemove(task.taskId)}
                    />

                    <Card
                        title={`作品库（${galleryTotal}）`}
                        bordered={false}
                        style={{ borderRadius: 12 }}
                        extra={
                            <Space>
                                <Segmented
                                    size="small"
                                    value={genTypeFilter}
                                    onChange={(v) => setGenTypeFilter(v as string)}
                                    options={[
                                        { label: '全部', value: 'all' },
                                        ...Object.entries(GEN_TYPE_LABELS).map(([value, label]) => ({
                                            label,
                                            value,
                                        })),
                                    ]}
                                />
                                <Button icon={<ReloadOutlined />} size="small" onClick={() => loadAll()}>
                                    刷新
                                </Button>
                            </Space>
                        }
                    >
                        {gallery.length === 0 ? (
                            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无作品" />
                        ) : (
                            <Row gutter={[12, 12]}>
                                {gallery.map((task) => {
                                    const url = task.resultUrl;
                                    return (
                                        <Col key={task.taskId} xs={12} sm={8} md={6}>
                                            <div className={styles.galleryCard}>
                                                {url && (
                                                    <>
                                                        <Popconfirm
                                                            className={styles.galleryAction}
                                                            title="确定删除吗"
                                                            onConfirm={() => refRemove(task.taskId)}
                                                        >
                                                            <Button type="text" size="small" danger icon={<DeleteOutlined />} />
                                                        </Popconfirm>
                                                        <Tooltip title="下载图片">
                                                            <Button
                                                                className={styles.galleryAction}
                                                                style={{ right: 40 }}
                                                                type="text"
                                                                size="small"
                                                                icon={<DownloadOutlined />}
                                                                onClick={() => handleDownload(url)}
                                                            />
                                                        </Tooltip>
                                                        <Tooltip title="设为人设定妆照（主形象，分镜生图参考它保持人物一致）">
                                                            <Button
                                                                className={styles.galleryAction}
                                                                style={{ right: 72 }}
                                                                type="text"
                                                                size="small"
                                                                icon={<TeamOutlined />}
                                                                onClick={() => openLookPicker(url)}
                                                            />
                                                        </Tooltip>
                                                    </>
                                                )}
                                                <div style={{ aspectRatio: '1 / 1', background: '#f5f4ef' }}>
                                                    {url && (
                                                        <Image
                                                            src={url}
                                                            alt={task.prompt}
                                                            width="100%"
                                                            height="100%"
                                                            style={{ objectFit: 'cover' }}
                                                        />
                                                    )}
                                                </div>
                                                <div style={{ padding: '8px 10px' }}>
                                                    <Tooltip title={task.prompt} placement="top">
                                                        <div
                                                            style={{
                                                                fontSize: 12,
                                                                color: '#666',
                                                                overflow: 'hidden',
                                                                textOverflow: 'ellipsis',
                                                                whiteSpace: 'nowrap',
                                                            }}
                                                        >
                                                            {GEN_TYPE_LABELS[task.genType]
                                                                ? `${GEN_TYPE_LABELS[task.genType]} · ${task.prompt || '-'}`
                                                                : task.prompt || '-'}
                                                        </div>
                                                    </Tooltip>
                                                </div>
                                            </div>
                                        </Col>
                                    );
                                })}
                            </Row>
                        )}
                        {galleryTotal > galleryPageSize && (
                            <div style={{ textAlign: 'right', marginTop: 16 }}>
                                <a
                                    onClick={() => loadGallery(Math.max(1, galleryPage - 1))}
                                    style={{ marginRight: 12, display: galleryPage > 1 ? 'inline' : 'none' }}
                                >
                                    上一页
                                </a>
                                <span style={{ color: '#999', fontSize: 12 }}>
                                    {galleryPage} / {Math.ceil(galleryTotal / galleryPageSize)}
                                </span>
                                <a
                                    onClick={() => loadGallery(galleryPage + 1)}
                                    style={{ marginLeft: 12, display: galleryPage < Math.ceil(galleryTotal / galleryPageSize) ? 'inline' : 'none' }}
                                >
                                    下一页
                                </a>
                            </div>
                        )}
                    </Card>

                    {/* 受控预览：任务完成自动弹出；平时隐藏，点击画廊卡片上的图也可预览 */}
                    {resultUrl && (
                        <Image
                            style={{ display: 'none' }}
                            src={resultUrl}
                            preview={{
                                visible: previewOpen,
                                src: resultUrl,
                                onVisibleChange: (open: boolean) => setPreviewOpen(open),
                            }}
                        />
                    )}
                </Col>
            </Row>

            <Modal
                title="设为人设定妆照"
                open={lookPicker.open}
                onCancel={() => setLookPicker({ open: false, url: '' })}
                onOk={doSetPersonaLook}
                confirmLoading={lookSetting}
                okText="设为主形象"
                width={420}
            >
                <Space direction="vertical" style={{ width: '100%' }} size={12}>
                    {lookPicker.url && (
                        <Image src={lookPicker.url} alt="待设为定妆照" style={{ maxHeight: 220, borderRadius: 8 }} />
                    )}
                    <Form layout="vertical">
                        <Form.Item label="目标人设" required>
                            <Select
                                placeholder="选择要绑定形象的人设"
                                value={lookPersonaId}
                                onChange={setLookPersonaId}
                                options={lookPersonas.map((p) => ({ label: p.name, value: p.persona_id }))}
                            />
                        </Form.Item>
                    </Form>
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                        设为主形象后会排到定妆照首位，之后所有分镜生图都会以它为参考，保持跨镜人物一致
                    </Typography.Text>
                </Space>
            </Modal>
        </PageContainer>
    );
};

export default ImagePage;
