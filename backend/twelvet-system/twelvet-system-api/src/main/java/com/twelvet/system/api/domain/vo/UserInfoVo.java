package com.twelvet.system.api.domain.vo;

import com.twelvet.system.api.domain.SysPost;
import com.twelvet.system.api.domain.SysRole;
import com.twelvet.system.api.domain.SysUser;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Set;

/**
 * @author twelvet
 * @Description: 用户信息
 */
public class UserInfoVo implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	/**
	 * 用户信息
	 */
	private SysUser user;

	/**
	 * 角色权限集合
	 */
	private Set<String> roles;

	/**
	 * 权限集合
	 */
	private Set<String> permissions;

	/**
	 * 所有角色列表
	 */
	private List<SysRole> rolesAll;

	/**
	 * 所有岗位列表
	 */
	private List<SysPost> posts;

	/**
	 * 用户岗位IDs
	 */
	private List<Long> postIds;

	/**
	 * 用户角色IDs
	 */
	private List<Long> roleIds;

	/**
	 * 权限组
	 */
	private String roleGroup;

	/**
	 * 角色岗位
	 */
	private String postGroup;

	public SysUser getUser() {
		return user;
	}

	public void setUser(SysUser user) {
		this.user = user;
	}

	public Set<String> getRoles() {
		return roles;
	}

	public void setRoles(Set<String> roles) {
		this.roles = roles;
	}

	public Set<String> getPermissions() {
		return permissions;
	}

	public void setPermissions(Set<String> permissions) {
		this.permissions = permissions;
	}

	public List<SysRole> getRolesAll() {
		return rolesAll;
	}

	public void setRolesAll(List<SysRole> rolesAll) {
		this.rolesAll = rolesAll;
	}

	public List<SysPost> getPosts() {
		return posts;
	}

	public void setPosts(List<SysPost> posts) {
		this.posts = posts;
	}

	public List<Long> getPostIds() {
		return postIds;
	}

	public void setPostIds(List<Long> postIds) {
		this.postIds = postIds;
	}

	public List<Long> getRoleIds() {
		return roleIds;
	}

	public void setRoleIds(List<Long> roleIds) {
		this.roleIds = roleIds;
	}

	public String getRoleGroup() {
		return roleGroup;
	}

	public void setRoleGroup(String roleGroup) {
		this.roleGroup = roleGroup;
	}

	public String getPostGroup() {
		return postGroup;
	}

	public void setPostGroup(String postGroup) {
		this.postGroup = postGroup;
	}

	@Override
	public String toString() {
		return "UserInfoVo{" + "user=" + user + ", roles=" + roles + ", permissions=" + permissions + ", rolesAll="
				+ rolesAll + ", roleGroup='" + roleGroup + '\'' + ", postGroup='" + postGroup + '\'' + '}';
	}

}
