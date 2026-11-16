package com.twelvet.payment.server.service.impl;

import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.payment.api.service.IPayOrderService;
import com.twelvet.payment.api.service.IPaymentGateway;
import com.twelvet.payment.api.service.ITenantRenewPayService;
import com.twelvet.payment.server.domain.TenantRenewOrder;
import com.twelvet.payment.server.mapper.PayOrderMapper;
import com.twelvet.payment.server.mapper.TenantRenewOrderMapper;
import com.twelvet.system.api.domain.SysTenant;
import com.twelvet.system.api.mapper.SysTenantMapper;
import com.twelvet.system.api.plan.Plan;
import com.twelvet.system.api.plan.PlanCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 租户套餐扫码续费支付实现
 * <p>
 * 重构后从 twelvet-trade 迁移到 twelvet-payment。 写 trade_pay 表统一走 PayOrderMapper，与商品支付单一致风格。
 * </p>
 *
 * @author twelvet
 */
@Service
public class TenantRenewPayServiceImpl implements ITenantRenewPayService {

	private static final Logger log = LoggerFactory.getLogger(TenantRenewPayServiceImpl.class);

	private final PayOrderMapper payOrderMapper;

	private final Map<String, IPaymentGateway> gatewayMap;

	private final TenantRenewOrderMapper renewOrderMapper;

	private final SysTenantMapper sysTenantMapper;

	private final IPayOrderService payOrderService;

	@Value("${billing.pay.mock:false}")
	private boolean mockEnabled;

	public TenantRenewPayServiceImpl(PayOrderMapper payOrderMapper, Map<String, IPaymentGateway> gatewayMap,
			TenantRenewOrderMapper renewOrderMapper, SysTenantMapper sysTenantMapper,
			IPayOrderService payOrderService) {
		this.payOrderMapper = payOrderMapper;
		this.gatewayMap = gatewayMap;
		this.renewOrderMapper = renewOrderMapper;
		this.sysTenantMapper = sysTenantMapper;
		this.payOrderService = payOrderService;
	}

	@Override
	public Map<String, Object> createOrder(String tenantCode, String planCode, int months, String channel) {
		SysTenant tenant = sysTenantMapper.selectByCode(tenantCode);
		if (tenant == null) {
			throw new TWTException("租户不存在: " + tenantCode);
		}
		Plan plan = PlanCatalog.get(planCode);
		if (plan == null) {
			throw new TWTException("未知套餐: " + planCode);
		}
		if (months <= 0) {
			throw new TWTException("续费月数必须 > 0");
		}
		if (plan.monthlyPrice().compareTo(BigDecimal.ZERO) <= 0) {
			throw new TWTException("免费版无需支付，请使用直接续费");
		}
		IPaymentGateway gateway = gatewayMap.get(channel);
		if (gateway == null) {
			throw new TWTException("不支持的支付渠道: " + channel);
		}

		String outTradeNo = payOrderService.generatePayNo();
		BigDecimal amount = plan.monthlyPrice().multiply(BigDecimal.valueOf(months));
		String subject = "weiwei租户续费-" + plan.name() + " x" + months + "月";
		boolean mock = mockEnabled;

		String qrCode;
		String payUrl = null;
		if (mock) {
			qrCode = "https://mock.pay/weiwei/" + outTradeNo;
		}
		else {
			IPaymentGateway.PaymentResult result = gateway.createPayRequest(outTradeNo, subject, amount, "");
			qrCode = result.qrCode();
			payUrl = result.payUrl();
		}

		// 写 trade_pay（order_id=NULL 标识为非商品订单的续费支付单）
		payOrderMapper.insertRenewPayOrder(outTradeNo, channel, amount, qrCode, tenant.getId());

		// 写 sys_tenant_renew_order（履约凭据）
		TenantRenewOrder order = new TenantRenewOrder();
		order.setTenantCode(tenantCode);
		order.setPlanCode(planCode);
		order.setMonths(months);
		order.setAmount(amount);
		order.setOutTradeNo(outTradeNo);
		order.setChannel(channel);
		renewOrderMapper.insert(order);

		Map<String, Object> resp = new HashMap<>();
		resp.put("payNo", outTradeNo);
		resp.put("qrCode", qrCode);
		resp.put("payUrl", payUrl);
		resp.put("mock", mock);
		resp.put("amount", amount);
		resp.put("planCode", planCode);
		resp.put("months", months);
		resp.put("tenantCode", tenantCode);
		return resp;
	}

	@Override
	public Map<String, Object> status(String payNo) {
		TenantRenewOrder order = requireOrder(payNo);
		if ("unpaid".equals(order.getStatus()) && !mockEnabled) {
			IPaymentGateway gateway = gatewayMap.get(order.getChannel());
			if (gateway != null && "SUCCESS".equals(gateway.queryPayStatus(payNo))) {
				fulfill(payNo);
				order = requireOrder(payNo);
			}
		}
		Map<String, Object> resp = new HashMap<>();
		resp.put("payNo", payNo);
		resp.put("status", order.getStatus());
		resp.put("applied", "paid".equals(order.getStatus()));
		resp.put("tenantCode", order.getTenantCode());
		resp.put("planCode", order.getPlanCode());
		resp.put("months", order.getMonths());
		resp.put("amount", order.getAmount());
		resp.put("mock", mockEnabled);
		return resp;
	}

	@Override
	public void mockPay(String payNo) {
		if (!mockEnabled) {
			throw new TWTException("模拟支付未开启（billing.pay.mock=false）");
		}
		requireOrder(payNo);
		fulfill(payNo);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean fulfill(String outTradeNo) {
		TenantRenewOrder order = requireOrder(outTradeNo);

		// 1. 标 trade_pay 已付（带幂等）
		payOrderMapper.markRenewPaid(outTradeNo);

		// 2. 抢占式标续费单已付；返回 0 = 已被处理
		if (renewOrderMapper.markPaid(outTradeNo) == 0) {
			log.info("续费单已履约过，跳过: {}", outTradeNo);
			return false;
		}

		// 3. 落库套餐
		SysTenant tenant = sysTenantMapper.selectByCode(order.getTenantCode());
		if (tenant == null) {
			throw new TWTException("租户不存在: " + order.getTenantCode());
		}
		Plan plan = PlanCatalog.get(order.getPlanCode());
		if (plan == null) {
			throw new TWTException("未知套餐: " + order.getPlanCode());
		}
		LocalDateTime base = (tenant.getExpireAt() != null && tenant.getExpireAt().isAfter(LocalDateTime.now()))
				? tenant.getExpireAt() : LocalDateTime.now();
		tenant.setPlanCode(plan.code());
		tenant.setExpireAt(base.plusMonths(order.getMonths()));
		tenant.setQuotaProductMax(plan.quotaProductMax());
		tenant.setQuotaAiCallDay(plan.quotaAiCallDay());
		sysTenantMapper.updatePlan(tenant);
		log.info("续费履约完成: tenant={}, plan={} x{}月, expire={}", order.getTenantCode(), plan.code(), order.getMonths(),
				tenant.getExpireAt());
		return true;
	}

	private TenantRenewOrder requireOrder(String outTradeNo) {
		TenantRenewOrder order = renewOrderMapper.selectByOutTradeNo(outTradeNo);
		if (order == null) {
			throw new TWTException("续费支付单不存在: " + outTradeNo);
		}
		return order;
	}

}
