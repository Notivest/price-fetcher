package com.notivest.pricefetcher.security

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.Instant

@Component
@ConditionalOnProperty(name = ["pricefetcher.security.audit.enabled"], havingValue = "true")
class AuditLogger {
  
  private val auditLogger = LoggerFactory.getLogger("AUDIT")
  
  fun logServiceCall(
    serviceInfo: CallContext,
    method: String,
    path: String,
    statusCode: Int? = null,
    duration: Long? = null,
    errorMessage: String? = null
  ) {
    val logData = mutableMapOf<String, Any>(
      "timestamp" to Instant.now().toString(),
      "callerId" to serviceInfo.getIdentifier(),
      "callerType" to when (serviceInfo) {
        is GatewayCallContext -> "user"
        is ServiceCallContext -> "service"
      },
      "scopes" to serviceInfo.scopes.joinToString(","),
      "method" to method,
      "path" to path,
      "requestId" to (serviceInfo.requestId ?: "unknown")
    )
    
    statusCode?.let { logData["statusCode"] = it }
    duration?.let { logData["durationMs"] = it }
    errorMessage?.let { logData["error"] = it }
    
    if (errorMessage != null) {
      auditLogger.warn("SERVICE_CALL_ERROR: {}", formatLogData(logData))
    } else {
      auditLogger.info("SERVICE_CALL: {}", formatLogData(logData))
    }
  }
  
  fun logAuthenticationFailure(
    method: String,
    path: String,
    reason: String,
    headers: Map<String, String> = emptyMap()
  ) {
    val logData = mapOf(
      "timestamp" to Instant.now().toString(),
      "event" to "AUTHENTICATION_FAILURE",
      "method" to method,
      "path" to path,
      "reason" to reason,
      "headers" to headers.keys.joinToString(",")
    )
    
    auditLogger.warn("AUTH_FAILURE: {}", formatLogData(logData))
  }
  
  fun logAuthorizationFailure(
    serviceInfo: CallContext,
    method: String,
    path: String,
    requiredScopes: List<String>,
    reason: String
  ) {
    val logData = mapOf(
      "timestamp" to Instant.now().toString(),
      "event" to "AUTHORIZATION_FAILURE",
      "callerId" to serviceInfo.getIdentifier(),
      "callerType" to when (serviceInfo) {
        is GatewayCallContext -> "user"
        is ServiceCallContext -> "service"
      },
      "method" to method,
      "path" to path,
      "requiredScopes" to requiredScopes.joinToString(","),
      "currentScopes" to serviceInfo.scopes.joinToString(","),
      "reason" to reason,
      "requestId" to (serviceInfo.requestId ?: "unknown")
    )
    
    auditLogger.warn("AUTHZ_FAILURE: {}", formatLogData(logData))
  }
  
  fun logScopeValidation(
    serviceInfo: CallContext,
    requiredScopes: List<String>,
    success: Boolean
  ) {
    val logData = mapOf(
      "timestamp" to Instant.now().toString(),
      "event" to "SCOPE_VALIDATION",
      "callerId" to serviceInfo.getIdentifier(),
      "callerType" to when (serviceInfo) {
        is GatewayCallContext -> "user"
        is ServiceCallContext -> "service"
      },
      "requiredScopes" to requiredScopes.joinToString(","),
      "currentScopes" to serviceInfo.scopes.joinToString(","),
      "success" to success,
      "requestId" to (serviceInfo.requestId ?: "unknown")
    )
    
    if (success) {
      auditLogger.debug("SCOPE_CHECK_PASSED: {}", formatLogData(logData))
    } else {
      auditLogger.warn("SCOPE_CHECK_FAILED: {}", formatLogData(logData))
    }
  }
  
  private fun formatLogData(data: Map<String, Any>): String {
    return data.entries.joinToString(" | ") { "${it.key}=${it.value}" }
  }
}
