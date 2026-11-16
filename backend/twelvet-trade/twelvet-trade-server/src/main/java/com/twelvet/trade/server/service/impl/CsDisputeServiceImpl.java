package com.twelvet.trade.server.service.impl;

import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.framework.jdbc.web.utils.PageUtils;
import com.twelvet.trade.server.mapper.CsDisputeMapper;
import com.twelvet.trade.server.service.ICsDisputeService;
import com.twelvet.trade.server.service.ITradeRefundService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 客服纠纷Service业务层处理
 *
 * @author twelvet
 */
@Service
public class CsDisputeServiceImpl implements ICsDisputeService {

	private static final Logger log = LoggerFactory.getLogger(CsDisputeServiceImpl.class);

	/**
	 * 会联动退款的裁决结论（沿用 trade_dispute 的枚举）
	 */
	private static final Set<String> REFUND_DECISIONS = Set.of("buyer", "seller", "partial");

	private final CsDisputeMapper disputeMapper;

	private final ITradeRefundService refundService;

	public CsDisputeServiceImpl(CsDisputeMapper disputeMapper, ITradeRefundService refundService) {
		this.disputeMapper = disputeMapper;
		this.refundService = refundService;
	}

	/**
	 * 查询纠纷工单列表
	 */
	@Override
	public TableDataInfo<Map<String, Object>> selectDisputeList(String status) {
		// 分页：PageUtils 读取请求参数 current / pageSize
		PageUtils.startPage();
		List<Map<String, Object>> list = disputeMapper.selectDisputeList(status);
		return PageUtils.getDataTable(list);
	}

	/**
	 * 查询纠纷统计
	 */
	@Override
	public Map<String, Object> selectDisputeStats() {
		List<Map<String, Object>> rows = disputeMapper.selectDisputeStats();
		Map<String, Object> result = new HashMap<>();
		if (!rows.isEmpty()) {
			Map<String, Object> row = rows.get(0);
			result.put("pending", row.get("pending"));
			result.put("inProgress", row.get("inProgress"));
			result.put("closed", row.get("closed"));
		}
		return result;
	}

	/**
	 * 查询纠纷详情
	 */
	@Override
	public Map<String, Object> selectDisputeDetail(Long disputeId) {
		Map<String, Object> detail = disputeMapper.selectDisputeDetail(disputeId);
		if (detail == null) {
			throw new TWTException("纠纷工单不存在");
		}
		return detail;
	}

	/**
	 * 客服领取工单
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public String claimDispute(Long disputeId, Long csId) {
		int rows = disputeMapper.claimDispute(disputeId, csId);
		if (rows == 0) {
			log.warn("领取工单失败: dispute={}, cs={}", disputeId, csId);
		}
		return "OK";
	}

	/**
	 * 提交裁决
	 * <p>
	 * 裁决通过时联动退款：不依赖 trade_dispute.refund_id（该列全仓零写入点）， 按 order_id 反查 mediation
	 * 状态的退款单并执行（找不到属正常，不阻断裁决）。 已关闭的纠纷不可重复裁决：UPDATE 0 行必须抛，不得静默返回 OK。
	 * </p>
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public String judgeDispute(Long disputeId, String decision, BigDecimal amount, String reason, Long csId) {
		Map<String, Object> detail = disputeMapper.selectDisputeDetail(disputeId);
		if (detail == null) {
			throw new TWTException("纠纷工单不存在");
		}
		int rows = disputeMapper.judgeDispute(disputeId, decision, amount, reason, csId);
		if (rows == 0) {
			throw new TWTException("纠纷工单已关闭");
		}
		Long orderId = toLong(detail.get("orderId"));
		if (orderId == null) {
			orderId = toLong(detail.get("order_id"));
		}
		if (orderId != null && REFUND_DECISIONS.contains(decision)) {
			// 找不到 mediation 退款单返回 false（可能是不走退款流程的纠纷），忽略返回值
			refundService.judgeByOrder(orderId, decision, amount, reason, csId);
		}
		log.info("纠纷裁决完成: dispute={}, decision={}, order={}", disputeId, decision, orderId);
		return "OK";
	}

	private Long toLong(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof Number) {
			return ((Number) value).longValue();
		}
		try {
			return Long.valueOf(String.valueOf(value));
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

}
