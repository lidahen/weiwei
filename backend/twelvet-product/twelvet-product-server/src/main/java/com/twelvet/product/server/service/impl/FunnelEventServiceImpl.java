package com.twelvet.product.server.service.impl;

import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.product.api.domain.FunnelEvent;
import com.twelvet.product.api.domain.Product;
import com.twelvet.product.api.service.IFunnelEventService;
import com.twelvet.product.server.mapper.FunnelEventMapper;
import com.twelvet.product.server.mapper.ProductMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 漏斗事件写入服务。
 * <p>
 * 设计约束（spec §6.3）：事件全部服务端记录；写库失败只记 warn、绝不向业务事务传播； 租户优先取业务对象（回调/Quartz 无上下文），类目在写入前统一补齐。
 * </p>
 *
 * @author twelvet
 */
@Service
public class FunnelEventServiceImpl implements IFunnelEventService {

	private static final Logger log = LoggerFactory.getLogger(FunnelEventServiceImpl.class);

	private static final Set<String> ALLOWED_TYPES = Set.of(FunnelEvent.TYPE_PRODUCT_EXPOSURE,
			FunnelEvent.TYPE_PRODUCT_DETAIL_VIEW, FunnelEvent.TYPE_IM_START, FunnelEvent.TYPE_ORDER_CREATE,
			FunnelEvent.TYPE_PAY_SUCCESS, FunnelEvent.TYPE_SHIP, FunnelEvent.TYPE_CONFIRM_RECEIPT,
			FunnelEvent.TYPE_REFUND_APPLY, FunnelEvent.TYPE_REFUND_SUCCESS, FunnelEvent.TYPE_REVIEW_CREATE);

	/** 主漏斗固定顺序（spec §6.3） */
	private static final List<String> FUNNEL_STEPS = List.of(FunnelEvent.TYPE_PRODUCT_EXPOSURE,
			FunnelEvent.TYPE_PRODUCT_DETAIL_VIEW, FunnelEvent.TYPE_IM_START, FunnelEvent.TYPE_ORDER_CREATE,
			FunnelEvent.TYPE_PAY_SUCCESS, FunnelEvent.TYPE_CONFIRM_RECEIPT);

	private static final List<String> REFUND_STEPS = List.of(FunnelEvent.TYPE_REFUND_APPLY,
			FunnelEvent.TYPE_REFUND_SUCCESS);

	private final FunnelEventMapper eventMapper;

	private final ProductMapper productMapper;

	public FunnelEventServiceImpl(FunnelEventMapper eventMapper, ProductMapper productMapper) {
		this.eventMapper = eventMapper;
		this.productMapper = productMapper;
	}

	@Override
	public void record(FunnelEvent event) {
		if (!isAllowed(event)) {
			return;
		}
		try {
			enrich(event);
			eventMapper.insertIgnore(event);
		}
		catch (Exception e) {
			log.warn("[漏斗] 事件写入失败 type={} key={}: {}", event.getEventType(), event.getEventKey(), e.getMessage());
		}
	}

	@Override
	public void recordBatch(List<FunnelEvent> events) {
		if (events == null || events.isEmpty()) {
			return;
		}
		try {
			List<FunnelEvent> valid = new ArrayList<>(events.size());
			for (FunnelEvent event : events) {
				if (isAllowed(event)) {
					enrich(event);
					valid.add(event);
				}
			}
			if (!valid.isEmpty()) {
				eventMapper.insertBatch(valid);
			}
		}
		catch (Exception e) {
			log.warn("[漏斗] 批量事件写入失败 size={}: {}", events.size(), e.getMessage());
		}
	}

	@Override
	public Map<String, Object> adminStats(Integer days, Long tenantId) {
		int window = days == null ? 7 : Math.max(1, Math.min(90, days));
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("days", window);
		result.put("tenantId", tenantId);
		result.put("tenants", eventMapper.selectTenants(window));
		if (tenantId == null) {
			// D7/D8：看板必须按租户切片，空租户只给选项，绝不给跨租户聚合
			result.put("summary", Map.of());
			result.put("funnel", List.of());
			result.put("refundFunnel", List.of());
			result.put("daily", List.of());
			return result;
		}
		Map<String, Map<String, Object>> summary = new LinkedHashMap<>();
		for (Map<String, Object> row : eventMapper.selectSummary(window, tenantId)) {
			summary.put(String.valueOf(row.get("eventType")), row);
		}
		result.put("summary", summary);
		result.put("funnel", buildFunnel(FUNNEL_STEPS, summary));
		result.put("refundFunnel", buildFunnel(REFUND_STEPS, summary));
		result.put("daily", eventMapper.selectDaily(window, tenantId));
		return result;
	}

	/**
	 * 按固定顺序组装漏斗：conversion = 本环节 / 上一环节；上一环节缺失或 0 时为 null。
	 */
	private List<Map<String, Object>> buildFunnel(List<String> steps, Map<String, Map<String, Object>> summary) {
		List<Map<String, Object>> rows = new ArrayList<>(steps.size());
		Long prevCount = null;
		for (String step : steps) {
			long count = countOf(summary.get(step), "count");
			long uv = countOf(summary.get(step), "uv");
			Double conversion = null;
			if (prevCount != null && prevCount > 0) {
				conversion = (double) count / prevCount;
			}
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("key", step);
			row.put("count", count);
			row.put("uv", uv);
			row.put("conversion", conversion);
			rows.add(row);
			prevCount = count;
		}
		return rows;
	}

	private long countOf(Map<String, Object> row, String key) {
		if (row == null) {
			return 0L;
		}
		Object value = row.get(key);
		return value instanceof Number number ? number.longValue() : 0L;
	}

	private boolean isAllowed(FunnelEvent event) {
		return event != null && event.getEventType() != null && ALLOWED_TYPES.contains(event.getEventType());
	}

	/**
	 * 补租户、类目与 aiAssisted 默认值。类目查询单独兜底：商品读失败不应丢掉整条事件。
	 */
	private void enrich(FunnelEvent event) {
		if (event.getTenantId() == null) {
			event.setTenantId(TenantContext.get());
		}
		if (event.getAiAssisted() == null) {
			event.setAiAssisted(Boolean.FALSE);
		}
		if (event.getCategory() == null && event.getProductId() != null) {
			try {
				Product product = productMapper.selectById(event.getProductId(), event.getTenantId());
				if (product != null) {
					event.setCategory(product.getCatL1());
				}
			}
			catch (Exception e) {
				log.warn("[漏斗] 类目补齐失败 productId={}: {}", event.getProductId(), e.getMessage());
			}
		}
	}

}