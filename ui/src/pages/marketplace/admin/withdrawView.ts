/** 提现单状态 → 可执行动作：待审核可批/驳，已通过可打款，其余无操作 */
export function withdrawActions(status: string): string[] {
    if (status === 'pending') return ['approve', 'reject'];
    if (status === 'approved') return ['pay'];
    return [];
}

/**
 * 从 UploadFile 提取凭证路径：只接受上传完成的文件（status === 'done'）。
 * 上传中 thumbUrl 是本地 data URL，误当路径会把占位符写进账务记录。
 */
export function voucherFromFile(file: any, staticBase = ''): string | undefined {
    if (!file || file.status !== 'done') return undefined;
    const response = file.response;
    if (response) {
        const data = response.data ?? response.imgUrl;
        if (typeof data === 'string' && data) return data;
    }
    const url = file.url || file.thumbUrl;
    if (typeof url === 'string' && url && !url.startsWith('data:')) {
        return url.replace(staticBase, '').split('?')[0] || undefined;
    }
    return undefined;
}

/** 打款凭证校验：必须上传一张凭证 */
export function validateVoucher(url?: string | null): string | null {
    return url && String(url).trim() ? null : '请上传打款凭证';
}

/** 驳回原因校验：必填 */
export function validateReason(text?: string | null): string | null {
    return text && String(text).trim() ? null : '请填写驳回原因';
}