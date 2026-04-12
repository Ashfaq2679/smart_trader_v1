package com.techcobber.smarttrader.v1.models;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * MongoDB document representing a registered user of the platform.
 *
 * <p>Each user is uniquely identified by {@code userId}, which is mapped
 * directly to MongoDB's {@code _id} field via {@link Id @Id}.</p>
 */
@Data
@Document("users")
public class User {

	@Id
	@NotBlank(message = "userId must not be blank")
	private String userId;

	@Email(message = "email must be a valid email address")
	private String email;

	private String displayName;

	private boolean enabled;

	private Instant createdAt;

	private Instant updatedAt;
}
