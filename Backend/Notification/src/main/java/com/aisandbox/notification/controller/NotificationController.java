package com.aisandbox.notification.controller;

import com.aisandbox.notification.exception.ResourceNotFoundException;
import com.aisandbox.notification.model.Notification;
import com.aisandbox.notification.ratelimit.TransactionalRequestExecutor;
import com.aisandbox.notification.repository.NotificationRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "Notification CRUD operations")
public class NotificationController {

	private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

	private final NotificationRepository notificationRepository;
	// Wraps mutations so a superseding request rolls the work back transactionally.
	private final TransactionalRequestExecutor txExecutor;

	public NotificationController(NotificationRepository notificationRepository, TransactionalRequestExecutor txExecutor) {
		this.notificationRepository = notificationRepository;
		this.txExecutor = txExecutor;
	}

	@GetMapping
	@Operation(summary = "List notifications (excludes soft-deleted unless includeDeleted=true)")
	public List<Notification> findAll(@RequestParam(defaultValue = "false") boolean includeDeleted) {
		log.info("Fetching notifications includeDeleted={}", includeDeleted);
		return includeDeleted ? notificationRepository.findAll() : notificationRepository.findByDeletedFalse();
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get notification by ID")
	public Notification findById(@PathVariable Long id) {
		log.info("Fetching notification id={}", id);
		return notificationRepository.findById(id)
			.orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + id));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Create a new notification")
	public Notification create(@RequestBody Notification notification) {
		Notification saved = txExecutor.run(() -> notificationRepository.save(notification));
		log.info("Created notification id={}", saved.getId());
		return saved;
	}

	@PutMapping("/{id}")
	@Operation(summary = "Update a notification (e.g. mark as read)")
	public Notification update(@PathVariable Long id, @RequestBody Notification payload) {
		Notification saved = txExecutor.run(() -> {
			Notification notification = notificationRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + id));
			notification.setRecipientId(payload.getRecipientId());
			notification.setChannel(payload.getChannel());
			notification.setMessage(payload.getMessage());
			notification.setRead(payload.isRead());
			return notificationRepository.save(notification);
		});
		log.info("Updated notification id={} read={}", id, saved.isRead());
		return saved;
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Delete a notification")
	public void delete(@PathVariable Long id) {
		txExecutor.run(() -> {
			Notification notification = notificationRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + id));
			notification.setDeleted(true);
			notificationRepository.save(notification);
			return null;
		});
		log.info("Soft-deleted notification id={}", id);
	}

	@GetMapping("/health")
	@Operation(summary = "Health check")
	public ResponseEntity<String> health() {
		return ResponseEntity.ok("notification-service OK");
	}

}
