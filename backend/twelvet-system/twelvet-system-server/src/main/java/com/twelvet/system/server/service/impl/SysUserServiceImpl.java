package com.twelvet.system.server.service.impl;

import cn.dev33.satoken.secure.BCrypt;
import cn.hutool.core.collection.CollectionUtil;
import com.twelvet.framework.core.constants.UserConstants;
import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.framework.datascope.annotation.SysDataScope;
import com.twelvet.framework.security.utils.SecurityUtils;
import com.twelvet.framework.utils.SpringContextHolder;
import com.twelvet.framework.utils.StrUtils;
import com.twelvet.framework.utils.TUtils;
import com.twelvet.system.api.domain.*;
import com.twelvet.system.server.mapper.*;
import com.twelvet.system.server.service.ISysUserService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author twelvet
 * @Description: 用户信息
 */
@Service
public class SysUserServiceImpl implements ISysUserService {

	private static final Logger log = LoggerFactory.getLogger(SysUserServiceImpl.class);

	private final SysUserMapper sysUserMapper;

	private final SysRoleMapper sysRoleMapper;

	private final SysPostMapper sysPostMapper;

	private final SysUserRoleMapper sysUserRoleMapper;

	private final SysUserPostMapper sysUserPostMapper;

	private final SysDeptMapper sysDeptMapper;

	public SysUserServiceImpl(SysUserMapper sysUserMapper, SysRoleMapper sysRoleMapper, SysPostMapper sysPostMapper,
			SysUserRoleMapper sysUserRoleMapper, SysUserPostMapper sysUserPostMapper, SysDeptMapper sysDeptMapper) {
		this.sysUserMapper = sysUserMapper;
		this.sysRoleMapper = sysRoleMapper;
		this.sysPostMapper = sysPostMapper;
		this.sysUserRoleMapper = sysUserRoleMapper;
		this.sysUserPostMapper = sysUserPostMapper;
		this.sysDeptMapper = sysDeptMapper;
	}

	/**
	 * 根据条件分页查询用户列表
	 * @param user 用户信息
	 * @return 用户信息集合信息
	 */
	@Override
	@SysDataScope(deptAlias = "d", userAlias = "u")
	public List<SysUser> selectUserList(SysUser user) {
		return sysUserMapper.selectUserList(user);
	}

	/**
	 * 通过用户名查询用户(此接口会隐藏部分信息,请对号入座使用)
	 * @param userName 用户名
	 * @return 用户对象信息
	 */
	@Override
	public SysUser selectUserByUserName(String userName, boolean hidden) {
		SysUser sysUser = selectUserByUserNameScoped(userName);
		if (sysUser == null) {
			return null;
		}

		Long deptId = sysUser.getDeptId();
		SysDept sysDept = sysDeptMapper.selectDeptById(deptId);
		sysUser.setDept(sysDept);

		// 隐藏模式：构建副本承载脱敏数据（不修改原 entity，防止 MyBatis 缓存脏数据）
		if (hidden) {
			SysUser safeUser = new SysUser();
			safeUser.setUserId(sysUser.getUserId());
			safeUser.setUsername(sysUser.getUsername());
			safeUser.setPhonenumber(StrUtils.hidePhone(sysUser.getPhonenumber()));
			safeUser.setEmail(StrUtils.hideEmail(sysUser.getEmail()));
			safeUser.setPassword(null); // hidden=true 时清除密码
			safeUser.setDept(sysUser.getDept());
			safeUser.setUsername(sysUser.getUsername());
			safeUser.setNickName(sysUser.getNickName());
			safeUser.setUserType(sysUser.getUserType());
			return safeUser;
		}

		// hidden=false 时保留原始数据（updatePwd 等需要密码的场景）
		return sysUser;
	}

	/**
	 * 按用户名查询用户（租户上下文存在时圈定本租户；同名账号跨租户独立，与租户级登录同语义）
	 * @param userName 用户名
	 * @return 用户对象信息
	 */
	private SysUser selectUserByUserNameScoped(String userName) {
		Long tenantId = TenantContext.get();
		return tenantId != null ? sysUserMapper.selectUserByUserNameAndTenant(userName, tenantId)
				: sysUserMapper.selectUserByUserName(userName);
	}

