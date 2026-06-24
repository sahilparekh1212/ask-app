package com.aisandbox.notification.repository;

import com.aisandbox.notification.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	/** Active (not soft-deleted) notifications. */
	List<Notification> findByDeletedFalse();
}
