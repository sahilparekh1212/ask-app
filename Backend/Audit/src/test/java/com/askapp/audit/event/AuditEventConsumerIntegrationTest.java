package com.askapp.audit.event;

import com.askapp.audit.repository.AuditLogRepository;
import com.askapp.common.event.AuditEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end test of the Kafka consume → persist path against an in-JVM broker (no Docker):
 * publish an {@link AuditEvent} to {@code audit.events} and assert the real {@code @KafkaListener}
 * deserializes it and writes an {@code AuditLog}, including idempotent dedup by {@code eventId}.
 *
 * <p>The listener is disabled by default in tests ({@code auto-startup=false}); it is re-enabled
 * here and pointed at the embedded broker. This runs inside the normal {@code test} task, so it is
 * gated by the "Build, test & coverage" CI check.
 */
// The Kafka serializer config is repeated here because src/test/resources/application.properties
// shadows the main application.properties on the test classpath — this keeps the test self-contained
// and mirrors the production contract (JSON value, type headers ignored, fixed target type).
@SpringBootTest(properties = {
	"spring.kafka.listener.auto-startup=true",
	"spring.kafka.consumer.group-id=audit-integration-test",
	"spring.kafka.consumer.auto-offset-reset=earliest",
	"spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
	"spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer",
	"spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
	"spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer",
	"spring.kafka.consumer.properties.spring.json.value.default.type=com.askapp.common.event.AuditEvent",
	"spring.kafka.consumer.properties.spring.json.use.type.headers=false",
	"spring.kafka.consumer.properties.spring.json.trusted.packages=com.askapp.common.event"
})
@EmbeddedKafka(partitions = 1, topics = "audit.events", bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class AuditEventConsumerIntegrationTest {

	@Autowired
	private KafkaTemplate<String, AuditEvent> kafkaTemplate;

	@Autowired
	private AuditLogRepository repository;

	@BeforeEach
	void reset() {
		repository.deleteAll();
	}

	@Test
	void publishedEventIsConsumedAndPersistedAsAnAuditLog() {
		AuditEvent event = new AuditEvent("it-evt-1", "User", "LOGIN", "user-1", Instant.now());

		kafkaTemplate.send("audit.events", event.entityType(), event);

		await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
			assertThat(repository.existsByEventId("it-evt-1")).isTrue());
		assertThat(repository.findByDeletedFalse()).singleElement().satisfies(row -> {
			assertThat(row.getEntityType()).isEqualTo("User");
			assertThat(row.getAction()).isEqualTo("LOGIN");
			assertThat(row.getDetails()).isEqualTo("user-1");
			assertThat(row.getEventId()).isEqualTo("it-evt-1");
		});
	}

	@Test
	void duplicateEventIsDeduplicatedByEventId() {
		AuditEvent duplicate = new AuditEvent("it-dup", "User", "LOGIN", "user-1", Instant.now());
		AuditEvent marker = new AuditEvent("it-marker", "User", "LOGOUT", "user-1", Instant.now());

		// Same key => same partition => processed in order. When the marker lands, both copies of
		// the duplicate have already been handled, so dedup must have left exactly one of them.
		kafkaTemplate.send("audit.events", "User", duplicate);
		kafkaTemplate.send("audit.events", "User", duplicate);
		kafkaTemplate.send("audit.events", "User", marker);

		await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
			assertThat(repository.existsByEventId("it-marker")).isTrue());
		assertThat(repository.count()).isEqualTo(2L); // one duplicate row + the marker
	}
}
