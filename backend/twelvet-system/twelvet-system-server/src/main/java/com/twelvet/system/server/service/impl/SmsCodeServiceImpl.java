package com.twelvet.system.server.service.impl;

import com.twelvet.framework.RedisUtils;
import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.system.server.service.ISmsCodeService;
import com.twelvet.system.server.sms.SmsSender;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 短信验证码服务实现：验证码存 Redis，附带发送间隔、当日次数、校验失败次数三重频控。
 * <ul>
 * <li>发送间隔 60 秒：{@code sms:send:limit:{mobile}}</li>
 * <li>当日上限 10 次：{@code sms:daily:{mobile}:{yyyyMMdd}}，首次自增后补设 TTL 至次日零点</li>
 * <li>验证码 300 秒有效：{@code sms:code:{mobile}}</li>
 * <li>失败计数 5 次作废：{@code sms:fail:{mobile}}</li>
 * </ul>
 *
 * @author twelvet
 */
@Service
public class SmsCodeServiceImpl implements ISmsCodeService {

	private static final Logger log = LoggerFactory.getLogger(SmsCodeServiceImpl.class);

	/** 验证码有效期（秒） */
	private static final int CODE_EXPIRE_SECONDS = 300;

	/** 同一手机号发送间隔（秒） */
	private static final int SEND_INTERVAL_SECONDS = 60;

	/** 同一手机号每日发送上限（次） */
	private static final int DAILY_LIMIT = 10;

	/** 单次验证码允许的最大校验失败次数 */
	private static final int MAX_VERIFY_ATTEMPTS = 5;

	/** mock 模式下的固定验证码 */
	private static final String MOCK_CODE = "123456";

	private static final String MOCK_PROVIDER = "mock";

	private static final String CODE_KEY_PREFIX = "sms:code:";

	private static final String SEND_LIMIT_KEY_PREFIX = "sms:send:limit:";

	private static final String DAILY_KEY_PREFIX = "sms:daily:";

	private static final String FAIL_KEY_PREFIX = "sms:fail:";

	private static final Pattern MOBILE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

	private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

	private static final SecureRandom RANDOM = new SecureRandom();

	private final SmsSender smsSender;

	/**
	 * 短信服务商：mock = 固定验证码并回传给前端；其余值走真实短信（返回 null）
	 */
	@Value("${sms.provider:mock}")
	private String provider;

	@Autowired
	private Environment environment;

	public SmsCodeServiceImpl(SmsSender smsSender) {
		this.smsSender = smsSender;
	}

	/**
	 * 启动自检：mock 短信器 + 生产 profile 是严重配置错误，必须显式告警（不阻断启动，便于先起服务再改配置）
	 */
	@PostConstruct
	public void warnOnMockProviderInProduction() {
		if (environment == null || !isMockProvider()) {
			return;
		}
		for (String profile : environment.getActiveProfiles()) {
			if (profile != null && profile.trim().toLowerCase(Locale.ROOT).startsWith("pro")) {
				log.error("【安全告警】sms.provider=mock 且 pro profile 激活：任意手机号可用固定验证码登录，生产环境必须切换真实短信服务商");
				return;
			}
		}
	}

	/**
	 * 发送验证码
	 * @param mobile 手机号
	 * @return 调试用验证码（mock 模式返回值，真实短信返回 null）
	 */
	@Override
	public String sendCode(String mobile) {
		if (mobile == null || !MOBILE_PATTERN.matcher(mobile).matches()) {
			throw new TWTException("手机号格式不正确");
		}
		String limitKey = SEND_LIMIT_KEY_PREFIX + mobile;
		if (Boolean.TRUE.equals(RedisUtils.hasKey(limitKey))) {
			throw new TWTException("请求过于频繁，请稍后再试");
		}
		String dailyKey = dailyKey(mobile);
		// 预检查只读计数：本次发送尚未成功，不能先消耗配额
		if (currentDailyCount(dailyKey) >= DAILY_LIMIT) {
			throw new TWTException("该手机号今日发送次数已达上限");
		}
		boolean mock = isMockProvider();
		String code = mock ? MOCK_CODE : randomCode();
		// 先发送后落库：短信网关失败时用户没收到码，不能反而被 60 秒限流锁住、也不能消耗当日配额
		smsSender.send(mobile, code);
		RedisUtils.setCacheObject(CODE_KEY_PREFIX + mobile, code, CODE_EXPIRE_SECONDS, TimeUnit.SECONDS);
		// 新码下发即清零旧失败计数：计数键跨「验证码代次」共享，若不清零，
		// 用户对旧码错了 4 次后取新码，只错 1 次就累计到 5 → 新码连带作废，
		// 正是 verify 里「残留计数会让重新取码后一输错就被锁死」要避免的情形。
		RedisUtils.deleteObject(FAIL_KEY_PREFIX + mobile);
		RedisUtils.setCacheObject(limitKey, "1", SEND_INTERVAL_SECONDS, TimeUnit.SECONDS);
		long count = RedisUtils.incrementAndGet(dailyKey);
		// INCR 与 EXPIRE 非原子：补设 TTL，避免无过期时间的计数键把该手机号永久顶在上限
		ensureDailyKeyTtl(dailyKey);
		// 兜底：预检查与自增之间的并发窗口，最多多发一条
		if (count > DAILY_LIMIT) {
			throw new TWTException("该手机号今日发送次数已达上限");
		}
		return mock ? code : null;
	}

