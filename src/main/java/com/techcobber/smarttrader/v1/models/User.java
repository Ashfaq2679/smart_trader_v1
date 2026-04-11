package com.techcobber.smarttrader.v1.models;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

/**
 * MongoDB document representing a registered user of the platform.
 *
 * <p>Each user is uniquely identified by {@code userId}. Users must be registered
 * before they can store credentials or preferences.</p>
 */
@Data
@Document("users")
public class User {

	@Id
	private String id;

	@Indexed(unique = true)
	private String userId;

	private String email;

	private String displayName;

	private boolean enabled;

	private Instant createdAt;

	private Instant updatedAt;
}
