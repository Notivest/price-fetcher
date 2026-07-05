package com.notivest.pricefetcher.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.notivest.pricefetcher.observability.CorrelationContext
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component

@Component
class AuthenticationErrorHandler(
  private val objectMapper: ObjectMapper,
) : AuthenticationEntryPoint {
  private val logger = LoggerFactory.getLogger(AuthenticationErrorHandler::class.java)

  override fun commence(
    request: HttpServletRequest,
    response: HttpServletResponse,
    authException: AuthenticationException,
  ) {
    response.status = HttpServletResponse.SC_UNAUTHORIZED
    response.contentType = MediaType.APPLICATION_JSON_VALUE

    val errorResponse = createErrorResponse(request, authException)

    logger.warn(
      "Authentication failed correlationId={} traceId={} message={}",
      errorResponse.correlationId,
      errorResponse.traceId,
      errorResponse.message,
      authException,
    )

    response.writer.write(objectMapper.writeValueAsString(errorResponse))
    response.writer.flush()
  }

  private fun createErrorResponse(
    request: HttpServletRequest,
    ex: AuthenticationException,
  ): AuthenticationErrorResponse {
    val correlationId =
      CorrelationContext.currentCorrelationId()
        ?: request.getHeader(CorrelationContext.HEADER_CORRELATION_ID)
        ?: request.getHeader(CorrelationContext.HEADER_REQUEST_ID)
    val traceId = CorrelationContext.currentTraceId()

    return when (ex) {
      is OAuth2AuthenticationException -> {
        when {
          ex.error.errorCode == "invalid_token" ->
            AuthenticationErrorResponse(
              error = "invalid_token",
              message = "El token JWT proporcionado es inválido",
              details = "Verifica que el token esté bien formado y no haya expirado",
              correlationId = correlationId,
              traceId = traceId,
            )
          ex.error.errorCode == "insufficient_scope" ->
            AuthenticationErrorResponse(
              error = "insufficient_scope",
              message = "El token no tiene los permisos necesarios",
              details = "Se requieren permisos adicionales para acceder a este recurso",
              correlationId = correlationId,
              traceId = traceId,
            )
          else ->
            AuthenticationErrorResponse(
              error = "authentication_failed",
              message = "Error de autenticación OAuth2",
              details = ex.error.description ?: "Token inválido o expirado",
              correlationId = correlationId,
              traceId = traceId,
            )
        }
      }

      else -> {
        when {
          ex.message?.contains("JWT") == true ->
            AuthenticationErrorResponse(
              error = "jwt_error",
              message = "Error procesando el token JWT",
              details = "Token malformado, expirado o inválido",
              correlationId = correlationId,
              traceId = traceId,
            )
          ex.message?.contains("Bearer") == true ->
            AuthenticationErrorResponse(
              error = "missing_token",
              message = "Token de autorización requerido",
              details = "Incluye el header 'Authorization: Bearer <token>'",
              correlationId = correlationId,
              traceId = traceId,
            )
          else ->
            AuthenticationErrorResponse(
              error = "unauthorized",
              message = "Acceso no autorizado",
              details = "Se requiere autenticación válida para acceder a este recurso",
              correlationId = correlationId,
              traceId = traceId,
            )
        }
      }
    }
  }
}
