package com.twelvet.runner.server.mapper;

import com.twelvet.runner.server.domain.RunnerNotifyLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 互助通知发送日志 Mapper
 *
 * @author twelvet
 */
@Mapper
public interface RunnerNotifyLogMapper {

	int insert(@Param("log") RunnerNotifyLog log, @Param("tenantId") Long tenantId);

}
