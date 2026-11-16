package com.twelvet.exchange.server.mapper;

import com.twelvet.exchange.server.domain.ExchangePickupCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 换物面交码 Mapper（twelve_exchange_pickup_code）
 *
 * @author twelvet
 */
@Mapper
public interface ExchangePickupCodeMapper {

	int insert(@Param("code") ExchangePickupCode pickupCode, @Param("tenantId") Long tenantId);

	List<ExchangePickupCode> selectByOrderId(@Param("orderId") Long orderId, @Param("tenantId") Long tenantId);

}
