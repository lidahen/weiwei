package com.twelvet.framework.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Redis 序列化 jsr310 修复测试（2026-11-03 批次1）
 * <p>
 * 锁定不变量：含 LocalDateTime 的实体可经 RedisTemplate（GenericJackson2JsonRedisSerializer）
 * 与 CacheManager（Jackson2JsonRedisSerializer）两层序列化/反序列化往返；值格式保持类型元数据。
 * 生产中受影响实体是 SysDictData（createTime 在 BaseEntity，twelvet-framework-core）——
 * framework-redis 不依赖 core，测试用同构 POJO（含 LocalDateTime 字段）替代，语义等价。
 * </p>
 */
class SerializerJsr310Test {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 3, 10, 30, 0);

	/** 与 SysDictData 同构的测试夹具（含 LocalDateTime 字段的实体） */
	public static class DictLike {

		private Long dictCode;

		private String dictLabel;

		private LocalDateTime createTime;

		public Long getDictCode() {
			return dictCode;
		}

		public void setDictCode(Long dictCode) {
			this.dictCode = dictCode;
		}

		public String getDictLabel() {
			return dictLabel;
		}

		public void setDictLabel(String dictLabel) {
			this.dictLabel = dictLabel;
		}

		public LocalDateTime getCreateTime() {
			return createTime;
		}

		public void setCreateTime(LocalDateTime createTime) {
			this.createTime = createTime;
		}

	}

	private DictLike dictData() {
		DictLike d = new DictLike();
		d.setDictCode(1L);
		d.setDictLabel("正常");
		d.setCreateTime(NOW);
		return d;
	}

	@Test
	@DisplayName("RedisTemplate 层：LocalDateTime 往返（锁定生产 ObjectMapper 构造）")
	void genericSerializer_localDateTimeRoundTrip() {
		// 生产构造：RedisTemplateConfiguration.jacksonRedisObjectMapper()
		ObjectMapper om = com.twelvet.framework.configure.RedisTemplateConfiguration.jacksonRedisObjectMapper();
		GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(om);

		byte[] bytes = serializer.serialize(dictData());

		Object restored = assertDoesNotThrow(() -> serializer.deserialize(bytes));
		assertTrue(restored instanceof DictLike);
		DictLike back = (DictLike) restored;
		assertEquals(1L, back.getDictCode());
		assertEquals("正常", back.getDictLabel());
		assertEquals(NOW, back.getCreateTime());
	}

	@Test
	@DisplayName("CacheManager 层：LocalDateTime 往返（锁定生产 ObjectMapper 构造）")
	void cacheSerializer_localDateTimeRoundTrip() {
		// 生产构造：RedisConfig.cacheObjectMapper()
		ObjectMapper om = com.twelvet.framework.configure.RedisConfig.cacheObjectMapper();
		Jackson2JsonRedisSerializer<Object> serializer = new Jackson2JsonRedisSerializer<>(Object.class);
		serializer.setObjectMapper(om);

		byte[] bytes = serializer.serialize(dictData());

		Object restored = assertDoesNotThrow(() -> serializer.deserialize(bytes));
		assertTrue(restored instanceof DictLike);
		DictLike back = (DictLike) restored;
		assertEquals(1L, back.getDictCode());
		assertEquals("正常", back.getDictLabel());
		assertEquals(NOW, back.getCreateTime());
	}

}
