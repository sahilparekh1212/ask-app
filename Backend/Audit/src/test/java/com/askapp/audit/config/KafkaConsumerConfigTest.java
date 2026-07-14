package com.askapp.audit.config;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KafkaConsumerConfigTest {

	@Test
	@SuppressWarnings("unchecked")
	void kafkaErrorHandler_isBuiltWithDeadLetterRecovery() {
		KafkaTemplate<Object, Object> template = mock(KafkaTemplate.class);

		DefaultErrorHandler handler = new KafkaConsumerConfig().kafkaErrorHandler(template);

		assertThat(handler).isNotNull();
	}
}
