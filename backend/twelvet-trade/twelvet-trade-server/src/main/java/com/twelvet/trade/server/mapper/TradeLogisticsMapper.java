package com.twelvet.trade.server.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 物流Mapper
 *
 * @author twelvet
 */
@Mapper
public interface TradeLogisticsMapper {

	/**
	 * 查询订单物流信息
	 * @param orderId 订单ID
	 * @param tenantId 租户ID(为 null 不过滤)
	 * @return 物流信息
	 */
	Map<String, Object> selectLogisticsByOrderId(@Param("orderId") Long orderId, @Param("tenantId") Long tenantId);

	/**
	 * 批量查询订单物流信息（每个订单取最新一条）
	 * @param orderIds 订单ID列表
	 * @param tenantId 租户ID(为 null 不过滤)
	 * @return 物流信息列表
	 */
	List<Map<String, Object>> selectLogisticsByOrderIds(@Param("orderIds") List<Long> orderIds,
			@Param("tenantId") Long tenantId);

	/**
	 * 新增物流信息
	 * @param orderId 订单ID
	 * @param company 物流公司
	 * @param no 物流单号
	 * @param latestNode 最新节点
	 * @param traceJson 物流轨迹
	 * @param tenantId 租户ID(为 null 落 default)
	 * @return 结果
	 */
	int insertLogistics(@Param("orderId") Long orderId, @Param("company") String company, @Param("no") String no,
			@Param("latestNode") String latestNode, @Param("traceJson") String traceJson,
			@Param("tenantId") Long tenantId);

}
