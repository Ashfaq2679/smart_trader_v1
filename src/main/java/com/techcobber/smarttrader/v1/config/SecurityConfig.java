package com.techcobber.smarttrader.v1.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * OAuth 2.0 Resource Server security configuration.
 *
 * <p>All {@code /api/**} endpoints require a valid JWT Bearer token issued by the
 * configured authorization server. The application validates tokens using the
 * JSON Web Key Set (JWKS) URI specified in
 * {@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri}.</p>
 *
 * <p>Sessions are stateless — every request must carry its own JWT in the
 * {@code Authorization: Bearer <token>} header.</p>
 *
 * <p>The actuator health endpoint ({@code /actuator/health}) is permitted
 * without authentication to support container health checks.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			// CSRF protection is disabled because this is a stateless REST API
			// that uses Bearer JWT tokens (not cookies) for authentication.
			// Bearer tokens are not automatically attached by browsers, so they
			// are not susceptible to CSRF attacks.
			.csrf(csrf -> csrf.disable()) // lgtm[java/spring-disabled-csrf-protection]
			.sessionManagement(session ->
				session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
				.requestMatchers("/api/**").authenticated()
				.anyRequest().denyAll())
			.oauth2ResourceServer(oauth2 ->
				oauth2.jwt(jwt -> {
					// Uses default JWT decoder configured via
					// spring.security.oauth2.resourceserver.jwt.jwk-set-uri
				}));

		return http.build();
	}
}