	/**
	 * 通过用户ID查询用户
	 * @param userId 用户ID
	 * @return 用户对象信息
	 */
	@Override
	public SysUser selectUserById(Long userId) {
		SysUser sysUser = sysUserMapper.selectUserById(userId);
		sysUser.setPassword(null);
		return sysUser;
	}

	/**
	 * 查询用户所属角色组
	 * @param userName 用户名
	 * @return 结果
	 */
	@Override
	public String selectUserRoleGroup(String userName) {
		List<SysRole> list = sysRoleMapper.selectRolesByUserName(userName);
		StringBuilder idsStr = new StringBuilder();
		for (SysRole role : list) {
			idsStr.append(role.getRoleName()).append(",");
		}
		if (StringUtils.isNotEmpty(idsStr.toString())) {
			return idsStr.substring(0, idsStr.length() - 1);
		}
		return idsStr.toString();
	}

	/**
	 * 查询用户所属岗位组
	 * @param userName 用户名
	 * @return 结果
	 */
	@Override
	public String selectUserPostGroup(String userName) {
		List<SysPost> list = sysPostMapper.selectPostsByUserName(userName);
		StringBuilder idsStr = new StringBuilder();
		for (SysPost post : list) {
			idsStr.append(post.getPostName()).append(",");
		}
		if (StringUtils.isNotEmpty(idsStr.toString())) {
			return idsStr.substring(0, idsStr.length() - 1);
		}
		return idsStr.toString();
	}

	/**
	 * 校验用户名称是否唯一
	 * @param userName 用户名称
	 * @return 结果
	 */
	@Override
	public String checkUserNameUnique(String userName) {
		int count = sysUserMapper.checkUserNameUnique(userName, TenantContext.get());
		if (count > 0) {
			return UserConstants.NOT_UNIQUE;
		}
		return UserConstants.UNIQUE;
	}

	/**
	 * 校验用户名称是否唯一
	 * @param user 用户信息
	 * @return String
	 */
	@Override
	public String checkPhoneUnique(SysUser user) {
		Long userId = Objects.isNull(user.getUserId()) ? -1L : user.getUserId();
		// 按租户判定：跨租户同手机号是「1 人跨 N 租户」特性，不冲突
		SysUser info = sysUserMapper.checkPhoneUnique(user.getPhonenumber(), TenantContext.get());
		if (Objects.nonNull(info) && info.getUserId().longValue() != userId.longValue()) {
			return UserConstants.NOT_UNIQUE;
		}
		return UserConstants.UNIQUE;
	}

	/**
	 * 校验email是否唯一
	 * @param user 用户信息
	 * @return String
	 */
	@Override
	public String checkEmailUnique(SysUser user) {
		Long userId = Objects.isNull(user.getUserId()) ? -1L : user.getUserId();
		SysUser info = sysUserMapper.checkEmailUnique(user.getEmail(), TenantContext.get());
		if (Objects.nonNull(info) && info.getUserId().longValue() != userId.longValue()) {
			return UserConstants.NOT_UNIQUE;
		}
		return UserConstants.UNIQUE;
	}

	/**
	 * 校验用户是否允许操作
	 * @param user 用户信息
	 */
	@Override
	public void checkUserAllowed(SysUser user) {
		if (Objects.nonNull(user.getUserId()) && user.isAdmin()) {
			throw new TWTException("不允许操作超级管理员用户");
		}
	}

	/**
	 * 校验用户是否有数据权限
	 * @param userId 用户id
	 */
	@Override
	public void checkUserDataScope(Long userId) {
		if (!SysUser.isAdmin(SecurityUtils.getLoginUser().getUserId())) {
			SysUser user = new SysUser();
			user.setUserId(userId);
			// 用户表读隔离：非超管只能在租户上下文圈内访问用户数据（跨租户 edit/remove/resetPwd 全被拦）
			user.setTenantId(TenantContext.get());
			List<SysUser> users = SpringContextHolder.getAopProxy(this).selectUserList(user);
			if (StrUtils.isEmpty(users)) {
				throw new TWTException("没有权限访问用户数据！");
			}
		}
	}

