package com.twelvet.framework.configure;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.twelvet.framework.constants.CacheConstants;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * @author twelvet
 * @Description: redis使用jackson序列化
 */
@SuppressWarnings("all")
@Configuration
public class RedisConfig implements CachingConfigurer {

	/**
	 * 配置缓存信息
	 * @param redisConnectionFactory RedisConnectionFactory
	 * @return CacheManager
	 */
	@Bean
	public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
		return new RedisCacheManager(RedisCacheWriter.nonLockingRedisCacheWriter(redisConnectionFactory),
				// 默认策略，未配置的 key 会使用这个
				this.getRedisCacheConfigurationWithTtl(600),
				// 指定 key 策略
				this.getRedisCacheConfigurationMap());
	}

	private Map<String, RedisCacheConfiguration> getRedisCacheConfigurationMap() {
		Map<String, RedisCacheConfiguration> redisCacheConfigurationMap = new HashMap<>();

		// 字典默认10分钟
		redisCacheConfigurationMap.put(CacheConstants.SYS_DICT_KEY, this.getRedisCacheConfigurationWithTtl(60 * 10));

		return redisCacheConfigurationMap;
	}

	private RedisCacheConfiguration getRedisCacheConfigurationWithTtl(int seconds) {
		Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer<>(
				Object.class);
		jackson2JsonRedisSerializer.setObjectMapper(cacheObjectMapper());

		RedisCacheConfiguration redisCacheConfiguration = RedisCacheConfiguration.defaultCacheConfig()
			// 缓存前缀
			.prefixCacheNameWith("fast:")
			// 修改为一个 :
			// .computePrefixWith(name -> name + ":")
			// 过期时间
			.entryTtl(Duration.ofSeconds(seconds));

		return redisCacheConfiguration;
	}

	/**
	 * Spring Cache（@Cacheable）值序列化用的 ObjectMapper（静态工厂，便于测试锁定构造）。
	 * <p>
	 * 2026-11-03 修复：注册 JavaTimeModule——@Cacheable(SYS_DICT_KEY) 缓存的 List&lt;SysDictData&gt;
	 * 含 LocalDateTime，此前写入即抛 InvalidDefinitionException（字典缓存静默失效）。
	 * defaultTyping 语义保持不变。
	 * </p>
	 */
	public static ObjectMapper cacheObjectMapper() {
		ObjectMapper om = new ObjectMapper();
		om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
		om.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL);
		om.registerModule(new JavaTimeModule());
		return om;
	}

}
