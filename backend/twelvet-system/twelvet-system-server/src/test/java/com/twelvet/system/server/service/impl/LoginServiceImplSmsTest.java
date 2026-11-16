package com.twelvet.system.server.service.impl;

import cn.dev33.satoken.context.mock.SaTokenContextMockUtil;
import com.twelvet.framework.RedisUtils;
import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.framework.utils.SpringContextHolder;
import com.twelvet.system.api.domain.SysUser;
import com.twelvet.system.api.domain.vo.LoginVO;
import com.twelvet.system.server.mapper.SysUserMapper;
import com.twelvet.system.server.service.ISmsCodeService;
import com.twelvet.system.server.service.ISysPermissionService;
import com.twelvet.system.server.service.ISysUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginServiceImplSmsTest {

	private static final String MOBILE = "13800001005";

	private final SysUserMapper userMapper = mock(SysUserMapper.class);

	private final ISysPermissionService permissionService = mock(ISysPermissionService.class);

	private final ISmsCodeService smsCodeService = mock(ISmsCodeService.class);

	private final ISysUserService sysUserService = mock(ISysUserService.class);

	/**
	 * RedisUtils 的静态字段在类初始化时执行 {@code SpringContextHolder.getBean("redisTemplate")}，脱离
	 * Spring 上下文时为 null 会抛 NPE，导致 Mockito 无法插桩该类（Cannot instrument ... could not be
	 * initialized）。 这里先给 SpringContextHolder 注入一个返回 mock RedisTemplate 的 beanFactory，
	 * 使类初始化成功；随后整类拦截 RedisUtils 静态调用 —— 签发令牌时的刷新令牌写 Redis 在纯单测下 无真实 Redis，且其他测试类可能已用不同的
	 * mock 初始化过该静态字段，逐调用拦截才不会 NPE。
	 */
	private static MockedStatic<RedisUtils> redis;

	@BeforeAll
	static void primeSpringContextHolder() {
		ConfigurableListableBeanFactory beanFactory = mock(ConfigurableListableBeanFactory.class);
		RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
		ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(beanFactory.getBean("redisTemplate")).thenReturn(redisTemplate);
		new SpringContextHolder().postProcessBeanFactory(beanFactory);
		redis = mockStatic(RedisUtils.class);
	}

	@AfterAll
	static void closeRedis() {
		redis.close();
	}

	/**
	 * smsLogin 走 {@code SecurityUtils.loginByDevice}（Sa-Token 需要请求上下文）与
	 * {@code IpUtils.getIpAddr}（需要 ServletRequestAttributes），纯单测下用 Sa-Token 自带的 mock 上下文与
	 * mock 请求补齐。
	 */
	@BeforeEach
	void setUpWebContext() {
		SaTokenContextMockUtil.setMockContext();
		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getRemoteAddr()).thenReturn("127.0.0.1");
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
	}

	@AfterEach
	void tearDownWebContext() {
		SaTokenContextMockUtil.clearContext();
		RequestContextHolder.resetRequestAttributes();
	}

	@Test
	@DisplayName("已有手机号：直接登录，不建号")
	void smsLogin_existingUser_logsIn() {
		SysUser existing = new SysUser();
		existing.setUserId(200L);
		existing.setUsername("u13800001005");
		existing.setUserType("02");
		existing.setStatus("0");
		when(userMapper.selectUserByPhoneAndTenant(eq(MOBILE), any())).thenReturn(existing);
		when(permissionService.getMenuPermission(200L)).thenReturn(Set.of("marketplace:home"));
		LoginServiceImpl service = new LoginServiceImpl(userMapper, permissionService, smsCodeService, sysUserService);

		service.smsLogin(MOBILE, "123456");

		verify(smsCodeService).verify(MOBILE, "123456");
		verify(sysUserService, never()).insertUser(any());
	}

	@Test
	@DisplayName("新手机号：自动建号，字段符合约定")
	void smsLogin_newUser_createsAccount() {
		when(userMapper.selectUserByPhoneAndTenant(eq(MOBILE), any())).thenReturn(null);
		when(sysUserService.insertUser(any())).thenAnswer(inv -> {
			SysUser u = inv.getArgument(0);
			u.setUserId(300L);
			return 1;
		});
		when(permissionService.getMenuPermission(anyLong())).thenReturn(Set.of());
		LoginServiceImpl service = new LoginServiceImpl(userMapper, permissionService, smsCodeService, sysUserService);

		service.smsLogin(MOBILE, "123456");

		ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
		verify(sysUserService).insertUser(captor.capture());
		SysUser created = captor.getValue();
		assertTrue(created.getUsername().startsWith("u" + MOBILE + "_"),
				"username 应为 u+手机号+随机后缀，实际: " + created.getUsername());
		assertTrue(created.getUsername().length() <= 30,
				"username 不得超过 varchar(30)，实际长度: " + created.getUsername().length());
		assertEquals("邻里用户1005", created.getNickName());
		assertEquals(MOBILE, created.getPhonenumber());
		assertEquals("02", created.getUserType());
		assertEquals("0", created.getStatus());
		assertEquals("0", created.getDelFlag());
		assertEquals("", created.getAvatar());
		assertArrayEquals(new Long[] { 2L }, created.getRoleIds());
		// 随机 UUID 的 BCrypt 哈希：符合 BCrypt 定长格式 $2a$10$<53 字符>
		// 注意：不能用 contains("-") 判明文 —— simpleUUID() 本身就无横线，那样写恒为真、约束不了任何东西。
		// BCrypt 哈希固定 60 字符，而未哈希的 simpleUUID() 是 32 字符 —— 长度才是能区分的判据。
		assertNotNull(created.getPassword());
		assertTrue(created.getPassword().startsWith("$2"));
		assertTrue(created.getPassword().length() >= 55,
				"密码应为 BCrypt 哈希（定长 60），而非 32 字符明文 UUID：" + created.getPassword().length());
	}

	@Test
	@DisplayName("软删后重新注册：同一手机号两次建号的 username 不同（不再被唯一键锁死）")
	void smsLogin_sameMobileAfterSoftDelete_generatesDistinctUsername() {
		// 场景：用户 A 建号 → 被后台软删（del_flag='2'）→ A 再次手机号登录。
		// 查询只认 del_flag='0' 故返回 null，会走建号；而用户名唯一键不含 del_flag，
		// 若 username 是确定性的 "u"+mobile 就会撞键，手机号被永久锁死。
		List<String> usernames = new ArrayList<>();
		when(userMapper.selectUserByPhoneAndTenant(eq(MOBILE), any())).thenReturn(null);
		when(sysUserService.insertUser(any())).thenAnswer(inv -> {
			SysUser u = inv.getArgument(0);
			usernames.add(u.getUsername());
			u.setUserId(300L);
			return 1;
		});
		when(permissionService.getMenuPermission(anyLong())).thenReturn(Set.of());
		LoginServiceImpl service = new LoginServiceImpl(userMapper, permissionService, smsCodeService, sysUserService);

		service.smsLogin(MOBILE, "123456");
		service.smsLogin(MOBILE, "123456");

		assertEquals(2, usernames.size(), "两次登录应各建号一次");
		assertFalse(usernames.get(0).equals(usernames.get(1)), "两次建号的 username 必须不同，否则软删行会占住唯一键: " + usernames);
		assertTrue(usernames.stream().allMatch(name -> name.startsWith("u" + MOBILE + "_")));
		assertTrue(usernames.stream().allMatch(name -> name.length() <= 30));
	}

	@Test
	@DisplayName("验证码错误：抛出，不查询用户")
	void smsLogin_badCode_throws() {
		doThrow(new TWTException("验证码不正确")).when(smsCodeService).verify(anyString(), anyString());
		LoginServiceImpl service = new LoginServiceImpl(userMapper, permissionService, smsCodeService, sysUserService);

		assertThrows(TWTException.class, () -> service.smsLogin(MOBILE, "000000"));
		verify(userMapper, never()).selectUserByPhoneAndTenant(anyString(), any());
	}

	@Test
	@DisplayName("账号被冻结：拒绝登录")
	void smsLogin_frozenUser_rejected() {
		SysUser frozen = new SysUser();
		frozen.setUserId(200L);
		frozen.setUserType("02");
		frozen.setStatus("1");
		when(userMapper.selectUserByPhoneAndTenant(eq(MOBILE), any())).thenReturn(frozen);
		LoginServiceImpl service = new LoginServiceImpl(userMapper, permissionService, smsCodeService, sysUserService);

		TWTException ex = assertThrows(TWTException.class, () -> service.smsLogin(MOBILE, "123456"));
		assertTrue(ex.getMessage().contains("冻结"));
	}

	@Test
	@DisplayName("并发建号冲突：捕获 DuplicateKeyException 后重查并登录")
	void smsLogin_concurrentCreate_fallsBackToRequery() {
		SysUser created = new SysUser();
		created.setUserId(301L);
		created.setUserType("02");
		created.setStatus("0");
		when(userMapper.selectUserByPhoneAndTenant(eq(MOBILE), any())).thenReturn(null).thenReturn(created);
		when(sysUserService.insertUser(any())).thenThrow(new org.springframework.dao.DuplicateKeyException("dup"));
		when(permissionService.getMenuPermission(anyLong())).thenReturn(Set.of());
		LoginServiceImpl service = new LoginServiceImpl(userMapper, permissionService, smsCodeService, sysUserService);

		service.smsLogin(MOBILE, "123456");

		verify(userMapper, org.mockito.Mockito.times(2)).selectUserByPhoneAndTenant(eq(MOBILE), any());
	}

	@Test
	@DisplayName("B 端账号（user_type=00）：拒绝手机号登录，不签发 token")
	void smsLogin_adminAccount_rejected() {
		// admin 种子手机号 15788888888、user_type='00'：mock 固定码下知道手机号即可拿到超管会话
		SysUser admin = new SysUser();
		admin.setUserId(1L);
		admin.setUsername("admin");
		admin.setUserType("00");
		admin.setStatus("0");
		when(userMapper.selectUserByPhoneAndTenant(eq(MOBILE), any())).thenReturn(admin);
		LoginServiceImpl service = new LoginServiceImpl(userMapper, permissionService, smsCodeService, sysUserService);

		TWTException ex = assertThrows(TWTException.class, () -> service.smsLogin(MOBILE, "123456"));

		assertTrue(ex.getMessage().contains("不支持手机号登录"));
		// 未走到发 token 的路径
		verify(permissionService, never()).getMenuPermission(anyLong());
		verify(sysUserService, never()).insertUser(any());
	}

	@Test
	@DisplayName("并发冲突后重查仍为空：抛业务异常而非 NPE")
	void smsLogin_duplicateKeyThenMissing_throws() {
		// 软删行占位手机号（或 username 撞唯一键）：insert 撞索引，重查仍查不到
		when(userMapper.selectUserByPhoneAndTenant(eq(MOBILE), any())).thenReturn(null).thenReturn(null);
		when(sysUserService.insertUser(any())).thenThrow(new org.springframework.dao.DuplicateKeyException("dup"));
		LoginServiceImpl service = new LoginServiceImpl(userMapper, permissionService, smsCodeService, sysUserService);

		TWTException ex = assertThrows(TWTException.class, () -> service.smsLogin(MOBILE, "123456"));

		assertTrue(ex.getMessage().contains("账号状态异常"));
		verify(permissionService, never()).getMenuPermission(anyLong());
	}

}
