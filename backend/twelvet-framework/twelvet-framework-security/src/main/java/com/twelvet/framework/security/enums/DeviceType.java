package com.twelvet.framework.security.enums;

/**
 * @author twelvet
 * @Description: 系统登录设备类型
 */
public enum DeviceType {

	/**
	 * pc端
	 */
	PC("pc"),

	/**
	 * app端
	 */
	APP("app");

	private final String device;

	DeviceType(String device) {
		this.device = device;
	}

}