	/**
	 * 新增保存用户信息
	 * @param user 用户信息
	 * @return 结果
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public int insertUser(SysUser user) {
		// 跨租户同人串联：手机号在任意租户命中既有身份 → 复用其 global_user_id（1 人跨 N 租户）
		if (user.getGlobalUserId() == null && StrUtils.isNotEmpty(user.getPhonenumber())) {
			Long globalId = sysUserMapper.selectGlobalIdByPhone(user.getPhonenumber());
			if (Objects.nonNull(globalId)) {
				user.setGlobalUserId(globalId);
			}
		}
		// 新增用户信息
		int rows = sysUserMapper.insertUser(user);
		// 首个身份：以自身 user_id 作为全局锚点
		if (Objects.isNull(user.getGlobalUserId())) {
			sysUserMapper.backfillGlobalUserId(user.getUserId());
		}
		// 新增用户岗位关联
		insertUserPost(user);
		// 新增用户与角色管理
		insertUserRole(user);
		return rows;
	}

	/**
	 * 修改保存用户信息
	 * @param user 用户信息
	 * @return 结果
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public int updateUser(SysUser user) {
		// 手机号唯一索引（uk_sys_user_tenant_phone）冲突时兜底翻译成可读提示，避免 DuplicateKeyException 500。
		// checkPhoneUnique 已处理「查回的是自己不算冲突」与租户上下文；控制器虽已前置判断，
		// 但 importUser（Excel 覆盖导入）等调用方绕过控制器，故服务层仍需兜底。
		if (StrUtils.isNotEmpty(user.getPhonenumber())
				&& UserConstants.NOT_UNIQUE.equals(this.checkPhoneUnique(user))) {
			throw new TWTException("手机号已被占用");
		}
		Long userId = user.getUserId();
		// 删除用户与角色关联
		sysUserRoleMapper.deleteUserRoleByUserId(userId);
		// 新增用户与角色管理
		insertUserRole(user);
		// 删除用户与岗位关联
		sysUserPostMapper.deleteUserPostByUserId(userId);
		// 新增用户与岗位管理
		insertUserPost(user);
		return sysUserMapper.updateUser(user);
	}

	/**
	 * 修改用户状态
	 * @param user 用户信息
	 * @return 结果
	 */
	@Override
	public int updateUserStatus(SysUser user) {
		return sysUserMapper.updateUser(user);
	}

	/**
	 * 修改用户基本信息
	 * @param user 用户信息
	 * @return 结果
	 */
	@Override
	public int updateUserProfile(SysUser user) {
		// 个人设置也能改手机号（SysProfileController → 本方法 → mapper.updateUser 会写 phonenumber），
		// 与 updateUser 同一套唯一性前置校验，否则撞唯一索引直接 500。
		if (StrUtils.isNotEmpty(user.getPhonenumber())
				&& UserConstants.NOT_UNIQUE.equals(this.checkPhoneUnique(user))) {
			throw new TWTException("手机号已被占用");
		}
		return sysUserMapper.updateUser(user);
	}

	/**
	 * 修改用户头像
	 * @param userName 用户名
	 * @param avatar 头像地址
	 * @return 结果
	 */
	@Override
	public boolean updateUserAvatar(String userName, String avatar) {
		// 同名账号跨租户独立：按租户圈定，防止误更新其他租户的同名行
		return sysUserMapper.updateUserAvatar(userName, avatar, TenantContext.get()) > 0;
	}

	/**
	 * 重置用户密码
	 * @param user 用户信息
	 * @return 结果
	 */
	@Override
	public int resetPwd(SysUser user) {
		return sysUserMapper.updateUser(user);
	}

	/**
	 * 重置用户密码
	 * @param userName 用户名
	 * @param password 密码
	 * @return 结果
	 */
	@Override
	public int resetUserPwd(String userName, String password) {
		return sysUserMapper.resetUserPwd(userName, password, TenantContext.get());
	}

