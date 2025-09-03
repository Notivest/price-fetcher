package com.notivest.pricefetcher.security

import org.springframework.security.core.context.SecurityContextHolder

/**
 * Información unificada para calls del Gateway (usuarios) y calls directos (servicios)
 */
sealed class CallContext {
  abstract val requestId: String?
  abstract val scopes: Set<String>

  abstract fun hasScope(scope: String): Boolean

  abstract fun hasAnyScope(vararg scopes: String): Boolean

  abstract fun getIdentifier(): String
}

/**
 * Context para calls que vienen del Gateway (usuarios finales)
 */
data class GatewayCallContext(
  val userId: String,
  val userEmail: String?,
  val userName: String?,
  override val scopes: Set<String>,
  val sessionId: String?,
  override val requestId: String?,
) : CallContext() {
  override fun hasScope(scope: String): Boolean = scopes.contains(scope)

  override fun hasAnyScope(vararg scopes: String): Boolean = this.scopes.any { it in scopes }

  override fun getIdentifier(): String = userEmail ?: userId

  fun isUser(): Boolean = true
}

/**
 * Context para calls directos service-to-service
 */
data class ServiceCallContext(
  val serviceId: String,
  val serviceName: String?,
  val clientId: String,
  override val scopes: Set<String>,
  val tokenSubject: String?,
  val tokenIssuer: String?,
  val internalCall: Boolean = false,
  override val requestId: String?,
) : CallContext() {
  override fun hasScope(scope: String): Boolean = scopes.contains(scope)

  override fun hasAnyScope(vararg scopes: String): Boolean = this.scopes.any { it in scopes }

  override fun getIdentifier(): String = serviceName ?: serviceId

  fun isService(): Boolean = true

  fun isInternalCall(): Boolean = internalCall
}

/**
 * Context holder unificado para acceder a información del caller (usuario o servicio)
 */
object UnifiedContext {
  /**
   * Obtiene el contexto actual (puede ser usuario o servicio)
   */
  fun getCurrentContext(): CallContext? {
    return try {
      val authentication = SecurityContextHolder.getContext().authentication
      authentication?.principal as? CallContext
    } catch (e: Exception) {
      null
    }
  }

  /**
   * Obtiene el contexto actual o lanza excepción
   */
  fun requireCurrentContext(): CallContext {
    return getCurrentContext()
      ?: throw SecurityException("No call context available")
  }

  /**
   * Verifica si el caller actual tiene un scope específico
   */
  fun hasScope(scope: String): Boolean {
    return getCurrentContext()?.hasScope(scope) ?: false
  }

  /**
   * Verifica si el caller actual tiene alguno de los scopes especificados
   */
  fun hasAnyScope(vararg scopes: String): Boolean {
    return getCurrentContext()?.hasAnyScope(*scopes) ?: false
  }

  /**
   * Obtiene el identificador del caller actual
   */
  fun getCurrentIdentifier(): String? {
    return getCurrentContext()?.getIdentifier()
  }

  /**
   * Verifica si el call actual es de un usuario (via Gateway)
   */
  fun isUserCall(): Boolean {
    return getCurrentContext() is GatewayCallContext
  }

  /**
   * Verifica si el call actual es de un servicio (directo)
   */
  fun isServiceCall(): Boolean {
    return getCurrentContext() is ServiceCallContext
  }

  /**
   * Obtiene el contexto de usuario (solo si es call del Gateway)
   */
  fun getCurrentUser(): GatewayCallContext? {
    return getCurrentContext() as? GatewayCallContext
  }

  /**
   * Obtiene el contexto de servicio (solo si es call directo)
   */
  fun getCurrentService(): ServiceCallContext? {
    return getCurrentContext() as? ServiceCallContext
  }

  /**
   * Verifica si es un call interno entre servicios
   */
  fun isInternalCall(): Boolean {
    return getCurrentService()?.isInternalCall() ?: false
  }

  /**
   * Obtiene todos los scopes del caller actual
   */
  fun getCurrentScopes(): Set<String> {
    return getCurrentContext()?.scopes ?: emptySet()
  }
}
