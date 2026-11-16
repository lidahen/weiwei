package com.twelvet.runner.server.mapper;

import com.twelvet.runner.api.domain.RunnerOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 互助接单记录 Mapper
 *
 * @author twelvet
 */
@Mapper
public interface RunnerOrderMapper {

	int insert(@Param("order") RunnerOrder order, @Param("tenantId") Long tenantId);

	List<RunnerOrder> selectByTaskId(@Param("taskId") Long taskId, @Param("tenantId") Long tenantId);

	List<RunnerOrder> selectByRunnerId(@Param("runnerId") Long runnerId, @Param("tenantId") Long tenantId,
			@Param("status") String status);

}
