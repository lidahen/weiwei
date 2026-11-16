package com.twelvet.system.server.service.impl;

import com.twelvet.framework.RedisUtils;
import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.framework.utils.SpringContextHolder;
import com.twelvet.system.server.sms.MockSmsSender;
import com.twelvet.system.server.sms.SmsSender;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmsCodeServiceImplTest {

	private static final String MOBILE = "13800001005";

	private static final String DAILY_KEY = "sms:daily:" + MOBILE + ":"
			+ LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

	/**
	 * RedisUtils 的静态字段在类初始化时执行 {@code SpringContextHolder.getBean("redisTemplate")}，脱离
	 * Spring 上下文时为 null 会抛 NPE， 导致 Mockito 无法插桩该类（Cannot instrument ... could not be
	 * initialized）。 这里先给 SpringContextHolder 注入一个返回 mock RedisTemplate 的 beanFactory，
	 * 使类初始化成功；后续所有 RedisUtils 调用均由 mockStatic 接管，不会触及该字段。
	 */
	@BeforeAll
	static void primeSpringContextHolder() {
		ConfigurableListableBeanFactory beanFactory = mock(ConfigurableListableBeanFactory.class);
		when(beanFactory.getBean("redisTemplate")).thenReturn(mock(RedisTemplate.class));
		new SpringContextHolder().postProcessBeanFactory(beanFactory);
	}

	@Test
	@DisplayName("发送成功：返回固定验证码 123456，写入 Redis 并设置 300 秒 TTL")
	void sendCode_writesRedisAndReturnsMockCode() {
		MockSmsSender sender = new MockSmsSender();
		try (MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class)) {
			redis.when(() -> RedisUtils.hasKey(anyString())).thenReturn(false);
			redis.when(() -> RedisUtils.incrementAndGet(anyString())).thenReturn(1L);
			SmsCodeServiceImpl service = new SmsCodeServiceImpl(sender);

			String code = service.sendCode(MOBILE);

			assertEquals("123456", code);
			redis.verify(() -> RedisUtils.setCacheObject(eq("sms:code:" + MOBILE), eq("123456"), eq(300L),
					eq(TimeUnit.SECONDS)));
		}
	}

	@Test
	@DisplayName("60 秒内重复发送被拒绝")
	void sendCode_rejectsWithinInterval() {
		MockSmsSender sender = new MockSmsSender();
		try (MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class)) {
			redis.when(() -> RedisUtils.hasKey("sms:send:limit:" + MOBILE)).thenReturn(true);
			SmsCodeServiceImpl service = new SmsCodeServiceImpl(sender);

			TWTException ex = assertThrows(TWTException.class, () -> service.sendCode(MOBILE));
			assertTrue(ex.getMessage().contains("过于频繁"));
			redis.verify(() -> RedisUtils.setCacheObject(anyString(), any(), anyLong(), any(TimeUnit.class)), never());
		}
	}

	@Test
	@DisplayName("当日发送次数超上限被拒绝")
	void sendCode_rejectsWhenDailyLimitExceeded() {
		MockSmsSender sender = new MockSmsSender();
		try (MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class)) {
			redis.when(() -> RedisUtils.hasKey(anyString())).thenReturn(false);
			redis.when(() -> RedisUtils.incrementAndGet(anyString())).thenReturn(11L);
			SmsCodeServiceImpl service = new SmsCodeServiceImpl(sender);

			TWTException ex = assertThrows(TWTException.class, () -> service.sendCode(MOBILE));
			assertTrue(ex.getMessage().contains("上限"));
		}
	}

	@Test
	@DisplayName("校验成功：删除验证码（防重放）")
	void verify_success_deletesCode() {
		MockSmsSender sender = new MockSmsSender();
		try (MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class)) {
			redis.when(() -> RedisUtils.getCacheObject("sms:code:" + MOBILE)).thenReturn("123456");
			SmsCodeServiceImpl service = new SmsCodeServiceImpl(sender);

			service.verify(MOBILE, "123456");

			redis.verify(() -> RedisUtils.deleteObject("sms:code:" + MOBILE));
		}
	}

	@Test
	@DisplayName("验证码错误：计数 +1 并抛异常")
	void verify_wrongCode_incrementsFailCount() {
		MockSmsSender sender = new MockSmsSender();
		try (MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class)) {
			redis.when(() -> RedisUtils.getCacheObject("sms:code:" + MOBILE)).thenReturn("123456");
			redis.when(() -> RedisUtils.incrementAndGet("sms:fail:" + MOBILE)).thenReturn(1L);
			SmsCodeServiceImpl service = new SmsCodeServiceImpl(sender);

			assertThrows(TWTException.class, () -> service.verify(MOBILE, "000000"));
			redis.verify(() -> RedisUtils.incrementAndGet("sms:fail:" + MOBILE));
		}
	}

	@Test
	@DisplayName("错误达 5 次：作废验证码，并清理失败计数键")
	void verify_fiveFailures_invalidatesCode() {
		MockSmsSender sender = new MockSmsSender();
		try (MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class)) {
			redis.when(() -> RedisUtils.getCacheObject("sms:code:" + MOBILE)).thenReturn("123456");
			redis.when(() -> RedisUtils.incrementAndGet("sms:fail:" + MOBILE)).thenReturn(5L);
			SmsCodeServiceImpl service = new SmsCodeServiceImpl(sender);

			assertThrows(TWTException.class, () -> service.verify(MOBILE, "000000"));
			redis.verify(() -> RedisUtils.deleteObject("sms:code:" + MOBILE));
			// 失败计数必须与验证码一同作废，否则重新取码后首次输错即被直接锁死
			redis.verify(() -> RedisUtils.deleteObject("sms:fail:" + MOBILE));
		}
	}

	@Test
	@DisplayName("验证码不存在或已过期")
	void verify_missingCode_throws() {
		MockSmsSender sender = new MockSmsSender();
		try (MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class)) {
			redis.when(() -> RedisUtils.getCacheObject("sms:code:" + MOBILE)).thenReturn(null);
			SmsCodeServiceImpl service = new SmsCodeServiceImpl(sender);

			TWTException ex = assertThrows(TWTException.class, () -> service.verify(MOBILE, "123456"));
			assertTrue(ex.getMessage().contains("失效"));
		}
	}

	@Test
	@DisplayName("发送间隔键：精确 key、值 1、TTL 60 秒")
	void sendCode_writesLimitKeyWithSixtySecondTtl() {
		MockSmsSender sender = new MockSmsSender();
		try (MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class)) {
			redis.when(() -> RedisUtils.hasKey(anyString())).thenReturn(false);
			redis.when(() -> RedisUtils.incrementAndGet(anyString())).thenReturn(1L);
			SmsCodeServiceImpl service = new SmsCodeServiceImpl(sender);

			service.sendCode(MOBILE);

			redis.verify(() -> RedisUtils.setCacheObject(eq("sms:send:limit:" + MOBILE), eq("1"), eq(60L),
					eq(TimeUnit.SECONDS)));
		}
	}

	@Test
	@DisplayName("发送成功：向短信发送器下发同一个验证码")
	void sendCode_invokesSenderWithCode() {
		SmsSender sender = mock(SmsSender.class);
		try (MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class)) {
			redis.when(() -> RedisUtils.hasKey(anyString())).thenReturn(false);
			redis.when(() -> RedisUtils.incrementAndGet(anyString())).thenReturn(1L);
			SmsCodeServiceImpl service = new SmsCodeServiceImpl(sender);

			service.sendCode(MOBILE);

			verify(sender).send(MOBILE, "123456");
		}
	}

	@Test
	@DisplayName("重新发码必须清零失败计数：否则新码一输错就被判「次数过多」")
	void sendCode_clearsStaleFailCount() {
		// 失败计数键跨「验证码代次」共享：用户对旧码错了 4 次后重新获取新码，
		// 若不清零，在新码上仅错 1 次就会累计到 5 → 新码连同计数一起作废，
		// 用户被莫名要求「重新获取」，且白耗一次当日配额。
		MockSmsSender sender = new MockSmsSender();
		try (MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class)) {
			redis.when(() -> RedisUtils.hasKey(anyString())).thenReturn(false);
			redis.when(() -> RedisUtils.incrementAndGet(anyString())).thenReturn(1L);
			SmsCodeServiceImpl service = new SmsCodeServiceImpl(sender);

			service.sendCode(MOBILE);

			redis.verify(() -> RedisUtils.deleteObject("sms:fail:" + MOBILE));
		}
	}

	@Test
	@DisplayName("真实短信 provider：返回 null，且下发的码为 6 位数字")
	void sendCode_realProvider_returnsNullAndSixDigitCode() {
		SmsSender sender = mock(SmsSender.class);
		try (MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class)) {
			redis.when(() -> RedisUtils.hasKey(anyString())).thenReturn(false);
			redis.when(() -> RedisUtils.incrementAndGet(anyString())).thenReturn(1L);
			SmsCodeServiceImpl service = new SmsCodeServiceImpl(sender);
			ReflectionTestUtils.setField(service, "provider", "aliyun");

			String returned = service.sendCode(MOBILE);

			// 非 mock 分支：接口不暴露验证码（null 本身即不等于固定码 123456）
			assertNull(returned);
			ArgumentCaptor<Object> codeCaptor = ArgumentCaptor.forClass(Object.class);
			redis.verify(() -> RedisUtils.setCacheObject(eq("sms:code:" + MOBILE), codeCaptor.capture(), eq(300L),
					eq(TimeUnit.SECONDS)));
			assertTrue(((String) codeCaptor.getValue()).matches("\\d{6}"));
		}
	}

	@Test
	@DisplayName("当日第 10 次仍放行，仅第 11 次拒绝")
	void sendCode_atDailyLimitTen_passes() {
		MockSmsSender sender = new MockSmsSender();
		try (MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class)) {
			redis.when(() -> RedisUtils.hasKey(anyString())).thenReturn(false);
			redis.when(() -> RedisUtils.incrementAndGet(anyString())).thenReturn(10L);
			SmsCodeServiceImpl service = new SmsCodeServiceImpl(sender);

			assertEquals("123456", service.sendCode(MOBILE));
		}
	}

	@Test
	@DisplayName("当日计数键缺失 TTL 时自动补设（INCR/EXPIRE 非原子的兜底）")
	void sendCode_restoresMissingDailyKeyTtl() {
		MockSmsSender sender = new MockSmsSender();
		try (MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class)) {
			redis.when(() -> RedisUtils.hasKey(anyString())).thenReturn(false);
			redis.when(() -> RedisUtils.incrementAndGet(anyString())).thenReturn(1L);
			redis.when(() -> RedisUtils.getExpire(DAILY_KEY)).thenReturn(-1L);
			redis.when(() -> RedisUtils.expire(eq(DAILY_KEY), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
			SmsCodeServiceImpl service = new SmsCodeServiceImpl(sender);

			service.sendCode(MOBILE);

			redis.verify(() -> RedisUtils.expire(eq(DAILY_KEY), anyLong(), eq(TimeUnit.SECONDS)));
		}
	}

	@Test
	@DisplayName("当日计数键 TTL 设置失败：删键并抛服务繁忙，避免永久锁死")
	void sendCode_ttlSetupFails_deletesKeyAndThrows() {
		MockSmsSender sender = new MockSmsSender();
		try (MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class)) {
			redis.when(() -> RedisUtils.hasKey(anyString())).thenReturn(false);
			redis.when(() -> RedisUtils.incrementAndGet(anyString())).thenReturn(1L);
			redis.when(() -> RedisUtils.getExpire(DAILY_KEY)).thenReturn(-1L);
			redis.when(() -> RedisUtils.expire(eq(DAILY_KEY), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(false);
			SmsCodeServiceImpl service = new SmsCodeServiceImpl(sender);

			TWTException ex = assertThrows(TWTException.class, () -> service.sendCode(MOBILE));

			assertTrue(ex.getMessage().contains("服务繁忙"));
			redis.verify(() -> RedisUtils.deleteObject(DAILY_KEY));
		}
	}

}