	/**
	 * 校验验证码，失败抛 TWTException
	 * @param mobile 手机号
	 * @param code 用户输入的验证码
	 */
	@Override
	public void verify(String mobile, String code) {
		String codeKey = CODE_KEY_PREFIX + mobile;
		String cached = RedisUtils.getCacheObject(codeKey);
		if (cached == null) {
			throw new TWTException("验证码已失效，请重新获取");
		}
		if (!cached.equals(code)) {
			String failKey = FAIL_KEY_PREFIX + mobile;
			long fails = RedisUtils.incrementAndGet(failKey);
			if (fails == 1) {
				RedisUtils.expire(failKey, CODE_EXPIRE_SECONDS, TimeUnit.SECONDS);
			}
			if (fails >= MAX_VERIFY_ATTEMPTS) {
				// 失败计数与验证码一并作废：否则残留计数会让用户重新取码后一输错就被直接锁死
				RedisUtils.deleteObject(codeKey);
				RedisUtils.deleteObject(failKey);
				throw new TWTException("验证码错误次数过多，请重新获取");
			}
			throw new TWTException("验证码不正确");
		}
		RedisUtils.deleteObject(codeKey);
		RedisUtils.deleteObject(FAIL_KEY_PREFIX + mobile);
	}

	/**
	 * 验证码有效期（秒）
	 * @return 验证码有效期，单位为秒
	 */
	@Override
	public int codeExpireSeconds() {
		return CODE_EXPIRE_SECONDS;
	}

	/**
	 * 是否 mock 模式（未配置时按 mock 处理，与 {@code sms.provider:mock} 的默认值一致）
	 */
	private boolean isMockProvider() {
		return provider == null || MOCK_PROVIDER.equalsIgnoreCase(provider);
	}

	/**
	 * 当日计数键：{@code sms:daily:{mobile}:{yyyyMMdd}}
	 */
	private String dailyKey(String mobile) {
		return DAILY_KEY_PREFIX + mobile + ":" + LocalDate.now().format(DAY_FORMATTER);
	}

	/**
	 * 读取当日已发送次数（只读，用于发送前预检查）
	 */
	private long currentDailyCount(String dailyKey) {
		Object cached = RedisUtils.getCacheObject(dailyKey);
		if (cached instanceof Number number) {
			return number.longValue();
		}
		if (cached instanceof String text) {
			try {
				return Long.parseLong(text.trim());
			}
			catch (NumberFormatException ignored) {
				return 0L;
			}
		}
		return 0L;
	}

	/**
	 * 确保当日计数键带过期时间
	 * <p>
	 * {@code INCR} 与 {@code EXPIRE} 不是原子操作：若设置 TTL 失败，键会永久留驻，该手机号将永久触顶。
	 * 因此设置失败时重试一次，仍失败则删键并拒绝本次请求——宁可这一次发送失败，也不能永久锁死。
	 * </p>
	 * @param dailyKey 当日计数键
	 */
	private void ensureDailyKeyTtl(String dailyKey) {
		if (RedisUtils.getExpire(dailyKey) >= 0) {
			return;
		}
		long ttl = secondsToMidnight();
		if (RedisUtils.expire(dailyKey, ttl, TimeUnit.SECONDS) || RedisUtils.expire(dailyKey, ttl, TimeUnit.SECONDS)) {
			return;
		}
		RedisUtils.deleteObject(dailyKey);
		throw new TWTException("服务繁忙，请稍后重试");
	}

	/**
	 * 生成 6 位随机验证码
	 */
	private String randomCode() {
		return String.format("%06d", RANDOM.nextInt(1000000));
	}

	/**
	 * 距离次日零点的秒数，用于当日计数键的 TTL
	 */
	private long secondsToMidnight() {
		LocalDateTime now = LocalDateTime.now();
		LocalDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay();
		return Math.max(Duration.between(now, midnight).getSeconds(), 1L);
	}

}
