package com.twelvet.system.server.service;

import com.twelvet.system.api.domain.SysClientDetails;

import java.util.List;

/**
 * @author twelvet
 * @Description: OAuth2终端配置 业务层
 */
public interface ISysOauthClientDetailsService {

	/**
	 * 根据条件分页查询终端配置
	 * @param sysClientDetails 终端配置信息
	 * @return 终端配置集合信息
	 */
	List<SysClientDetails> selectSysClientDetailsList(SysClientDetails sysClientDetails);

	/**
	 * 根据终端编号查询信息
	 * @param clientId 终端编号
	 * @return 终端配置
	 */
	SysClientDetails selectSysClientDetailsById(String clientId);

	/**
	 * 批量删除终端配置
	 * @param clientIds 需要删除的终端编号
	 * @return 结果
	 */
	int deleteSysClientDetailsByIds(String[] clientIds);

	/**
	 * 新增终端配置
	 * @param sysClientDetails 终端配置信息
	 * @return 结果
	 */
	int insertSysClientDetails(SysClientDetails sysClientDetails);

	/**
	 * 修改终端配置
	 * @param sysClientDetails 终端配置信息
	 * @return 结果
	 */
	int updateSysClientDetails(SysClientDetails sysClientDetails);

}
