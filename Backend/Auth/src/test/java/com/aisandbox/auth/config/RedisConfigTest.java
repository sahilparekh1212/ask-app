package com.aisandbox.auth.config;

import com.aisandbox.auth.service.RefreshTokenEntry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RedisConfigTest {

	@Test
	void refreshTokenRedisTemplate_wiresStringKeysAndJsonValues() {
		RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);

		RedisTemplate<String, RefreshTokenEntry> template =
			new RedisConfig().refreshTokenRedisTemplate(connectionFactory);

		assertThat(template.getConnectionFactory()).isSameAs(connectionFactory);
		assertThat(template.getKeySerializer()).isInstanceOf(StringRedisSerializer.class);
		assertThat(template.getValueSerializer()).isInstanceOf(Jackson2JsonRedisSerializer.class);
	}
}
