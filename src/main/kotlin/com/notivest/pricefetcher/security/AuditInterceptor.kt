package com.notivest.pricefetcher.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

@Component
@ConditionalOnProperty(name = ["pricefetcher.security.audit.enabled"], havingValue = "true")
class AuditInterceptor(
  private val auditLogger: AuditLogger,
) : HandlerInterceptor {
  companion object {
    private const val START_TIME_ATTRIBUTE = "audit.startTime"
  }

  override fun preHandle(
    request: HttpServletRequest,
    response: HttpServletResponse,
    handler: Any,
  ): Boolean {
    // Marcar tiempo de inicio para medir duración
    request.setAttribute(START_TIME_ATTRIBUTE, System.currentTimeMillis())

    return true
  }

  override fun afterCompletion(
    request: HttpServletRequest,
    response: HttpServletResponse,
    handler: Any,
    ex: Exception?,
  ) {
    // Calcular duración
    val startTime = request.getAttribute(START_TIME_ATTRIBUTE) as? Long
    val duration = startTime?.let { System.currentTimeMillis() - it }

    // Obtener información del servicio
    val callContext = UnifiedContext.getCurrentContext()

    if (callContext != null) {
      // Log de llamada exitosa o con error
      auditLogger.logServiceCall(
        serviceInfo = callContext,
        method = request.method,
        path = request.requestURI,
        statusCode = response.status,
        duration = duration,
        errorMessage = ex?.message,
      )
    } else if (shouldAuditPath(request.requestURI)) {
      // Log de fallo de autenticación para paths que deberían estar autenticados
      auditLogger.logAuthenticationFailure(
        method = request.method,
        path = request.requestURI,
        reason = "No service context available",
        headers = extractRelevantHeaders(request),
      )
    }
  }

  private fun shouldAuditPath(path: String): Boolean {
    // No auditar health checks y actuator endpoints
    return !path.startsWith("/health") &&
      !path.startsWith("/actuator") &&
      !path.startsWith("/info")
  }

  private fun extractRelevantHeaders(request: HttpServletRequest): Map<String, String> {
    val relevantHeaders =
      listOf(
        "X-User-ID",
        "X-User-Scopes",
        "X-Token-Type",
        "X-Service-Name",
        "X-Request-ID",
      )

    return relevantHeaders.mapNotNull { headerName ->
      request.getHeader(headerName)?.let { headerValue ->
        headerName to if (headerValue.isNotBlank()) "present" else "empty"
      }
    }.toMap()
  }
}
