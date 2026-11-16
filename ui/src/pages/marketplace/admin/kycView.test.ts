import { kycActions, toReviewPayload } from './kycView';

describe('kycView', () => {
  it('仅 pending 状态可审核', () => {
    expect(kycActions('pending')).toEqual(['verified', 'rejected']);
    expect(kycActions('verified')).toEqual([]);
    expect(kycActions('unverified')).toEqual([]);
    expect(kycActions('rejected')).toEqual([]);
  });

  it('审核负载：驳回必须填写备注', () => {
    expect(toReviewPayload({ accountId: 3 }, 'rejected', '证件不清晰')).toEqual({
      accountId: 3,
      result: 'rejected',
      note: '证件不清晰',
    });
    expect(toReviewPayload({ accountId: 3 }, 'rejected', '   ')).toBeNull();
    expect(toReviewPayload({ accountId: 4 }, 'verified')).toEqual({
      accountId: 4,
      result: 'verified',
      note: undefined,
    });
  });
});