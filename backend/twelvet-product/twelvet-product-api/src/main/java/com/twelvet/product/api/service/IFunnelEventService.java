package com.twelvet.product.api.service;

import com.twelvet.product.api.domain.FunnelEvent;

import java.util.List;
import java.util.Map;

/**
 * 核心漏斗事件写入服务（spec §6.3）。
 * <p>
 * 所有方法必须尽力而为：实现内部吞掉持久化异常，绝不向调用方的业务事务传播。
 * </p>
 *
 * @author twelvet
 */
public interface IFunnelEventService {

	/**
	 * 记录单条事件。eventType 不在 10 类白名单内时静默忽略。
	 */
	void record(FunnelEvent event);

	/**
	 * 批量记录（曝光等一次请求产生 N 条的场景），只发一次批量 INSERT。
	 */
	void recordBatch(List<FunnelEvent> events);

	/**
	 * 社区漏斗看板数据。tenantId 为空时只返回租户选项，不返回任何聚合数据。
	 * @param days 统计天数，缺省 7，夹在 1..90
	 * @param tenantId 社区（租户）ID
	 * @return days/tenantId/tenants/summary/funnel/refundFunnel/daily
	 */
	Map<String, Object> adminStats(Integer days, Long tenantId);

}