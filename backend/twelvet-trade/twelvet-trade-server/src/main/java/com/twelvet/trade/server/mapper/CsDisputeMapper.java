package com.twelvet.trade.server.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 纠纷工单Mapper
 *
 * @author twelvet
 */
@Mapper
public interface CsDisputeMapper {

	/**
	 * 查询纠纷工单列表
	 * @param status 状态
	 * @return 工单列表
	 */
	List<Map<String, Object>> selectDisputeList(@Param("status") String status);

	/**
	 * 查询纠纷工单详情
	 * @param disputeId 纠纷ID
	 * @return 工单详情
	 */
	Map<String, Object> selectDisputeDetail(@Param("disputeId") Long disputeId);

	/**
	 * 查询纠纷统计
	 * @return 统计Map
	 */
	List<Map<String, Object>> selectDisputeStats();

	/**
	 * 客服领取工单
	 * @param disputeId 纠纷ID
	 * @param csId 客服ID
	 * @return 结果
	 */
	int claimDispute(@Param("disputeId") Long disputeId, @Param("csId") Long csId);

	/**
	 * 提交裁决
	 * @param disputeId 纠纷ID
	 * @param decision 裁决决定
	 * @param amount 退款金额
	 * @param reason 裁决原因
	 * @param csId 客服ID
	 * @return 结果
	 */
	int judgeDispute(@Param("disputeId") Long disputeId, @Param("decision") String decision,
			@Param("amount") java.math.BigDecimal amount, @Param("reason") String reason, @Param("csId") Long csId);

}
