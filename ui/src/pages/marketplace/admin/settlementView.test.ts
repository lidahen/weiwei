import { settlementRow } from './settlementView';

describe('settlementView', () => {
  it('金额与状态派生为展示文本', () => {
    expect(
      settlementRow({
        grossAmount: 100,
        commissionAmount: 0,
        netAmount: 100,
        status: 'holding',
        holdUntil: '2026-11-09 10:00:00',
      }),
    ).toMatchObject({
      grossText: '¥100.00',
      commissionText: '¥0.00',
      netText: '¥100.00',
      statusText: '担保中',
      holdUntilText: '2026-11-09 10:00',
    });
  });

  it('空金额与未知状态兜底', () => {
    expect(settlementRow({ grossAmount: null, status: 'settled' }).grossText).toBe('-');
    expect(settlementRow({ status: 'settled' }).statusText).toBe('已结算');
    expect(settlementRow({ status: 'unknown' }).statusText).toBe('unknown');
    expect(settlementRow({ holdUntil: null }).holdUntilText).toBe('-');
  });
});