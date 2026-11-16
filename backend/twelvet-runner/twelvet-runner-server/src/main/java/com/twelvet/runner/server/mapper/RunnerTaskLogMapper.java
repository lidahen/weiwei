package com.twelvet.runner.server.mapper;

import com.twelvet.runner.api.domain.RunnerTaskLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 互助任务流转日志 Mapper
 *
 * @author twelvet
 */
@Mapper
public interface RunnerTaskLogMapper {

	int insert(@Param("log") RunnerTaskLog log, @Param("tenantId") Long tenantId);

	List<RunnerTaskLog> selectByTaskId(@Param("taskId") Long taskId, @Param("tenantId") Long tenantId);

}
