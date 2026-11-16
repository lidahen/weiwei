package com.twelvet.framework.configure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * @author twelvet
 * @Description: Redis 配置类
 */
@EnableCaching
@AutoConfiguration
@AutoConfigureBefore(RedisAutoConfiguration.class)
public class RedisTemplateConfiguration {

	/**
	 * 生产 RedisTemplate 值序列化用的 ObjectMapper（静态工厂，便于测试锁定构造）。
	 * <p>
	 * 2026-11-03 修复：注册 JavaTimeModule——此前含 LocalDateTime 的实体（SysDictData 等，
	 * createTime 在 BaseEntity）写入即抛 InvalidDefinitionException 且静默丢失（i18n 初始化
	 * 在线程池里，无任何告警）。类型元数据（@class）语义保持不变。
	 * </p>
	 * <p>
	 * 边界（final review 发现）：无参默认构造会注册 NullValueSerializer（null 值经
	 * RedisTemplate 写入时的 @class 哨兵），自定义 mapper 不会。NullValueSerializer 为
	 * 包私有无法直接注册；当前仓库无经 RedisUtils 缓存 null 的调用方（Spring Cache 的 null
	 * 走 CacheManager 路径不受影响）——若未来需要缓存 null，须在此补注册。
	 * </p>
	 */
	public static ObjectMapper jacksonRedisObjectMapper() {
		ObjectMapper om = new ObjectMapper();
		om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
		om.activateDefaultTyping(LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL,
				JsonTypeInfo.As.PROPERTY);
		om.registerModule(new JavaTimeModule());
		return om;
	}

	@Bean
	public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
		RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
		redisTemplate.setKeySerializer(RedisSerializer.string());
		redisTemplate.setHashKeySerializer(RedisSerializer.string());
		redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer(jacksonRedisObjectMapper()));
		redisTemplate.setHashValueSerializer(new GenericJackson2JsonRedisSerializer(jacksonRedisObjectMapper()));
		redisTemplate.setConnectionFactory(factory);
		return redisTemplate;
	}

	@Bean
	public HashOperations<String, String, Object> hashOperations(RedisTemplate<String, Object> redisTemplate) {
		return redisTemplate.opsForHash();
	}

	@Bean
	public ValueOperations<String, String> valueOperations(RedisTemplate<String, String> redisTemplate) {
		return redisTemplate.opsForValue();
	}

	@Bean
	public ListOperations<String, Object> listOperations(RedisTemplate<String, Object> redisTemplate) {
		return redisTemplate.opsForList();
	}

	@Bean
	public SetOperations<String, Object> setOperations(RedisTemplate<String, Object> redisTemplate) {
		return redisTemplate.opsForSet();
	}

	@Bean
	public ZSetOperations<String, Object> zSetOperations(RedisTemplate<String, Object> redisTemplate) {
		return redisTemplate.opsForZSet();
	}

}
