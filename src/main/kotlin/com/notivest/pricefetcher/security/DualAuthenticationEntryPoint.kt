package com.notivest.pricefetcher.security

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class DualAuthenticationEntryPoint(
  private val objectMapper: ObjectMapper
) : AuthenticationEntryPoint {
  
  private val logger = LoggerFactory.getLogger(DualAuthenticationEntryPoint::class.java)
  
  override fun commence(
    request: HttpServletRequest,
    response: HttpServletResponse,
    authException: AuthenticationException
  ) {
    logger.warn(
      "Authentication failed for {} {}: {}",
      request.method,
      request.requestURI,
      authException.message
    )
    
    val errorResponse = determineErrorResponse(request)
    
    response.status = HttpServletResponse.SC_UNAUTHORIZED
    response.contentType = MediaType.APPLICATION_JSON_VALUE
    response.characterEncoding = "UTF-8"
    
    objectMapper.writeValue(response.outputStream, errorResponse)
  }
  
  private fun determineErrorResponse(request: HttpServletRequest): Map<String, Any> {
    val hasGatewayHeaders = request.getHeader("X-User-ID") != null
    val hasBearerToken = request.getHeader("Authorization")?.startsWith("Bearer ") == true
    
    return when {
      hasGatewayHeaders -> createGatewayErrorResponse(request)
      hasBearerToken -> createServiceErrorResponse(request)
      else -> createGenericErrorResponse(request)
    }
  }
  
  private fun createGatewayErrorResponse(request: HttpServletRequest): Map<String, Any> {
    return mapOf(
      "error" to "unauthorized",
      "message" to "Invalid or missing gateway headers. User authentication failed.",
      "timestamp" to Instant.now().toString(),
      "path" to request.requestURI,
      "method" to request.method,
      "callType" to "gateway",
      "requiredHeaders" to listOf(
        "X-User-ID",
        "X-User-Scopes"
      )
    )
  }
  
  private fun createServiceErrorResponse(request: HttpServletRequest): Map<String, Any> {
    return mapOf(
      "error" to "unauthorized",
      "message" to "Invalid or expired JWT token. Service authentication failed.",
      "timestamp" to Instant.now().toString(),
      "path" to request.requestURI,
      "method" to request.method,
      "callType" to "service",
      "requiredToken" to "Valid Bearer JWT token with appropriate scopes"
    )
  }
  
  private fun createGenericErrorResponse(request: HttpServletRequest): Map<String, Any> {
    return mapOf(
      "error" to "unauthorized",
      "message" to "Authentication required. Provide either gateway headers or JWT token.",
      "timestamp" to Instant.now().toString(),
      "path" to request.requestURI,
      "method" to request.method,
      "supportedAuthTypes" to listOf(
        mapOf(
          "type" to "gateway",
          "description" to "Headers from API Gateway",
          "headers" to listOf("X-User-ID", "X-User-Scopes")
        ),
        mapOf(
          "type" to "service",
          "description" to "JWT token for service-to-service calls",
          "header" to "Authorization: Bearer <jwt-token>"
        )
      )
    )
  }
}