	/**
	 * 新增用户角色信息
	 * @param user 用户对象
	 */
	public void insertUserRole(SysUser user) {
		Long[] roles = user.getRoleIds();
		if (Objects.nonNull(roles)) {
			// 新增用户与角色管理
			List<SysUserRole> list = new ArrayList<>();
			for (Long roleId : roles) {
				SysUserRole ur = new SysUserRole();
				ur.setUserId(user.getUserId());
				ur.setRoleId(roleId);
				list.add(ur);
			}
			if (list.size() > 0) {
				sysUserRoleMapper.batchUserRole(list);
			}
		}
	}

	/**
	 * 新增用户岗位信息
	 * @param user 用户对象
	 */
	public void insertUserPost(SysUser user) {
		Long[] posts = user.getPostIds();
		if (Objects.nonNull(posts)) {
			// 新增用户与岗位管理
			List<SysUserPost> list = new ArrayList<SysUserPost>();
			for (Long postId : posts) {
				SysUserPost up = new SysUserPost();
				up.setUserId(user.getUserId());
				up.setPostId(postId);
				list.add(up);
			}
			if (list.size() > 0) {
				sysUserPostMapper.batchUserPost(list);
			}
		}
	}

	/**
	 * 通过用户ID删除用户
	 * @param userId 用户ID
	 * @return 结果
	 */
	@Override
	public int deleteUserById(Long userId) {
		// 删除用户与角色关联
		sysUserRoleMapper.deleteUserRoleByUserId(userId);
		// 删除用户与岗位表
		sysUserPostMapper.deleteUserPostByUserId(userId);
		return sysUserMapper.deleteUserById(userId);
	}

	/**
	 * 批量删除用户信息
	 * @param userIds 需要删除的用户ID
	 * @return 结果
	 */
	@Override
	public int deleteUserByIds(Long[] userIds) {
		for (Long userId : userIds) {
			checkUserAllowed(new SysUser(userId));
			checkUserDataScope(userId);
		}
		return sysUserMapper.deleteUserByIds(userIds);
	}

	/**
	 * 导入用户数据
	 * @param userList 用户数据列表
	 * @param cover 更新操作人
	 * @param operName 操作用户
	 * @return 结果
	 */
	@Override
	public String importUser(List<SysUser> userList, Boolean cover, String operName) {
		if (CollectionUtil.isEmpty(userList)) {
			throw new TWTException("导入用户数据不能为空！");
		}
		int successNum = 0;
		int failureNum = 0;
		StringBuilder successMsg = new StringBuilder();
		StringBuilder failureMsg = new StringBuilder();
		for (SysUser user : userList) {
			try {
				// 验证是否存在这个用户（租户内查重，同名账号跨租户允许）
				SysUser u = selectUserByUserNameScoped(user.getUsername());
				if (Objects.isNull(u)) {
					// 初始化密码为123456
					user.setPassword(BCrypt.hashpw("123456"));
					user.setCreateBy(operName);
					this.insertUser(user);
					successNum++;
					successMsg.append("<br/>")
						.append(successNum)
						.append("、账号 ")
						.append(user.getUsername())
						.append(" 导入成功");
				}
				else if (cover) {
					user.setUpdateBy(operName);
					this.updateUser(user);
					successNum++;
					successMsg.append("<br/>")
						.append(successNum)
						.append("、账号 ")
						.append(user.getUsername())
						.append(" 更新成功");
				}
				else {
					failureNum++;
					failureMsg.append("<br/>")
						.append(failureNum)
						.append("、账号 ")
						.append(user.getUsername())
						.append(" 已存在");
				}
			}
			catch (Exception e) {
				failureNum++;
				String msg = "<br/>" + failureNum + "、账号 " + user.getUsername() + " 导入失败：";
				failureMsg.append(msg).append(e.getMessage());
				log.error(msg, e);
			}
		}
		if (failureNum > 0) {
			failureMsg.insert(0, "很抱歉，导入失败！共 " + failureNum + " 条数据格式不正确，错误如下：");
			throw new TWTException(failureMsg.toString());
		}
		else {
			successMsg.insert(0, "恭喜您，数据已全部导入成功！共 " + successNum + " 条，数据如下：");
		}
		return successMsg.toString();
	}

}
