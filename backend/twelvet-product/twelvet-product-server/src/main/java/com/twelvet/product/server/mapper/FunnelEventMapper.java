package com.twelvet.product.server.mapper;

import com.twelvet.product.api.domain.FunnelEvent;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 漏斗事件 Mapper。
 *
 * @author twelvet
 */
public interface FunnelEventMapper {

	/**
	 * 单条写入，event_key 冲突时静默跳过（幂等）。
	 */
	int insertIgnore(@Param("e") FunnelEvent event);

	/**
	 * 批量写入（曝光），event_key 为空的曝光行不受唯一键约束。
	 */
	int insertBatch(@Param("list") List<FunnelEvent> list);

	List<Map<String, Object>> selectDaily(@Param("days") int days, @Param("tenantId") Long tenantId);

	List<Map<String, Object>> selectSummary(@Param("days") int days, @Param("tenantId") Long tenantId);

	/**
	 * 有事件数据的社区选项（近 days 天）。
	 */
	List<Map<String, Object>> selectTenants(@Param("days") int days);

}