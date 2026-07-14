package com.askapp.audit.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Consumer resilience for the audit-event listener. A record that keeps failing is retried a
 * few times and then routed to the dead-letter topic {@code audit.events.DLT}, so one poison
 * message can't stall the partition. Spring Boot auto-wires this single {@link DefaultErrorHandler}
 * bean into the Kafka listener container.
 */
@Configuration
public class KafkaConsumerConfig {

	private static final long RETRY_INTERVAL_MS = 1000L;
	private static final long MAX_RETRIES = 3L;

	@Bean
	public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
		DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
		return new DefaultErrorHandler(recoverer, new FixedBackOff(RETRY_INTERVAL_MS, MAX_RETRIES));
	}
}
