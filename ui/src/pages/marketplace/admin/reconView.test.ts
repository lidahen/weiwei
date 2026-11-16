import { reconActions, reconTypeText, validateResolveNote, validateUnfreezeReason } from './reconView';

describe('reconView', () => {
  it('差异类型中文', () => {
    expect(reconTypeText('missing_settlement')).toBe('漏单：已支付无结算单');
    expect(reconTypeText('phantom_settlement')).toBe('虚单：结算单无支付流水');
    expect(reconTypeText('amount_mismatch')).toBe('金额不一致');
    expect(reconTypeText('status_mismatch')).toBe('退款后结算状态未收口');
    expect(reconTypeText('ledger_mismatch')).toBe('账本与余额不一致');
    expect(reconTypeText('other')).toBe('other');
  });

  it('open 只能处理，解冻只在差异闭环后出现', () => {
    expect(reconActions('open')).toEqual(['resolve']);
    expect(reconActions('resolved')).toEqual(['unfreeze']);
  });

  it('处理说明与解冻原因必填', () => {
    expect(validateResolveNote('')).toBe('请填写处理说明');
    expect(validateResolveNote('   ')).toBe('请填写处理说明');
    expect(validateResolveNote('已人工补单')).toBeNull();
    expect(validateUnfreezeReason('')).toBe('请填写解冻原因');
    expect(validateUnfreezeReason('对账已补齐')).toBeNull();
  });
});