import { Input, message, Modal, Radio, Space } from 'antd';
import type { UploadFile } from 'antd/es/upload/interface';
import React, { useState } from 'react';
import UploadImages from './UploadImages';
import { reportSubmit } from '@/services/marketplace/api';
import { REPORT_REASONS, TARGET_TYPE_LABELS } from '@/utils/report';
import { requireLogin } from '@/utils/loginGuard';
import TWT from '@/setting';

const { TextArea } = Input;

interface Props {
    open: boolean;
    /** product / feed / user */
    targetType: string;
    targetId: number;
    /** 展示给用户看的对象名称，如「商品『九成新机械键盘』」 */
    targetLabel?: string;
    onClose: () => void;
}

const MAX_DETAIL = 200;
const MAX_IMAGES = 3;

/**
 * 举报弹窗（商品 / 动态 / 用户通用）
 *
 * 「其他」必须填说明：后端 compose 会把标签与详情拼成「标签：详情」，
 * 只留一个「其他」标签运营看不懂要处理什么。
 */
const ReportDialog: React.FC<Props> = ({ open, targetType, targetId, targetLabel, onClose }) => {
    const [reasonType, setReasonType] = useState<string>('');
    const [reasonDetail, setReasonDetail] = useState('');
    const [fileList, setFileList] = useState<UploadFile[]>([]);
    const [submitting, setSubmitting] = useState(false);

    const reset = () => {
        setReasonType('');
        setReasonDetail('');
        setFileList([]);
        setSubmitting(false);
    };

    const handleClose = () => {
        reset();
        onClose();
    };

    /** 从 UploadFile 中提取已上传的 OSS 相对路径（与 ProductPublish 同逻辑） */
    const getImagePath = (f: UploadFile): string | undefined => {
        const { response } = f as any;
        if (response) {
            const data = response.data ?? response.imgUrl;
            if (typeof data === 'string') return data;
        }
        const url = f.url || f.thumbUrl;
        if (url) {
            const path = url.replace(TWT.static, '').split('?')[0];
            return path || undefined;
        }
        return undefined;
    };

    const handleSubmit = async () => {
        if (!requireLogin()) return;
        if (!reasonType) {
            message.warning('请选择举报原因');
            return;
        }
        if (reasonType === 'other' && !reasonDetail.trim()) {
            message.warning('请填写具体原因');
            return;
        }

        const evidenceImages = fileList
            .filter((f) => f.status === 'done')
            .map(getImagePath)
            .filter(Boolean) as string[];

        setSubmitting(true);
        try {
            const res: any = await reportSubmit({
                targetType,
                targetId,
                reasonType,
                reasonDetail: reasonDetail.trim() || undefined,
                evidenceImages: evidenceImages.length ? evidenceImages : undefined,
            });
            message.success(res?.msg || '举报已提交');
            reset();
            onClose();
        } catch (e: any) {
            message.error(e?.response?.data?.msg || '提交失败，请稍后重试');
            setSubmitting(false);
        }
    };

    return (
        <Modal
            open={open}
            title={`举报${TARGET_TYPE_LABELS[targetType] || '内容'}`}
            okText="提交举报"
            cancelText="取消"
            confirmLoading={submitting}
            onOk={handleSubmit}
            onCancel={handleClose}
            destroyOnClose
            maskClosable={false}
        >
            {targetLabel && (
                <div style={{ marginBottom: 12, color: '#8c8c8c', wordBreak: 'break-all' }}>
                    {targetLabel}
                </div>
            )}

            <div style={{ marginBottom: 8 }}>举报原因</div>
            <Radio.Group value={reasonType} onChange={(e) => setReasonType(e.target.value)}>
                <Space direction="vertical">
                    {REPORT_REASONS.map((r) => (
                        <Radio key={r.value} value={r.value}>
                            {r.label}
                        </Radio>
                    ))}
                </Space>
            </Radio.Group>

            <div style={{ margin: '16px 0 8px' }}>
                补充说明
                {reasonType === 'other' && <span style={{ color: '#ff4d4f' }}>（必填）</span>}
            </div>
            <TextArea
                value={reasonDetail}
                onChange={(e) => setReasonDetail(e.target.value)}
                placeholder="补充说明（可留空，最多 200 字）"
                maxLength={MAX_DETAIL}
                showCount
                autoSize={{ minRows: 3, maxRows: 5 }}
            />

            <div style={{ margin: '16px 0 8px' }}>证据截图（选填，最多 {MAX_IMAGES} 张）</div>
            <UploadImages value={fileList} onChange={setFileList} max={MAX_IMAGES} />
        </Modal>
    );
};

export default ReportDialog;
