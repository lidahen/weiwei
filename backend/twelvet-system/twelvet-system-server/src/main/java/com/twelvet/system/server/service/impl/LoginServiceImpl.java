package com.twelvet.system.server.service.impl;

import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.stp.StpUtil;
import com.twelvet.framework.RedisUtils;
import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.framework.security.constants.SecurityConstants;
import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.framework.log.event.SysLoginLogEvent;
import com.twelvet.framework.security.domain.LoginUser;
import com.twelvet.framework.security.enums.DeviceType;
import com.twelvet.framework.security.utils.SecurityUtils;
import com.twelvet.framework.utils.DateUtils;
import com.twelvet.framework.utils.IdUtils;
import com.twelvet.framework.utils.SpringContextHolder;
import com.twelvet.framework.utils.TUtils;
import com.twelvet.framework.utils.http.IpUtils;
import com.twelvet.system.api.domain.SysLoginInfo;
import com.twelvet.system.api.domain.SysUser;
import com.twelvet.system.api.domain.dto.LoginDTO;
import com.twelvet.system.api.domain.vo.LoginVO;
import com.twelvet.system.server.mapper.SysUserMapper;
import com.twelvet.system.server.service.ISmsCodeService;
import com.twelvet.system.server.service.ISysPermissionService;
import com.twelvet.system.server.service.ISysUserService;
import com.twelvet.system.server.service.ILoginService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @author twelvet
 * @Description: 登录服务
 */
@Service
public class LoginServiceImpl implements ILoginService {

	/** 自动建号默认角色：普通用户（C 端菜单由 docs/db 基线数据授予） */
	private static final Long DEFAULT_ROLE_ID = 2L;

	/** C 端用户类型：仅该类型允许手机号验证码登录（00运营 / 01客服 保持账号密码） */
	private static final String C_END_USER_TYPE = "02";

	private final SysUserMapper userMapper;

	private final ISysPermissionService iSysPermissionService;

	private final ISmsCodeService iSmsCodeService;

	private final ISysUserService iSysUserService;

	public LoginServiceImpl(SysUserMapper userMapper, ISysPermissionService iSysPermissionService,
			ISmsCodeService iSmsCodeService, ISysUserService iSysUserService) {
		this.userMapper = userMapper;
		this.iSysPermissionService = iSysPermissionService;
		this.iSmsCodeService = iSmsCodeService;
		this.iSysUserService = iSysUserService;
	}

	/**
	 * 登录
	 * @param loginDTO LoginDTO
	 */
	@Override
	public LoginVO login(LoginDTO loginDTO) {
		SysUser sysUser = loadUserByUsername(loginDTO.getUsername(), loginDTO.getPassword());

		// 构建用户信息
		LoginUser loginUser = buildLoginUser(sysUser);

		// 生成token
		return issueToken(loginUser);
	}

	/**
	 * 手机号验证码登录（首次登录自动建号）
	 * @param mobile 手机号
	 * @param code 验证码
	 * @return LoginVO
	 */
	@Override
	public LoginVO smsLogin(String mobile, String code) {
		// 验证码不通过直接抛出，失败日志由 SmsCodeService 体现
		iSmsCodeService.verify(mobile, code);

		// 租户级登录：与账号密码登录同语义，上下文来自 TenantFilter
		Long tenantId = TenantContext.get();
		SysUser sysUser = userMapper.selectUserByPhoneAndTenant(mobile, tenantId);
		if (Objects.isNull(sysUser)) {
			sysUser = createUserByMobile(mobile, tenantId);
		}

		// B 端账号（运营 00 / 客服 01）保持账号密码登录，不开放手机号验证码登录：
		// mock 固定码 + 已知手机号即可拿到超管会话，必须在此拦截。
		// 自动建号新建的恒为 '02'，不会走到该分支。
		if (!C_END_USER_TYPE.equals(sysUser.getUserType())) {
			throw new TWTException("该账号不支持手机号登录，请使用账号密码登录");
		}

		if ("1".equals(sysUser.getStatus())) {
			throw new TWTException("账号已被冻结");
		}

		LoginUser loginUser = buildLoginUser(sysUser);
		LoginVO loginVO = issueToken(loginUser);

		// 发送异步日志事件（校验通过之后才发布）
		SysLoginInfo sysLoginInfo = new SysLoginInfo();
		sysLoginInfo.setStatus(SecurityConstants.LOGIN_SUCCESS);
		sysLoginInfo.setUserName(sysUser.getUsername());
		sysLoginInfo.setDeptId(sysUser.getDeptId());
		sysLoginInfo.setIpaddr(IpUtils.getIpAddr());
		sysLoginInfo.setMsg("手机号登录成功");
		sysLoginInfo.setCreateTime(LocalDateTime.now());
		sysLoginInfo.setCreateBy(sysUser.getUsername());
		sysLoginInfo.setUpdateBy(sysUser.getUsername());
		SpringContextHolder.publishEvent(new SysLoginLogEvent(sysLoginInfo));

		return loginVO;
	}

