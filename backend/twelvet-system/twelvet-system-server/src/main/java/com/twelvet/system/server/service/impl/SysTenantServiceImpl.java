package com.twelvet.system.server.service.impl;

import com.twelvet.framework.core.constants.BillingKeys;
import com.twelvet.framework.RedisUtils;
import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.system.api.domain.SysTenant;
import com.twelvet.system.api.domain.vo.SysTenantPlanVO;
import com.twelvet.system.api.plan.Plan;
import com.twelvet.system.api.plan.PlanCatalog;
import com.twelvet.system.api.mapper.SysTenantMapper;
import com.twelvet.system.server.service.ISysTenantService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 租户服务实现（商业化 P0）
 *
 * <p>
 * 续费规则：
 * </p>
 * <ul>
 * <li>按 planCode 取 PlanCatalog 拿 quota_product_max / quota_ai_call_day</li>
 * <li>若当前 expire_at 在未来 → 在其基础上 + 月数；否则按 now() 算</li>
 * <li>免费版（quota=100）→ 基础版（1000）→ Pro（不限）</li>
 * </ul>
 */
@Service
public class SysTenantServiceImpl implements ISysTenantService {

	private final SysTenantMapper sysTenantMapper;

	public SysTenantServiceImpl(SysTenantMapper sysTenantMapper) {
		this.sysTenantMapper = sysTenantMapper;
	}

	@Override
	public SysTenantPlanVO getPlanInfo(String tenantCode) {
		SysTenant t = sysTenantMapper.selectByCode(tenantCode);
		if (t == null) {
			throw new TWTException("租户不存在: " + tenantCode);
		}
		SysTenantPlanVO vo = new SysTenantPlanVO();
		vo.setTenantCode(t.getTenantCode());
		vo.setTenantName(t.getTenantName());
		vo.setPlanCode(t.getPlanCode());
		vo.setExpireAt(t.getExpireAt());
		vo.setQuotaProductMax(t.getQuotaProductMax());
		vo.setProductCount(sysTenantMapper.countProducts(t.getId()));
		vo.setQuotaAiCallDay(t.getQuotaAiCallDay());
		Object today = RedisUtils.getCacheObject(BillingKeys.aiDayKey(t.getId()));
		vo.setAiCallToday(today instanceof Number n ? n.longValue() : 0L);
		return vo;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void renew(String tenantCode, String planCode, int months) {
		if (months <= 0) {
			throw new TWTException("续费月数必须 > 0");
		}
		Plan plan = PlanCatalog.get(planCode);
		if (plan == null) {
			throw new TWTException("未知套餐: " + planCode);
		}
		SysTenant t = sysTenantMapper.selectByCode(tenantCode);
		if (t == null) {
			throw new TWTException("租户不存在: " + tenantCode);
		}
		// 续费基线：当前未过期则在其上累加，否则从 now() 开始
		LocalDateTime base = (t.getExpireAt() != null && t.getExpireAt().isAfter(LocalDateTime.now())) ? t.getExpireAt()
				: LocalDateTime.now();
		t.setPlanCode(plan.code());
		t.setExpireAt(base.plusMonths(months));
		t.setQuotaProductMax(plan.quotaProductMax());
		t.setQuotaAiCallDay(plan.quotaAiCallDay());
		sysTenantMapper.updatePlan(t);
	}

}
