package com.notivest.pricefetcher.security

import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.slf4j.LoggerFactory
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Component

@Aspect
@Component
class DualScopeValidationAspect {
  
  private val logger = LoggerFactory.getLogger(DualScopeValidationAspect::class.java)
  
  @Before("@annotation(requireServiceScope)")
  fun validateRequireServiceScope(joinPoint: JoinPoint, requireServiceScope: RequireServiceScope) {
    val callContext = UnifiedContext.getCurrentContext()
      ?: throw AccessDeniedException("No call context available")
    
    val requiredScopes = requireServiceScope.value
    val hasAnyScope = callContext.hasAnyScope(*requiredScopes)
    
    if (!hasAnyScope) {
      logger.warn(
        "Access denied for caller {} - missing required service scopes: {} (has: {})",
        callContext.getIdentifier(),
        requiredScopes.toList(),
        callContext.scopes
      )
      throw AccessDeniedException(
        "Insufficient scopes. Required: ${requiredScopes.joinToString(" or ")}, " +
        "but caller has: ${callContext.scopes.joinToString(", ")}"
      )
    }
    
    logger.debug(
      "Service scope validation passed for caller {} with scopes: {}",
      callContext.getIdentifier(),
      callContext.scopes
    )
  }
  
  @Before("@annotation(allowBothCallTypes)")
  fun validateAllowBothCallTypes(joinPoint: JoinPoint, allowBothCallTypes: AllowBothCallTypes) {
    val callContext = UnifiedContext.getCurrentContext()
      ?: throw AccessDeniedException("No call context available")
    
    when {
      UnifiedContext.isUserCall() -> {
        // Validar scopes de usuario
        val userScopes = allowBothCallTypes.userScopes
        if (userScopes.isNotEmpty() && !callContext.hasAnyScope(*userScopes)) {
          logger.warn(
            "Access denied for user {} - missing required user scopes: {} (has: {})",
            callContext.getIdentifier(),
            userScopes.toList(),
            callContext.scopes
          )
          throw AccessDeniedException(
            "Insufficient user scopes. Required: ${userScopes.joinToString(" or ")}"
          )
        }
      }
      
      UnifiedContext.isServiceCall() -> {
        // Validar scopes de servicio
        val serviceScopes = allowBothCallTypes.serviceScopes
        if (serviceScopes.isNotEmpty() && !callContext.hasAnyScope(*serviceScopes)) {
          logger.warn(
            "Access denied for service {} - missing required service scopes: {} (has: {})",
            callContext.getIdentifier(),
            serviceScopes.toList(),
            callContext.scopes
          )
          throw AccessDeniedException(
            "Insufficient service scopes. Required: ${serviceScopes.joinToString(" or ")}"
          )
        }
      }
      
      else -> {
        throw AccessDeniedException("Unknown call type")
      }
    }
    
    logger.debug(
      "Dual call type validation passed for {} {}",
      if (UnifiedContext.isUserCall()) "user" else "service",
      callContext.getIdentifier()
    )
  }
  
  // Validación a nivel de clase
  @Before("@within(requireServiceScope) && execution(public * *(..))")
  fun validateClassRequireServiceScope(joinPoint: JoinPoint, requireServiceScope: RequireServiceScope) {
    validateRequireServiceScope(joinPoint, requireServiceScope)
  }
  
  @Before("@within(allowBothCallTypes) && execution(public * *(..))")
  fun validateClassAllowBothCallTypes(joinPoint: JoinPoint, allowBothCallTypes: AllowBothCallTypes) {
    validateAllowBothCallTypes(joinPoint, allowBothCallTypes)
  }
}
