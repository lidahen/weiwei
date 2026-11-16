import { formatPrice } from '@/pages/marketplace/utils';

/** 状态展示元数据：中文文案 + antd Tag 颜色 */
export type StatusMeta = { text: string; color: string };

export const SETTLEMENT_STATUS: Record<string, StatusMeta> = {
  holding: { text: '担保中', color: 'blue' },
  pending_settle: { text: '待打款', color: 'gold' },
  settled: { text: '已结算', color: 'green' },
  withdrawn: { text: '已提现', color: 'cyan' },
  closed: { text: '已关闭', color: 'default' },
  clawback: { text: '已扣回', color: 'red' },
};

export const WITHDRAW_STATUS: Record<string, StatusMeta> = {
  pending: { text: '待审核', color: 'gold' },
  approved: { text: '待打款', color: 'blue' },
  paid: { text: '已打款', color: 'green' },
  rejected: { text: '已驳回', color: 'red' },
};

export const KYC_STATUS: Record<string, StatusMeta> = {
  unverified: { text: '未实名', color: 'default' },
  pending: { text: '审核中', color: 'gold' },
  verified: { text: '已通过', color: 'green' },
  rejected: { text: '已驳回', color: 'red' },
};

export const RECON_STATUS: Record<string, StatusMeta> = {
  open: { text: '待处理', color: 'red' },
  resolved: { text: '已处理', color: 'green' },
};

/** 对账差异类型中文（未知类型原样显示） */
export const RECON_TYPES: Record<string, string> = {
  missing_settlement: '漏单：已支付无结算单',
  phantom_settlement: '虚单：结算单无支付流水',
  amount_mismatch: '金额不一致',
  status_mismatch: '退款后结算状态未收口',
  duplicate_settlement: '重复结算单',
  ledger_mismatch: '账本与余额不一致',
};

export function statusMeta(map: Record<string, StatusMeta>, status: string): StatusMeta {
  return map[status] ?? { text: status, color: 'default' };
}

export function reconTypeText(type: string): string {
  return RECON_TYPES[type] ?? type;
}

/** 金额展示：空值 '-'，其余与 formatPrice 一致 */
export function moneyText(value?: number | string | null): string {
  if (value === undefined || value === null || value === '') return '-';
  return formatPrice(value);
}

/** 字符串金额 → number；空值/非法值按 0 */
export function toAmount(value: string): number {
  const number = Number(value);
  return Number.isFinite(number) ? number : 0;
}

/** 提现金额校验：>0、最多两位小数、不超过可用余额 */
export function validateWithdrawAmount(amountText: string, balance: number): string | null {
  const text = (amountText ?? '').trim();
  if (!text) return '提现金额必须大于 0';
  const amount = Number(text);
  if (!Number.isFinite(amount) || amount <= 0) return '提现金额必须大于 0';
  if (!/^\d+(\.\d{1,2})?$/.test(text)) return '金额最多两位小数';
  if (typeof balance === 'number' && amount > balance) return '超过可用余额';
  return null;
}

export type KycForm = {
  accountName: string;
  idCardNo: string;
  bankCardNo: string;
  bankName: string;
};

/** 实名表单校验：四项必填，证件号 15/18 位，银行卡 12–19 位数字 */
export function validateKycForm(form: KycForm): string | null {
  if (!form.accountName?.trim()) return '请填写实名姓名';
  if (!/^(\d{15}|\d{17}[\dXx])$/.test(form.idCardNo?.trim() ?? '')) return '证件号格式不正确';
  if (!/^\d{12,19}$/.test(form.bankCardNo?.trim() ?? '')) return '银行卡号格式不正确';
  if (!form.bankName?.trim()) return '请填写开户行';
  return null;
}