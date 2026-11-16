package com.twelvet.exchange.server.mapper;

import com.twelvet.exchange.server.domain.ExchangeOrderLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 交换订单流转日志 Mapper
 *
 * @author twelvet
 */
@Mapper
public interface ExchangeOrderLogMapper {

	int insert(@Param("log") ExchangeOrderLog log, @Param("tenantId") Long tenantId);

	List<ExchangeOrderLog> selectByOrderId(@Param("orderId") Long orderId, @Param("tenantId") Long tenantId);

}
