package com.techcobber.smarttrader.v1.exceptions;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import lombok.extern.slf4j.Slf4j;

/**
 * Centralised exception handler for all REST controllers.
 *
 * <p>Translates common exceptions into a consistent JSON error response with
 * the shape {@code {"timestamp", "status", "error", "message"}}.</p>
 *
 * <p>Controllers may still use their own {@code try-catch} blocks for
 * business-specific error mapping. This handler acts as a safety net for
 * exceptions that are not caught at the controller level, and as the
 * primary handler for bean-validation errors triggered by {@code @Valid}.</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	/**
	 * Handles bean-validation failures triggered by {@code @Valid} on request bodies.
	 *
	 * @return 400 with per-field error details
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex) {
		String fieldErrors = ex.getBindingResult().getFieldErrors().stream()
				.map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
				.collect(Collectors.joining(", "));

		log.warn("Validation failed: {}", fieldErrors);
		return buildResponse(HttpStatus.BAD_REQUEST, fieldErrors);
	}

	/**
	 * Handles malformed or unreadable JSON request bodies.
	 *
	 * @return 400 with a descriptive message
	 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<Map<String, Object>> handleUnreadableMessage(HttpMessageNotReadableException ex) {
		log.warn("Malformed request body: {}", ex.getMessage());
		return buildResponse(HttpStatus.BAD_REQUEST, "Malformed JSON request body");
	}

	/**
	 * Handles missing required request parameters.
	 *
	 * @return 400 with parameter name
	 */
	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<Map<String, Object>> handleMissingParams(MissingServletRequestParameterException ex) {
		log.warn("Missing request parameter: {}", ex.getParameterName());
		return buildResponse(HttpStatus.BAD_REQUEST, "Missing required parameter: " + ex.getParameterName());
	}

	/**
	 * Handles type-mismatch on request parameters (e.g. passing a string where int is expected).
	 *
	 * @return 400 with parameter name and expected type
	 */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
		String message = String.format("Parameter '%s' must be of type %s",
				ex.getName(), ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");
		log.warn("Type mismatch: {}", message);
		return buildResponse(HttpStatus.BAD_REQUEST, message);
	}

	/**
	 * Handles business-rule violations thrown as {@link IllegalArgumentException}.
	 *
	 * <p>This is a fallback for any {@code IllegalArgumentException} that is not
	 * caught by a controller's own {@code try-catch}.</p>
	 *
	 * @return 400 with the exception message
	 */
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
		log.warn("Illegal argument: {}", ex.getMessage());
		return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
	}

	/**
	 * Catch-all for any other uncaught exception.
	 *
	 * @return 500 with a generic error message (details are logged, not exposed)
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
		log.error("Unhandled exception: {}", ex.getMessage(), ex);
		return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
	}

	// ------------------------------------------------------------------
	// Internal
	// ------------------------------------------------------------------

	private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("timestamp", Instant.now().toString());
		body.put("status", status.value());
		body.put("error", status.getReasonPhrase());
		body.put("message", message);
		return ResponseEntity.status(status).body(body);
	}
}
