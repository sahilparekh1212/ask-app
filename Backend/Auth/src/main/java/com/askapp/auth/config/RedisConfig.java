package com.askapp.auth.config;

import com.askapp.auth.service.RefreshTokenEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis wiring for the refresh-token store — only created when {@code auth.refresh-token.store=redis}
 * so the plain in-memory deployment never touches Redis (Spring Boot's Redis auto-config connects
 * lazily, so its presence on the classpath is harmless when this store is off).
 *
 * <p>Values are stored as typed JSON: {@link JavaTimeModule} is registered so the entry's
 * {@code Instant expiresAt} serializes cleanly, and the serializer is bound to
 * {@link RefreshTokenEntry} directly (no polymorphic {@code @class} type header needed).
 */
@Configuration
@ConditionalOnProperty(name = "auth.refresh-token.store", havingValue = "redis")
public class RedisConfig {

	@Bean
	public RedisTemplate<String, RefreshTokenEntry> refreshTokenRedisTemplate(RedisConnectionFactory connectionFactory) {
		ObjectMapper mapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();
		RedisTemplate<String, RefreshTokenEntry> template = new RedisTemplate<>();
		template.setConnectionFactory(connectionFactory);
		template.setKeySerializer(new StringRedisSerializer());
		template.setValueSerializer(new Jackson2JsonRedisSerializer<>(mapper, RefreshTokenEntry.class));
		template.afterPropertiesSet();
		return template;
	}
}
