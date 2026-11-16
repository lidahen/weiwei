import { dictCategoryTree, interestInit } from '@/services/marketplace/api';
import { useRequest } from '@umijs/max';
import { Modal, Tag, message } from 'antd';
import React, { useEffect, useState } from 'react';
import { marketplaceTokens } from '@/pages/marketplace/styles/tokens';

export const INTEREST_INIT_FLAG = 'twt.interest.init.done';

export interface InterestGuideModalProps {
    open: boolean;
    /** 提交或跳过后回调（调用方负责关闭与标记完成） */
    onDone: (submitted: boolean) => void;
}

/**
 * 新用户兴趣引导：选择感兴趣的类目，写入画像种子（推荐立刻个性化，无需等行为积累）。
 * 弹出时机由调用方控制（登录用户 + localStorage 未标记）。
 */
const InterestGuideModal: React.FC<InterestGuideModalProps> = ({ open, onDone }) => {
    const { data: catResp } = useRequest(dictCategoryTree, { ready: open });
    const cats: any[] = ((catResp as any)?.data ?? (catResp as any)) || [];
    const [selected, setSelected] = useState<string[]>([]);
    const [submitting, setSubmitting] = useState(false);

    useEffect(() => {
        if (open) {
            setSelected([]);
        }
    }, [open]);

    const toggle = (code: string) => {
        setSelected((prev) =>
            prev.includes(code)
                ? prev.filter((c) => c !== code)
                : prev.length >= 5
                    ? prev
                    : [...prev, code],
        );
    };

    const submit = async () => {
        if (selected.length === 0) {
            message.warning('至少选择 1 个感兴趣的分类');
            return;
        }
        setSubmitting(true);
        try {
            await interestInit(selected);
            message.success('已记住你的兴趣，推荐更懂你了');
            onDone(true);
        }
        catch {
            message.error('保存失败，请稍后再试');
        }
        finally {
            setSubmitting(false);
        }
    };

    return (
        <Modal
            title="告诉我们你的兴趣，推荐更懂你"
            open={open}
            okText={`保存${selected.length ? `（已选 ${selected.length}/5）` : ''}`}
            cancelText="跳过"
            confirmLoading={submitting}
            onOk={submit}
            onCancel={() => onDone(false)}
            width={480}
        >
            <p style={{ color: marketplaceTokens.color.sub, fontSize: 13 }}>
                选择你常逛的分类（最多 5 个），首页推荐会优先展示这类好物；之后照常浏览也会自动学习你的偏好。
            </p>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginTop: 12 }}>
                {cats.map((c: any) => (
                    <Tag.CheckableTag
                        key={c.code}
                        checked={selected.includes(c.code)}
                        onChange={() => toggle(c.code)}
                        style={{
                            padding: '5px 14px',
                            fontSize: 13,
                            borderRadius: marketplaceTokens.radius.pill,
                            border: `1px solid ${selected.includes(c.code)
                                ? marketplaceTokens.color.primary
                                : marketplaceTokens.color.line}`,
                        }}
                    >
                        {c.name}
                    </Tag.CheckableTag>
                ))}
            </div>
        </Modal>
    );
};

export default InterestGuideModal;
