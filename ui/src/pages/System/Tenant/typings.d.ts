declare namespace SystemTenant {
    /**
     * 续费/升级请求
     */
    type RenewParams = {
        planCode: string;
        months: number;
    };

    /**
     * 列表行 = 租户套餐详情 VO
     */
    type PageListItem = {
        tenantCode: string;
        tenantName: string;
        planCode: 'free' | 'basic' | 'pro';
        expireAt: string | null;
        quotaProductMax: number;   // 0 = 不限
        productCount: number;
        quotaAiCallDay: number;    // 0 = 不限（P2）
        aiCallToday: number;       // 今日已用 AI 调用（P2）
    };

    /**
     * 扫码续费下单响应（P2）
     */
    type RenewOrderResult = {
        payNo: string;
        qrCode: string;
        payUrl: string | null;
        mock: boolean;
        amount: number;
        planCode: string;
        months: number;
        tenantCode: string;
    };

    /**
     * 续费支付状态响应（P2）
     */
    type RenewStatusResult = {
        payNo: string;
        status: 'unpaid' | 'paid';
        applied: boolean;
        tenantCode: string;
        planCode: string;
        months: number;
        amount: number;
        mock: boolean;
    };

    /**
     * 套餐枚举（与后端 PlanCatalog 一致）
     */
    type PlanCode = 'free' | 'basic' | 'pro';
}
