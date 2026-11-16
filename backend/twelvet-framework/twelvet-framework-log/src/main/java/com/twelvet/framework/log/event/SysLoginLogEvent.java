package com.twelvet.framework.log.event;

import com.twelvet.system.api.domain.SysLoginInfo;
import org.springframework.context.ApplicationEvent;

/**
 * @author twelvet
 * @Description: 系统登录日志事件
 */
public class SysLoginLogEvent extends ApplicationEvent {

	public SysLoginLogEvent(SysLoginInfo source) {
		super(source);
	}

}
