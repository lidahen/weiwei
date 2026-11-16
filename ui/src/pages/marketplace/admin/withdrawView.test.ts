import { validateReason, validateVoucher, voucherFromFile, withdrawActions } from './withdrawView';

describe('withdrawView', () => {
  it('提现状态决定操作', () => {
    expect(withdrawActions('pending')).toEqual(['approve', 'reject']);
    expect(withdrawActions('approved')).toEqual(['pay']);
    expect(withdrawActions('paid')).toEqual([]);
    expect(withdrawActions('rejected')).toEqual([]);
  });

  it('打款必须上传凭证', () => {
    expect(validateVoucher('')).toBe('请上传打款凭证');
    expect(validateVoucher('/dfs/a.png')).toBeNull();
  });

  it('打款凭证只接受上传完成的文件', () => {
    expect(voucherFromFile({ status: 'uploading', thumbUrl: 'data:image/png;base64,AAAA' }, '/static')).toBeUndefined();
    expect(voucherFromFile({ status: 'done', response: { data: '/dfs/a.png' } }, '/static')).toBe('/dfs/a.png');
    expect(
      voucherFromFile({ status: 'done', url: 'https://oss.example.com/dfs/a.png?x=1' }, 'https://oss.example.com'),
    ).toBe('/dfs/a.png');
    expect(voucherFromFile({ status: 'done', thumbUrl: 'data:image/png;base64,AAAA' }, '/static')).toBeUndefined();
  });

  it('驳回必须填写原因', () => {
    expect(validateReason('')).toBe('请填写驳回原因');
    expect(validateReason('   ')).toBe('请填写驳回原因');
    expect(validateReason('资料不符')).toBeNull();
  });
});