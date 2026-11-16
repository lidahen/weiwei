import { canSubmitKyc, toKycPayload } from './mySettlementView';

describe('mySettlementView', () => {
  it('toKycPayload 去首尾空格，证件号 X 大写', () => {
    expect(
      toKycPayload({
        accountName: ' 张三 ',
        idCardNo: '11010119900101123x',
        bankCardNo: ' 6222020200112233445 ',
        bankName: ' 工商银行 ',
      }),
    ).toEqual({
      accountName: '张三',
      idCardNo: '11010119900101123X',
      bankCardNo: '6222020200112233445',
      bankName: '工商银行',
    });
  });

  it('canSubmitKyc 仅在四项合法时为 true', () => {
    expect(
      canSubmitKyc({
        accountName: '张三',
        idCardNo: '110101199001011234',
        bankCardNo: '6222020200112233445',
        bankName: '工商银行',
      }),
    ).toBe(true);
    expect(canSubmitKyc({ accountName: '', idCardNo: '', bankCardNo: '', bankName: '' })).toBe(false);
  });
});