	/**
	 * 手机号首次登录自动建号
	 * @param mobile 手机号
	 * @param tenantId 租户ID
	 * @return 建号后的用户（并发冲突时返回已存在的用户）
	 */
	private SysUser createUserByMobile(String mobile, Long tenantId) {
		SysUser sysUser = new SysUser();
		// username 必须带随机后缀，不能只用 "u"+mobile：
		// uk_sys_user_tenant_username 不含 del_flag（历史 tenant-round4 migration；当前基线已包含），
		// 而登录查询只认 del_flag='0'。确定性用户名会导致软删行永久占位：
		// 软删后再次登录 → 查不到 → 建号 → 撞同名唯一键 → 手机号被锁死。
		// C 端不输入 username，可预测性无业务价值，故加 6 位随机十六进制
		// （长度 1+11+1+6=19 ≤ varchar(30)）。
		sysUser.setUsername("u" + mobile + "_" + randomSuffix());
		sysUser.setNickName("邻里用户" + mobile.substring(7));
		sysUser.setPhonenumber(mobile);
		sysUser.setUserType("02");
		sysUser.setAvatar("");
		sysUser.setStatus("0");
		sysUser.setDelFlag("0");
		// 随机密码：用户不会用到，确保无法走账号密码登录；insertUser 不再二次加密
		sysUser.setPassword(BCrypt.hashpw(IdUtils.simpleUUID()));
		sysUser.setRoleIds(new Long[] { DEFAULT_ROLE_ID });
		if (tenantId != null) {
			// 无上下文时不设置，交给 TenantFieldInterceptor
			sysUser.setTenantId(tenantId);
		}

		try {
			iSysUserService.insertUser(sysUser);
		}
		catch (DuplicateKeyException e) {
			// 并发建号：另一请求已插入同一手机号，重查后直接登录。
			// 重查仍为空说明撞的是别的唯一键（或历史遗留的软删占位行），不能放任 NPE。
			// 注：自动建号的 username 已带随机后缀，正常不会再撞 uk_sys_user_tenant_username 的软删占位；
			// 此兜底保留，用于其他唯一键冲突与修复前已存在的历史数据。
			SysUser existed = userMapper.selectUserByPhoneAndTenant(mobile, tenantId);
			if (Objects.isNull(existed)) {
				throw new TWTException("账号状态异常，请联系客服");
			}
			return existed;
		}

		// 建号成功后回查：内存对象只有 roleIds，roles 需由 Mapper 的 collection 聚合出全量角色，
		// 否则 LoginUser.roles 为 null，数据权限切面遍历时 NPE。
		// 按新 user_id 回查（而非手机号）：无租户上下文时按手机号可能命中其他租户的同人身份
		SysUser created = Objects.nonNull(sysUser.getUserId()) ? userMapper.selectUserById(sysUser.getUserId()) : null;
		return Objects.nonNull(created) ? created : sysUser;
	}

	/**
	 * 用户名随机后缀：取 simpleUUID() 前 6 位十六进制，用于避开软删行对用户名的唯一键占位
	 * @return 6 位十六进制字符串
	 */
	private static String randomSuffix() {
		return IdUtils.simpleUUID().substring(0, 6);
	}

	/**
	 * 退出登录
	 */
	@Override
	public void logout() {
		Long deptId = SecurityUtils.getDeptId();
		String username = SecurityUtils.getUsername();

		// 发送异步日志事件
		SysLoginInfo sysLoginInfo = new SysLoginInfo();
		sysLoginInfo.setStatus(SecurityConstants.LOGIN_SUCCESS);
		sysLoginInfo.setUserName(username);
		sysLoginInfo.setDeptId(deptId);
		sysLoginInfo.setIpaddr(IpUtils.getIpAddr());
		sysLoginInfo.setMsg("退出登录");
		// 发送异步日志事件
		sysLoginInfo.setCreateTime(LocalDateTime.now());
		sysLoginInfo.setCreateBy(username);
		sysLoginInfo.setUpdateBy(username);
		SpringContextHolder.publishEvent(new SysLoginLogEvent(sysLoginInfo));

		StpUtil.logout();
	}

