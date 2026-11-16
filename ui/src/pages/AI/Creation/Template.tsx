import { DeleteOutlined, ReloadOutlined } from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import {
    Button,
    Card,
    Descriptions,
    Empty,
    List,
    message,
    Modal,
    Popconfirm,
    Space,
    Tag,
    Typography,
} from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { useIsMobile } from '@/hooks/useIsMobile';
import { getTemplate, listTemplates, removeTemplates, AiViralTemplate } from './service';

const ROLE_LABELS: Record<string, string> = {
    hook: '钩子',
    body: '正文',
    proof: '证明',
    cta: '号召',
};

/** 结构模板 → 人话摘要（对非技术用户直出 JSON 不可读；异常结构回退 JSON 原文） */
const StructureView: React.FC<{ structure: Record<string, any> | null | undefined }> = ({ structure }) => {
    if (!structure || (!structure.hook && !structure.shots)) {
        return (
            <pre style={{ maxHeight: 480, overflow: 'auto', fontSize: 12 }}>
                {structure ? JSON.stringify(structure, null, 2) : '（无结构数据）'}
            </pre>
        );
    }
    const hook = structure.hook || {};
    const shots = Array.isArray(structure.shots) ? structure.shots : [];
    return (
        <div style={{ maxHeight: 480, overflow: 'auto' }}>
            <Descriptions size="small" column={1} bordered>
                <Descriptions.Item label="钩子类型">{hook.type || '-'}</Descriptions.Item>
                <Descriptions.Item label="钩子套路">{hook.pattern || '-'}</Descriptions.Item>
                <Descriptions.Item label="首镜画面">{hook.visual || '-'}</Descriptions.Item>
                <Descriptions.Item label="叙事节奏">{structure.rhythm || '-'}</Descriptions.Item>
                <Descriptions.Item label="视觉风格">{structure.visual_style || '-'}</Descriptions.Item>
                <Descriptions.Item label="CTA 结构">{structure.cta || '-'}</Descriptions.Item>
            </Descriptions>
            <Typography.Title level={5} style={{ marginTop: 12 }}>
                分镜骨架（{shots.length} 镜）
            </Typography.Title>
            <ol style={{ paddingLeft: 20, margin: 0 }}>
                {shots.map((s: any, i: number) => (
                    <li key={i} style={{ marginBottom: 4, fontSize: 13 }}>
                        <Tag>{ROLE_LABELS[s?.role] || s?.role || '-'}</Tag>
                        {[s?.visual_type, s?.lens, s?.transition].filter(Boolean).join(' · ')}
                        {s?.duration_ratio !== undefined && ` · 时长占比 ${s.duration_ratio}`}
                    </li>
                ))}
            </ol>
        </div>
    );
};

const TemplateLibrary: React.FC = () => {
    const isMobile = useIsMobile();
    const [rows, setRows] = useState<AiViralTemplate[]>([]);
    const [loading, setLoading] = useState(false);

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const res = await listTemplates();
            if (res?.code === 200) {
                setRows(res.data?.rows || []);
            }
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        load();
    }, [load]);

    const viewDetail = useCallback(
        async (templateId: number) => {
            const res = await getTemplate(templateId);
            if (res?.code !== 200 || !res.data) {
                message.error(res?.msg || '加载模板详情失败');
                return;
            }
            Modal.info({
                title: res.data.title || '结构模板',
                width: isMobile ? '94%' : 640,
                content: <StructureView structure={res.data.structure} />,
            });
        },
        [isMobile],
    );

    const doRemove = useCallback(
        async (templateId: number) => {
            const res = await removeTemplates(String(templateId));
            if (res?.code === 200) {
                message.success('已删除');
                load();
            } else {
                message.error(res?.msg || '删除失败');
            }
        },
        [load],
    );

    return (
        <PageContainer title={false} extra={[<Button key="refresh" icon={<ReloadOutlined />} onClick={load} />]}>
            <Card loading={loading}>
                {rows.length === 0 ? (
                    <Empty description="暂无爆款模板：在「创作项目 → 新建作品 → 复刻爆款」中粘贴链接拆解" />
                ) : (
                    <List
                        dataSource={rows}
                        renderItem={(item) => (
                            <List.Item
                                actions={[
                                    <Button key="view" type="link" onClick={() => viewDetail(item.template_id)}>
                                        查看结构
                                    </Button>,
                                    <Popconfirm
                                        key="del"
                                        title="确认删除该模板？"
                                        onConfirm={() => doRemove(item.template_id)}
                                    >
                                        <Button type="link" danger icon={<DeleteOutlined />} />
                                    </Popconfirm>,
                                ]}
                            >
                                <List.Item.Meta
                                    title={
                                        <Space wrap>
                                            <span>{item.title || `模板 #${item.template_id}`}</span>
                                            {item.hook_type && <Tag color="orange">{item.hook_type}</Tag>}
                                            <Tag>{item.shot_count || '-'} 镜</Tag>
                                            <Tag>{item.duration_sec ? `${item.duration_sec}s` : '-'}</Tag>
                                        </Space>
                                    }
                                    description={
                                        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                                            拆解时间：{item.create_time}
                                        </Typography.Text>
                                    }
                                />
                            </List.Item>
                        )}
                    />
                )}
            </Card>
        </PageContainer>
    );
};

export default TemplateLibrary;
