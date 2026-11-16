package com.twelvet.system.server.service.impl;

import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.system.api.domain.SysUser;
import com.twelvet.system.server.mapper.SysDeptMapper;
import com.twelvet.system.server.mapper.SysPostMapper;
import com.twelvet.system.server.mapper.SysRoleMapper;
import com.twelvet.system.server.mapper.SysUserMapper;
import com.twelvet.system.server.mapper.SysUserPostMapper;
import com.twelvet.system.server.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 后台编辑用户时手机号唯一性前置校验。
 * <p>
 * 校验口径必须与数据库唯一索引 uk_sys_user_tenant_phone 一致：手机号冲突时抛可读业务异常， 而不是让 DuplicateKeyException
 * 冒泡成 500；手机号未变（查回的是自己）时不得误拦。
 * </p>
 */
class SysUserServiceImplPhoneUniqueTest {

	private final SysUserMapper userMapper = mock(SysUserMapper.class);

	private final SysRoleMapper roleMapper = mock(SysRoleMapper.class);

	private final SysPostMapper postMapper = mock(SysPostMapper.class);

	private final SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);

	private final SysUserPostMapper userPostMapper = mock(SysUserPostMapper.class);

	private final SysDeptMapper deptMapper = mock(SysDeptMapper.class);

	/**
	 * TenantContext 是 ThreadLocal，纯单测里显式设置以模拟后台请求线程；用例结束后清理， 避免污染同一线程上的其他测试。
	 */
	@BeforeEach
	void setUpTenantContext() {
		TenantContext.set(1L);
	}

	@AfterEach
	void clearTenantContext() {
		TenantContext.clear();
	}

	@Test
	@DisplayName("改为已被占用的手机号：抛业务异常，不落库")
	void updateUser_duplicatePhone_throws() {
		SysUser other = new SysUser();
		other.setUserId(999L);
		when(userMapper.checkPhoneUnique(any(), any())).thenReturn(other);
		SysUserServiceImpl service = new SysUserServiceImpl(userMapper, roleMapper, postMapper, userRoleMapper,
				userPostMapper, deptMapper);
		SysUser input = new SysUser();
		input.setUserId(1L);
		input.setPhonenumber("13800001005");

		TWTException ex = assertThrows(TWTException.class, () -> service.updateUser(input));

		assertTrue(ex.getMessage().contains("手机号已被占用"));
		verify(userMapper, never()).updateUser(any());
	}

	@Test
	@DisplayName("手机号未被占用：正常更新")
	void updateUser_uniquePhone_updates() {
		when(userMapper.checkPhoneUnique(any(), any())).thenReturn(null);
		when(userMapper.updateUser(any())).thenReturn(1);
		SysUserServiceImpl service = new SysUserServiceImpl(userMapper, roleMapper, postMapper, userRoleMapper,
				userPostMapper, deptMapper);
		SysUser input = new SysUser();
		input.setUserId(1L);
		input.setPhonenumber("13800001005");

		service.updateUser(input);

		verify(userMapper).updateUser(any());
	}

	@Test
	@DisplayName("手机号未变（查回的是自己）：不拦，正常更新")
	void updateUser_samePhoneSelf_updates() {
		SysUser self = new SysUser();
		self.setUserId(1L);
		self.setPhonenumber("13800001005");
		when(userMapper.checkPhoneUnique(eq("13800001005"), any())).thenReturn(self);
		when(userMapper.updateUser(any())).thenReturn(1);
		SysUserServiceImpl service = new SysUserServiceImpl(userMapper, roleMapper, postMapper, userRoleMapper,
				userPostMapper, deptMapper);
		SysUser input = new SysUser();
		input.setUserId(1L);
		input.setPhonenumber("13800001005");

		service.updateUser(input);

		verify(userMapper).updateUser(any());
	}

	/**
	 * 个人设置入口（SysProfileController.updateProfile → updateUserProfile →
	 * mapper.updateUser）同样会写 phonenumber，必须与 updateUser 共用同一套唯一性前置校验，否则撞唯一索引直接 500。
	 */
	@Test
	@DisplayName("个人设置改为已被占用的手机号：抛业务异常，不落库")
	void updateUserProfile_duplicatePhone_throws() {
		SysUser other = new SysUser();
		other.setUserId(999L);
		when(userMapper.checkPhoneUnique(any(), any())).thenReturn(other);
		SysUserServiceImpl service = new SysUserServiceImpl(userMapper, roleMapper, postMapper, userRoleMapper,
				userPostMapper, deptMapper);
		SysUser input = new SysUser();
		input.setUserId(1L);
		input.setPhonenumber("13800001005");

		TWTException ex = assertThrows(TWTException.class, () -> service.updateUserProfile(input));

		assertTrue(ex.getMessage().contains("手机号已被占用"));
		verify(userMapper, never()).updateUser(any());
	}

	@Test
	@DisplayName("个人设置手机号未变（查回的是自己）：不拦，正常更新")
	void updateUserProfile_samePhoneSelf_updates() {
		SysUser self = new SysUser();
		self.setUserId(1L);
		self.setPhonenumber("13800001005");
		when(userMapper.checkPhoneUnique(eq("13800001005"), any())).thenReturn(self);
		when(userMapper.updateUser(any())).thenReturn(1);
		SysUserServiceImpl service = new SysUserServiceImpl(userMapper, roleMapper, postMapper, userRoleMapper,
				userPostMapper, deptMapper);
		SysUser input = new SysUser();
		input.setUserId(1L);
		input.setPhonenumber("13800001005");

		service.updateUserProfile(input);

		verify(userMapper).updateUser(any());
	}

}
