package com.twelvet.trade.server.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 退款处理日志 Mapper
 *
 * @author twelvet
 */
public interface TradeRefundLogMapper {

	/**
	 * 写一条处理日志
	 * @param refundId 退款单ID
	 * @param operatorId 操作人ID(系统动作传 null)
	 * @param action 动作编码
	 * @param content 说明内容
	 * @return 结果
	 */
	int insert(@Param("refundId") Long refundId, @Param("operatorId") Long operatorId, @Param("action") String action,
			@Param("content") String content);

	/**
	 * 按退款单ID查时间线（正序）
	 * @param refundId 退款单ID
	 * @return 日志列表
	 */
	List<Map<String, Object>> selectByRefundId(@Param("refundId") Long refundId);

}
