package com.aisandbox.notification.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import io.swagger.v3.oas.annotations.media.Schema;

@Entity
@Table(name = "notifications")
public class Notification extends AuditableEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Schema(accessMode = Schema.AccessMode.READ_ONLY)
	private Long id;

	private Long recipientId;

	private String channel;

	private String message;

	private boolean read;

	@Schema(accessMode = Schema.AccessMode.READ_ONLY)
	private boolean deleted = false;

	protected Notification() {
	}

	public Notification(Long recipientId, String channel, String message) {
		this.recipientId = recipientId;
		this.channel = channel;
		this.message = message;
		this.read = false;
	}

	public Long getId() {
		return id;
	}

	public Long getRecipientId() {
		return recipientId;
	}

	public void setRecipientId(Long recipientId) {
		this.recipientId = recipientId;
	}

	public String getChannel() {
		return channel;
	}

	public void setChannel(String channel) {
		this.channel = channel;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public boolean isRead() {
		return read;
	}

	public void setRead(boolean read) {
		this.read = read;
	}

	public boolean isDeleted() {
		return deleted;
	}

	public void setDeleted(boolean deleted) {
		this.deleted = deleted;
	}

}
