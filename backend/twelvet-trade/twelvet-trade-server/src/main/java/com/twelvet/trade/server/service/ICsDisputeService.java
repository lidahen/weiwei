package com.twelvet.trade.server.service;

import com.twelvet.framework.jdbc.web.page.TableDataInfo;

import java.util.List;
import java.util.Map;

/**
 * 客服纠纷Service接口
 *
 * @author twelvet
 */
public interface ICsDisputeService {

	/**
	 * 查询纠纷工单列表(分页)
	 * @param status 状态
	 * @return 工单分页列表
	 */
	TableDataInfo<Map<String, Object>> selectDisputeList(String status);

	/**
	 * 查询纠纷统计
	 * @return 统计Map
	 */
	Map<String, Object> selectDisputeStats();

	/**
	 * 查询纠纷详情
	 * @param disputeId 纠纷ID
	 * @return 工单详情
	 */
	Map<String, Object> selectDisputeDetail(Long disputeId);

	/**
	 * 客服领取工单
	 * @param disputeId 纠纷ID
	 * @param csId 客服ID
	 * @return 结果
	 */
	String claimDispute(Long disputeId, Long csId);

	/**
	 * 提交裁决
	 * @param disputeId 纠纷ID
	 * @param decision 裁决决定
	 * @param amount 退款金额
	 * @param reason 裁决原因
	 * @param csId 客服ID
	 * @return 结果
	 */
	String judgeDispute(Long disputeId, String decision, java.math.BigDecimal amount, String reason, Long csId);

}
