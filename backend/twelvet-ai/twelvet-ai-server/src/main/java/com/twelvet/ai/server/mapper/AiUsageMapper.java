package com.twelvet.ai.server.mapper;

import com.twelvet.ai.api.domain.AiUsageDaily;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * AI 用量日账单 Mapper（只读：Python 侧负责落账）
 */
@Mapper
public interface AiUsageMapper {

	/**
	 * 查询本租户近 N 天日账单（升序）
	 * @param tenantId 租户ID
	 * @param startDate 起始日期（含）
	 * @return 日账单列表
	 */
	List<AiUsageDaily> selectDaily(@Param("tenantId") Long tenantId, @Param("startDate") LocalDate startDate);

}
