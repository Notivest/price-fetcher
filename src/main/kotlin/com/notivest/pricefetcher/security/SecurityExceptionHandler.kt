package com.notivest.pricefetcher.security

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import java.time.Instant

@RestControllerAdvice
class SecurityExceptionHandler {
  private val logger = LoggerFactory.getLogger(SecurityExceptionHandler::class.java)

  @ExceptionHandler(AccessDeniedException::class)
  fun handleAccessDenied(
    ex: AccessDeniedException,
    request: WebRequest,
  ): ResponseEntity<Map<String, Any>> {
    val callContext = UnifiedContext.getCurrentContext()

    logger.warn(
      "Access denied for service {}: {} - Path: {}",
      callContext?.getIdentifier() ?: "unknown",
      ex.message,
      request.getDescription(false),
    )

    val errorResponse =
      mapOf(
        "error" to "forbidden",
        "message" to (ex.message ?: "Access denied"),
        "timestamp" to Instant.now().toString(),
        "path" to extractPath(request),
        "serviceId" to (callContext?.getIdentifier() ?: "unknown"),
        "serviceName" to (if (UnifiedContext.isUserCall()) "user" else "service"),
        "currentScopes" to (callContext?.scopes ?: emptySet<String>()),
      )

    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse)
  }

  @ExceptionHandler(AuthenticationException::class)
  fun handleAuthenticationException(
    ex: AuthenticationException,
    request: WebRequest,
  ): ResponseEntity<Map<String, Any>> {
    logger.warn(
      "Authentication failed: {} - Path: {}",
      ex.message,
      request.getDescription(false),
    )

    val errorResponse =
      mapOf(
        "error" to "unauthorized",
        "message" to "Authentication required. Please provide valid service headers.",
        "timestamp" to Instant.now().toString(),
        "path" to extractPath(request),
        "requiredHeaders" to
          listOf(
            "X-User-ID",
            "X-User-Scopes",
            "X-Token-Type",
          ),
      )

    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse)
  }

  @ExceptionHandler(AuthenticationCredentialsNotFoundException::class)
  fun handleCredentialsNotFound(
    ex: AuthenticationCredentialsNotFoundException,
    request: WebRequest,
  ): ResponseEntity<Map<String, Any>> {
    logger.warn(
      "Authentication credentials not found: {} - Path: {}",
      ex.message,
      request.getDescription(false),
    )

    val errorResponse =
      mapOf(
        "error" to "unauthorized",
        "message" to "Authentication credentials not found",
        "timestamp" to Instant.now().toString(),
        "path" to extractPath(request),
      )

    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse)
  }

  @ExceptionHandler(SecurityException::class)
  fun handleSecurityException(
    ex: SecurityException,
    request: WebRequest,
  ): ResponseEntity<Map<String, Any>> {
    logger.error(
      "Security exception: {} - Path: {}",
      ex.message,
      request.getDescription(false),
      ex,
    )

    val errorResponse =
      mapOf(
        "error" to "security_error",
        "message" to (ex.message ?: "Security error occurred"),
        "timestamp" to Instant.now().toString(),
        "path" to extractPath(request),
      )

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse)
  }

  private fun extractPath(request: WebRequest): String {
    val description = request.getDescription(false)
    return if (description.startsWith("uri=")) {
      description.substring(4)
    } else {
      description
    }
  }
}