	/**
	 * 刷新令牌
	 * @param refreshToken 刷新令牌
	 * @return LoginVO
	 */
	@Override
	public LoginVO refreshToken(String refreshToken) {
		// 校验刷新令牌
		Long userId = RedisUtils.getCacheObject(SecurityConstants.REFRESH_TOKEN_KEY + refreshToken);
		if (userId == null) {
			throw new TWTException("刷新令牌无效或已过期");
		}
		// 刷新后旧令牌立即作废
		RedisUtils.deleteObject(SecurityConstants.REFRESH_TOKEN_KEY + refreshToken);

		SysUser sysUser = userMapper.selectUserById(userId);
		if (sysUser == null) {
			throw new TWTException("登录用户不存在");
		}

		// 重新构建用户信息并签发新令牌
		LoginUser loginUser = buildLoginUser(sysUser);
		return issueToken(loginUser);
	}

	/**
	 * 签发令牌：登录与手机号登录共用，返回值结构与 /auth/oauth2/token 一致
	 * @param loginUser 登录用户信息
	 * @return LoginVO
	 */
	private LoginVO issueToken(LoginUser loginUser) {
		SecurityUtils.loginByDevice(loginUser, DeviceType.PC);

		LoginVO loginVO = new LoginVO();
		loginVO.setAccessToken(StpUtil.getTokenValue());

		// 生成刷新令牌(与用户ID绑定存Redis)，有效期与access_token一致
		String refreshToken = IdUtils.simpleUUID();
		RedisUtils.setCacheObject(SecurityConstants.REFRESH_TOKEN_KEY + refreshToken, loginUser.getUserId(),
				StpUtil.getTokenTimeout(), TimeUnit.SECONDS);
		loginVO.setRefreshToken(refreshToken);

		// 令牌有效期(秒)，用于前端定时续签
		loginVO.setExpiresIn(StpUtil.getTokenTimeout());

		return loginVO;
	}

	/**
	 * 通过账号名称登录
	 * @param username 账号名称
	 * @param password 账号密码
	 * @return
	 */
	private SysUser loadUserByUsername(String username, String password) {
		// 租户级登录：上下文来自 TenantFilter（子域名/X-Tenant-Code），同名账号各租户独立；
		// 无上下文走全局查找（兼容 curl 无 header 与回滚）
		Long tenantId = TenantContext.get();
		SysUser sysUser = tenantId != null ? userMapper.selectUserByUserNameAndTenant(username, tenantId)
				: userMapper.selectUserByUserName(username);

		if (Objects.isNull(sysUser)) {
			throw new TWTException("登录用户：" + username + " 不存在.");
		}

		// 发送异步日志事件
		Long deptId = sysUser.getDeptId();
		SysLoginInfo sysLoginInfo = new SysLoginInfo();
		sysLoginInfo.setStatus(SecurityConstants.LOGIN_SUCCESS);
		sysLoginInfo.setUserName(username);
		sysLoginInfo.setDeptId(deptId);
		sysLoginInfo.setIpaddr(IpUtils.getIpAddr());
		sysLoginInfo.setMsg("登录成功");
		sysLoginInfo.setCreateTime(LocalDateTime.now());
		sysLoginInfo.setCreateBy(username);
		sysLoginInfo.setUpdateBy(username);

		if (!BCrypt.checkpw(password, sysUser.getPassword())) {
			sysLoginInfo.setMsg("账号密码不正确");
			sysLoginInfo.setStatus(SecurityConstants.LOGIN_FAIL);
			SpringContextHolder.publishEvent(new SysLoginLogEvent(sysLoginInfo));
			throw new TWTException("账号密码不正确");
		}

		if (sysUser.getStatus().equals("1")) {
			String error = "账号已被冻结";
			sysLoginInfo.setMsg(error);
			sysLoginInfo.setStatus(SecurityConstants.LOGIN_FAIL);
			SpringContextHolder.publishEvent(new SysLoginLogEvent(sysLoginInfo));
			throw new TWTException(error);
		}

		SpringContextHolder.publishEvent(new SysLoginLogEvent(sysLoginInfo));
		return sysUser;
	}

	/**
	 * 构建用户信息
	 * @param sysUser SysUser
	 * @return LoginUser
	 */
	private LoginUser buildLoginUser(SysUser sysUser) {
		LoginUser loginUser = new LoginUser();

		loginUser.setUserId(sysUser.getUserId());
		loginUser.setUsername(sysUser.getUsername());
		loginUser.setDeptId(sysUser.getDeptId());
		// token 绑定登录租户：TenantBindInterceptor 依据该值拒绝换头穿越
		loginUser.setTenantId(sysUser.getTenantId());

		// 权限集合
		Set<String> permissions = iSysPermissionService.getMenuPermission(sysUser.getUserId());

		loginUser.setPermissions(permissions);
		loginUser.setRoles(sysUser.getRoles());
		return loginUser;
	}

}
