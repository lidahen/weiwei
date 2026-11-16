import {
  KYC_STATUS,
  RECON_STATUS,
  RECON_TYPES,
  SETTLEMENT_STATUS,
  WITHDRAW_STATUS,
  moneyText,
  statusMeta,
  toAmount,
  validateKycForm,
  validateWithdrawAmount,
} from './settlementShared';

describe('settlementShared', () => {
  it('结算状态中文与颜色固定，未知状态原样返回', () => {
    expect(statusMeta(SETTLEMENT_STATUS, 'holding')).toEqual({ text: '担保中', color: 'blue' });
    expect(statusMeta(SETTLEMENT_STATUS, 'pending_settle')).toEqual({ text: '待打款', color: 'gold' });
    expect(statusMeta(SETTLEMENT_STATUS, 'settled')).toEqual({ text: '已结算', color: 'green' });
    expect(statusMeta(SETTLEMENT_STATUS, 'withdrawn')).toEqual({ text: '已提现', color: 'cyan' });
    expect(statusMeta(SETTLEMENT_STATUS, 'closed')).toEqual({ text: '已关闭', color: 'default' });
    expect(statusMeta(SETTLEMENT_STATUS, 'clawback')).toEqual({ text: '已扣回', color: 'red' });
    expect(statusMeta(SETTLEMENT_STATUS, 'unknown')).toEqual({ text: 'unknown', color: 'default' });
  });

  it('提现/实名/差异状态映射', () => {
    expect(statusMeta(WITHDRAW_STATUS, 'pending')).toEqual({ text: '待审核', color: 'gold' });
    expect(statusMeta(WITHDRAW_STATUS, 'approved')).toEqual({ text: '待打款', color: 'blue' });
    expect(statusMeta(WITHDRAW_STATUS, 'paid')).toEqual({ text: '已打款', color: 'green' });
    expect(statusMeta(WITHDRAW_STATUS, 'rejected')).toEqual({ text: '已驳回', color: 'red' });
    expect(statusMeta(KYC_STATUS, 'unverified')).toEqual({ text: '未实名', color: 'default' });
    expect(statusMeta(KYC_STATUS, 'pending')).toEqual({ text: '审核中', color: 'gold' });
    expect(statusMeta(KYC_STATUS, 'verified')).toEqual({ text: '已通过', color: 'green' });
    expect(statusMeta(KYC_STATUS, 'rejected')).toEqual({ text: '已驳回', color: 'red' });
    expect(statusMeta(RECON_STATUS, 'open')).toEqual({ text: '待处理', color: 'red' });
    expect(statusMeta(RECON_STATUS, 'resolved')).toEqual({ text: '已处理', color: 'green' });
  });

  it('差异类型中文', () => {
    expect(RECON_TYPES.missing_settlement).toBe('漏单：已支付无结算单');
    expect(RECON_TYPES.phantom_settlement).toBe('虚单：结算单无支付流水');
    expect(RECON_TYPES.amount_mismatch).toBe('金额不一致');
    expect(RECON_TYPES.status_mismatch).toBe('退款后结算状态未收口');
    expect(RECON_TYPES.ledger_mismatch).toBe('账本与余额不一致');
  });

  it('金额空值显示 -，其余复用 formatPrice', () => {
    expect(moneyText(null)).toBe('-');
    expect(moneyText(undefined)).toBe('-');
    expect(moneyText(1234.5)).toBe('¥1,234.50');
    expect(moneyText('0.00')).toBe('¥0.00');
  });

  it('提现金额校验：正数、两位小数、不超过余额', () => {
    expect(validateWithdrawAmount('0', 100)).toBe('提现金额必须大于 0');
    expect(validateWithdrawAmount('', 100)).toBe('提现金额必须大于 0');
    expect(validateWithdrawAmount('120', 100)).toBe('超过可用余额');
    expect(validateWithdrawAmount('10.123', 100)).toBe('金额最多两位小数');
    expect(validateWithdrawAmount('10.50', 100)).toBeNull();
    expect(validateWithdrawAmount('100', 100)).toBeNull();
  });

  it('实名表单校验：必填与格式', () => {
    expect(validateKycForm({ accountName: '', idCardNo: '1', bankCardNo: '1', bankName: '' })).not.toBeNull();
    expect(
      validateKycForm({ accountName: '张三', idCardNo: '123', bankCardNo: '6222020200112233445', bankName: '工商银行' }),
    ).toBe('证件号格式不正确');
    expect(
      validateKycForm({ accountName: '张三', idCardNo: '110101199001011234', bankCardNo: 'abc', bankName: '工商银行' }),
    ).toBe('银行卡号格式不正确');
    expect(
      validateKycForm({
        accountName: '张三',
        idCardNo: '110101199001011234',
        bankCardNo: '6222020200112233445',
        bankName: '工商银行',
      }),
    ).toBeNull();
  });

  it('toAmount 转数字', () => {
    expect(toAmount('10.50')).toBe(10.5);
    expect(toAmount('')).toBe(0);
  });
});