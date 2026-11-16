package com.twelvet.system.server.service.impl;

import com.twelvet.system.api.domain.SysClientDetails;
import com.twelvet.system.server.mapper.SysOauthClientDetailsMapper;
import com.twelvet.system.server.service.ISysOauthClientDetailsService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author twelvet
 * @Description: OAuth2终端配置 业务层
 */
@Service
public class SysOauthClientDetailsServiceImpl implements ISysOauthClientDetailsService {

	private final SysOauthClientDetailsMapper sysOauthClientDetailsMapper;

	public SysOauthClientDetailsServiceImpl(SysOauthClientDetailsMapper sysOauthClientDetailsMapper) {
		this.sysOauthClientDetailsMapper = sysOauthClientDetailsMapper;
	}

	/**
	 * 根据条件分页查询终端配置
	 * @param sysClientDetails 终端配置信息
	 * @return 终端配置集合信息
	 */
	@Override
	public List<SysClientDetails> selectSysClientDetailsList(SysClientDetails sysClientDetails) {
		return sysOauthClientDetailsMapper.selectSysClientDetailsList(sysClientDetails);
	}

	/**
	 * 根据终端编号查询信息
	 * @param clientId 终端编号
	 * @return 终端配置
	 */
	@Override
	public SysClientDetails selectSysClientDetailsById(String clientId) {
		return sysOauthClientDetailsMapper.selectSysClientDetailsById(clientId);
	}

	/**
	 * 批量删除终端配置
	 * @param clientIds 需要删除的终端编号
	 * @return 结果
	 */
	@Override
	public int deleteSysClientDetailsByIds(String[] clientIds) {
		return sysOauthClientDetailsMapper.deleteSysClientDetailsByIds(clientIds);
	}

	/**
	 * 新增终端配置
	 * @param sysClientDetails 终端配置信息
	 * @return 结果
	 */
	@Override
	public int insertSysClientDetails(SysClientDetails sysClientDetails) {
		return sysOauthClientDetailsMapper.insertSysClientDetails(sysClientDetails);
	}

	/**
	 * 修改终端配置
	 * @param sysClientDetails 终端配置信息
	 * @return 结果
	 */
	@Override
	public int updateSysClientDetails(SysClientDetails sysClientDetails) {
		return sysOauthClientDetailsMapper.updateSysClientDetails(sysClientDetails);
	}

}
