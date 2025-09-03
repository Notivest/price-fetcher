package com.notivest.pricefetcher.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.*

@Component
@ConditionalOnProperty(name = ["pricefetcher.security.enabled"], havingValue = "true", matchIfMissing = true)
class DualAuthenticationFilter(
  private val jwtDecoder: JwtDecoder? = null,
) : OncePerRequestFilter() {
  private val logger = LoggerFactory.getLogger(DualAuthenticationFilter::class.java)

  companion object {
    // Headers del Gateway (para usuarios)
    const val HEADER_USER_ID = "X-User-ID"
    const val HEADER_USER_EMAIL = "X-User-Email"
    const val HEADER_USER_NAME = "X-User-Name"
    const val HEADER_USER_SCOPES = "X-User-Scopes"
    const val HEADER_SESSION_ID = "X-Session-ID"

    // Headers para service-to-service
    const val HEADER_SERVICE_NAME = "X-Service-Name"
    const val HEADER_INTERNAL_CALL = "X-Internal-Call"
    const val HEADER_REQUEST_ID = "X-Request-ID"

    // Authorization header para JWT
    const val AUTHORIZATION_HEADER = "Authorization"
    const val BEARER_PREFIX = "Bearer "
  }

  override fun doFilterInternal(
    request: HttpServletRequest,
    response: HttpServletResponse,
    filterChain: FilterChain,
  ) {
    try {
      // Generar request ID si no existe
      val requestId = request.getHeader(HEADER_REQUEST_ID) ?: UUID.randomUUID().toString()

      // Agregar información básica al MDC
      MDC.put("requestId", requestId)
      MDC.put("method", request.method)
      MDC.put("uri", request.requestURI)

      // Determinar tipo de autenticación y procesar
      val callContext = determineCallType(request, requestId)

      if (callContext != null) {
        // Agregar información del caller al MDC
        MDC.put("callType", if (callContext is GatewayCallContext) "gateway" else "service")
        MDC.put("callerId", callContext.getIdentifier())

        // Crear authorities basados en scopes
        val authorities = callContext.scopes.map { SimpleGrantedAuthority(it) }

        // Crear authentication token
        val authentication =
          UsernamePasswordAuthenticationToken(
            // principal
            callContext,
            // credentials
            null,
            // authorities
            authorities,
          )

        // Establecer en SecurityContext
        SecurityContextHolder.getContext().authentication = authentication

        val callType = if (callContext is GatewayCallContext) "gateway" else "service"
        logger.debug("Authenticated $callType call from: ${callContext.getIdentifier()} with scopes: ${callContext.scopes} for request: $requestId")
      } else {
        logger.warn("No valid authentication found for request: $requestId")
      }

      filterChain.doFilter(request, response)
    } catch (e: Exception) {
      logger.error("Error processing dual authentication", e)
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication failed")
    } finally {
      // Limpiar MDC y SecurityContext
      MDC.clear()
      SecurityContextHolder.clearContext()
    }
  }

  private fun determineCallType(
    request: HttpServletRequest,
    requestId: String,
  ): CallContext? {
    // 1. Verificar si es call del Gateway (tiene headers de usuario)
    val userId = request.getHeader(HEADER_USER_ID)
    if (!userId.isNullOrBlank()) {
      return processGatewayCall(request, requestId)
    }

    // 2. Verificar si es call directo con JWT
    val authHeader = request.getHeader(AUTHORIZATION_HEADER)
    if (authHeader?.startsWith(BEARER_PREFIX) == true) {
      return processServiceCall(request, requestId, authHeader)
    }

    return null
  }

  private fun processGatewayCall(
    request: HttpServletRequest,
    requestId: String,
  ): GatewayCallContext? {
    val userId = request.getHeader(HEADER_USER_ID) ?: return null
    val userEmail = request.getHeader(HEADER_USER_EMAIL)
    val userName = request.getHeader(HEADER_USER_NAME)
    val scopesHeader = request.getHeader(HEADER_USER_SCOPES) ?: ""
    val sessionId = request.getHeader(HEADER_SESSION_ID)

    // Parsear scopes
    val scopes =
      scopesHeader.split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toSet()

    if (scopes.isEmpty()) {
      logger.warn("No valid scopes found in gateway headers for user: $userId")
      return null
    }

    return GatewayCallContext(
      userId = userId,
      userEmail = userEmail,
      userName = userName,
      scopes = scopes,
      sessionId = sessionId,
      requestId = requestId,
    )
  }

  private fun processServiceCall(
    request: HttpServletRequest,
    requestId: String,
    authHeader: String,
  ): ServiceCallContext? {
    if (jwtDecoder == null) {
      logger.warn("JWT decoder not available for service call validation")
      return null
    }

    try {
      val token = authHeader.removePrefix(BEARER_PREFIX)
      val jwt = jwtDecoder.decode(token)

      // Extraer información del JWT
      val serviceId = jwt.subject ?: return null
      val clientId = jwt.getClaimAsString("azp") ?: jwt.getClaimAsString("client_id") ?: serviceId
      val tokenIssuer = jwt.issuer?.toString()

      // Extraer scopes del JWT
      val scopes = extractScopesFromJwt(jwt)
      if (scopes.isEmpty()) {
        logger.warn("No valid scopes found in JWT for service: $serviceId")
        return null
      }

      // Headers adicionales para service-to-service
      val serviceName = request.getHeader(HEADER_SERVICE_NAME)
      val internalCall = request.getHeader(HEADER_INTERNAL_CALL)?.toBoolean() ?: false

      return ServiceCallContext(
        serviceId = serviceId,
        serviceName = serviceName,
        clientId = clientId,
        scopes = scopes,
        tokenSubject = serviceId,
        tokenIssuer = tokenIssuer,
        internalCall = internalCall,
        requestId = requestId,
      )
    } catch (e: Exception) {
      logger.warn("Failed to validate JWT token", e)
      return null
    }
  }

  private fun extractScopesFromJwt(jwt: org.springframework.security.oauth2.jwt.Jwt): Set<String> {
    // Intentar diferentes formas de extraer scopes según el proveedor
    return when {
      // Auth0 format
      jwt.hasClaim("scope") -> {
        jwt.getClaimAsString("scope")?.split(" ")?.toSet() ?: emptySet()
      }
      // Alternative format
      jwt.hasClaim("scopes") -> {
        jwt.getClaimAsStringList("scopes")?.toSet() ?: emptySet()
      }
      // Permissions format (Auth0 también puede usar esto)
      jwt.hasClaim("permissions") -> {
        jwt.getClaimAsStringList("permissions")?.toSet() ?: emptySet()
      }
      else -> emptySet()
    }
  }

  override fun shouldNotFilter(request: HttpServletRequest): Boolean {
    val path = request.requestURI

    // No filtrar health checks y actuator endpoints
    return path.startsWith("/health") ||
      path.startsWith("/actuator")
  }
}
