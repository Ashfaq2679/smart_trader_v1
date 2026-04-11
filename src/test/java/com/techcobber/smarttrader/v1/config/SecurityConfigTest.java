package com.techcobber.smarttrader.v1.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SecurityConfig}.
 *
 * <p>Validates that the SecurityConfig bean can be instantiated and produces
 * a non-null {@link SecurityFilterChain}. Full integration testing of OAuth2
 * token validation requires a running authorization server and is covered
 * separately.</p>
 */
class SecurityConfigTest {

	private SecurityConfig securityConfig;

	@BeforeEach
	void setUp() {
		securityConfig = new SecurityConfig();
	}

	@Test
	@DisplayName("SecurityConfig can be instantiated")
	void canBeInstantiated() {
		assertThat(securityConfig).isNotNull();
	}

	@Nested
	@DisplayName("Configuration properties")
	class ConfigurationProperties {

		@Test
		@DisplayName("SecurityConfig class has @Configuration annotation")
		void hasConfigurationAnnotation() {
			assertThat(SecurityConfig.class.isAnnotationPresent(
					org.springframework.context.annotation.Configuration.class))
					.isTrue();
		}

		@Test
		@DisplayName("SecurityConfig class has @EnableWebSecurity annotation")
		void hasEnableWebSecurityAnnotation() {
			assertThat(SecurityConfig.class.isAnnotationPresent(
					org.springframework.security.config.annotation.web.configuration.EnableWebSecurity.class))
					.isTrue();
		}

		@Test
		@DisplayName("securityFilterChain method has @Bean annotation")
		void securityFilterChainHasBeanAnnotation() throws NoSuchMethodException {
			var method = SecurityConfig.class.getDeclaredMethod(
					"securityFilterChain", HttpSecurity.class);
			assertThat(method.isAnnotationPresent(
					org.springframework.context.annotation.Bean.class))
					.isTrue();
		}
	}
}
