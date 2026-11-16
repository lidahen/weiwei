import type { KycForm } from './admin/settlementShared';
import { validateKycForm } from './admin/settlementShared';

/** 实名表单 → 提交负载：去首尾空格，证件号末位 x 统一大写 */
export function toKycPayload(form: KycForm): KycForm {
    return {
        accountName: (form.accountName ?? '').trim(),
        idCardNo: (form.idCardNo ?? '').trim().toUpperCase(),
        bankCardNo: (form.bankCardNo ?? '').trim(),
        bankName: (form.bankName ?? '').trim(),
    };
}

/** 实名表单是否可提交：四项合法（与共享校验同源） */
export function canSubmitKyc(form: KycForm): boolean {
    return validateKycForm(toKycPayload(form)) === null;
}