package com.techcobber.smarttrader.v1.exceptions;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Unit tests for {@link GlobalExceptionHandler}.
 */
class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	// =======================================================================
	// MethodArgumentNotValidException (bean validation)
	// =======================================================================

	@Nested
	@DisplayName("handleValidationErrors")
	class ValidationErrorTests {

		@Test
		@DisplayName("Returns 400 with field-level error details")
		void returnsBadRequestWithFieldErrors() throws Exception {
			BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "orderRequest");
			bindingResult.addError(new FieldError("orderRequest", "productId", "productId is required"));
			bindingResult.addError(new FieldError("orderRequest", "side", "side is required"));

			MethodParameter param = new MethodParameter(
					GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyMethod", String.class), 0);
			MethodArgumentNotValidException ex = new MethodArgumentNotValidException(param, bindingResult);

			ResponseEntity<Map<String, Object>> response = handler.handleValidationErrors(ex);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
			Map<String, Object> body = response.getBody();
			assertThat(body).isNotNull();
			assertThat(body.get("status")).isEqualTo(400);
			assertThat(body.get("error")).isEqualTo("Bad Request");
			assertThat((String) body.get("message")).contains("productId").contains("side");
			assertThat(body).containsKey("timestamp");
		}

		@Test
		@DisplayName("Returns single field error message")
		void returnsSingleFieldError() throws Exception {
			BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "user");
			bindingResult.addError(new FieldError("user", "email", "email must be a valid email address"));

			MethodParameter param = new MethodParameter(
					GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyMethod", String.class), 0);
			MethodArgumentNotValidException ex = new MethodArgumentNotValidException(param, bindingResult);

			ResponseEntity<Map<String, Object>> response = handler.handleValidationErrors(ex);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
			assertThat((String) response.getBody().get("message"))
					.isEqualTo("email: email must be a valid email address");
		}
	}

	// =======================================================================
	// HttpMessageNotReadableException (malformed JSON)
	// =======================================================================

	@Nested
	@DisplayName("handleUnreadableMessage")
	class UnreadableMessageTests {

		@Test
		@DisplayName("Returns 400 for malformed JSON")
		void returnsBadRequestForMalformedJson() {
			HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
					"JSON parse error", new MockHttpInputMessage(new byte[0]));

			ResponseEntity<Map<String, Object>> response = handler.handleUnreadableMessage(ex);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
			assertThat(response.getBody().get("message")).isEqualTo("Malformed JSON request body");
		}
	}

	// =======================================================================
	// MissingServletRequestParameterException
	// =======================================================================

	@Nested
	@DisplayName("handleMissingParams")
	class MissingParamsTests {

		@Test
		@DisplayName("Returns 400 with missing parameter name")
		void returnsBadRequestWithParamName() {
			MissingServletRequestParameterException ex =
					new MissingServletRequestParameterException("userId", "String");

			ResponseEntity<Map<String, Object>> response = handler.handleMissingParams(ex);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
			assertThat((String) response.getBody().get("message")).contains("userId");
		}
	}

	// =======================================================================
	// MethodArgumentTypeMismatchException
	// =======================================================================

	@Nested
	@DisplayName("handleTypeMismatch")
	class TypeMismatchTests {

		@Test
		@DisplayName("Returns 400 with parameter name and expected type")
		void returnsBadRequestWithTypeInfo() {
			MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
					"abc", Integer.class, "limit", null, new NumberFormatException("For input string: abc"));

			ResponseEntity<Map<String, Object>> response = handler.handleTypeMismatch(ex);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
			assertThat((String) response.getBody().get("message"))
					.contains("limit").contains("Integer");
		}
	}

	// =======================================================================
	// IllegalArgumentException
	// =======================================================================

	@Nested
	@DisplayName("handleIllegalArgument")
	class IllegalArgumentTests {

		@Test
		@DisplayName("Returns 400 with exception message")
		void returnsBadRequestWithMessage() {
			IllegalArgumentException ex = new IllegalArgumentException("productId is required");

			ResponseEntity<Map<String, Object>> response = handler.handleIllegalArgument(ex);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
			assertThat(response.getBody().get("message")).isEqualTo("productId is required");
		}
	}

	// =======================================================================
	// Generic Exception (catch-all)
	// =======================================================================

	@Nested
	@DisplayName("handleGenericException")
	class GenericExceptionTests {

		@Test
		@DisplayName("Returns 500 with generic error message")
		void returnsInternalServerError() {
			Exception ex = new RuntimeException("Something went wrong");

			ResponseEntity<Map<String, Object>> response = handler.handleGenericException(ex);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
			assertThat(response.getBody().get("message")).isEqualTo("An unexpected error occurred");
			assertThat(response.getBody().get("status")).isEqualTo(500);
		}

		@Test
		@DisplayName("Does not leak internal exception details")
		void doesNotLeakDetails() {
			Exception ex = new NullPointerException("secret internal detail");

			ResponseEntity<Map<String, Object>> response = handler.handleGenericException(ex);

			assertThat((String) response.getBody().get("message")).doesNotContain("secret");
		}
	}

	// =======================================================================
	// Response structure
	// =======================================================================

	@Nested
	@DisplayName("Response structure")
	class ResponseStructureTests {

		@Test
		@DisplayName("All error responses contain standard fields")
		void containsStandardFields() {
			IllegalArgumentException ex = new IllegalArgumentException("test");

			ResponseEntity<Map<String, Object>> response = handler.handleIllegalArgument(ex);

			Map<String, Object> body = response.getBody();
			assertThat(body).containsKeys("timestamp", "status", "error", "message");
		}

		@Test
		@DisplayName("Timestamp is present and non-null")
		void timestampIsPresent() {
			ResponseEntity<Map<String, Object>> response =
					handler.handleGenericException(new RuntimeException("test"));

			assertThat(response.getBody().get("timestamp")).isNotNull();
		}
	}

	// Helper method used to create MethodParameter for MethodArgumentNotValidException
	@SuppressWarnings("unused")
	private void dummyMethod(String arg) {
		// used only for MethodParameter reflection
	}
}
