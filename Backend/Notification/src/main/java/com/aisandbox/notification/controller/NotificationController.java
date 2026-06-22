package com.aisandbox.notification.controller;

import com.aisandbox.notification.model.Notification;
import com.aisandbox.notification.repository.NotificationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

	private final NotificationRepository notificationRepository;

	public NotificationController(NotificationRepository notificationRepository) {
		this.notificationRepository = notificationRepository;
	}

	@GetMapping
	public List<Notification> findAll() {
		return notificationRepository.findAll();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Notification create(@RequestBody Notification notification) {
		return notificationRepository.save(notification);
	}

}
