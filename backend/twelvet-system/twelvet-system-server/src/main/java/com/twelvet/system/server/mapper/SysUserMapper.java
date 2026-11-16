package com.twelvet.system.server.mapper;

import com.twelvet.system.api.domain.SysUser;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author twelvet
 * @Description: 用户信息Mapper
 */
public interface SysUserMapper {

	/**
	 * 根据条件分页查询用户列表
	 * @param sysUser 用户信息
	 * @return 用户信息集合信息
	 */
	List<SysUser> selectUserList(SysUser sysUser);

	/**
	 * 通过用户名查询用户
	 * @param userName 用户名
	 * @return 用户对象信息
	 */
	SysUser selectUserByUserName(String userName);

	/**
	 * 通过用户名+租户查询用户（租户级登录：同名账号各租户独立）
	 * @param userName 用户名
	 * @param tenantId 租户ID
	 * @return 用户对象信息
	 */
	SysUser selectUserByUserNameAndTenant(@Param("userName") String userName, @Param("tenantId") Long tenantId);

	/**
	 * 通过手机号 + 租户查询用户（C 端手机号登录用）
	 * @param phonenumber 手机号
	 * @param tenantId 租户ID，null 表示不限租户
	 * @return 用户信息，不存在返回 null
	 */
	SysUser selectUserByPhoneAndTenant(@Param("phonenumber") String phonenumber, @Param("tenantId") Long tenantId);

	/**
	 * 用户插入后回填全局用户ID（= 自身 user_id，作为首个身份锚点）
	 * @param userId 用户ID
	 * @return 结果
	 */
	int backfillGlobalUserId(Long userId);

	/**
	 * 任意租户内按手机号查全局用户ID（跨租户同人串联键）
	 * @param phonenumber 手机号码
	 * @return 全局用户ID（未命中返回 null）
	 */
	Long selectGlobalIdByPhone(String phonenumber);

	/**
	 * 通过用户ID查询用户
	 * @param userId 用户ID
	 * @return 用户对象信息
	 */
	SysUser selectUserById(Long userId);

	/**
	 * 新增用户信息
	 * @param user 用户信息
	 * @return 结果
	 */
	int insertUser(SysUser user);

	/**
	 * 修改用户信息
	 * @param user 用户信息
	 * @return 结果
	 */
	int updateUser(SysUser user);

	/**
	 * 修改用户头像（租户上下文存在时按租户圈定，同名账号跨租户独立）
	 * @param userName 用户名
	 * @param avatar 头像地址
	 * @param tenantId 租户ID
	 * @return 结果
	 */
	int updateUserAvatar(@Param("userName") String userName, @Param("avatar") String avatar,
			@Param("tenantId") Long tenantId);

	/**
	 * 重置用户密码（租户上下文存在时按租户圈定，同名账号跨租户独立）
	 * @param userName 用户名
	 * @param password 密码
	 * @param tenantId 租户ID
	 * @return 结果
	 */
	int resetUserPwd(@Param("userName") String userName, @Param("password") String password,
			@Param("tenantId") Long tenantId);

	/**
	 * 通过用户ID删除用户
	 * @param userId 用户ID
	 * @return 结果
	 */
	int deleteUserById(Long userId);

	/**
	 * 批量删除用户信息
	 * @param userIds 需要删除的用户ID
	 * @return 结果
	 */
	int deleteUserByIds(Long[] userIds);

	/**
	 * 校验用户名称是否唯一（租户上下文存在时按租户判定）
	 * @param userName 用户名称
	 * @param tenantId 租户ID（可空 = 全局判定）
	 * @return 结果
	 */
	int checkUserNameUnique(@Param("userName") String userName, @Param("tenantId") Long tenantId);

	/**
	 * 校验手机号码是否唯一（租户上下文存在时按租户判定，跨租户允许重复）
	 * @param phonenumber 手机号码
	 * @param tenantId 租户ID（可空 = 全局判定）
	 * @return 结果
	 */
	SysUser checkPhoneUnique(@Param("phonenumber") String phonenumber, @Param("tenantId") Long tenantId);

	/**
	 * 校验email是否唯一（租户上下文存在时按租户判定）
	 * @param email 用户邮箱
	 * @param tenantId 租户ID（可空 = 全局判定）
	 * @return 结果
	 */
	SysUser checkEmailUnique(@Param("email") String email, @Param("tenantId") Long tenantId);

}
