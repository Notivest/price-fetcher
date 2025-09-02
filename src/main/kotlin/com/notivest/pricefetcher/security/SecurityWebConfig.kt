package com.notivest.pricefetcher.security

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
@ConditionalOnProperty(name = ["pricefetcher.security.audit.enabled"], havingValue = "true")
class SecurityWebConfig(
  private val auditInterceptor: AuditInterceptor
) : WebMvcConfigurer {
  
  override fun addInterceptors(registry: InterceptorRegistry) {
    registry.addInterceptor(auditInterceptor)
      .addPathPatterns("/**")
      .excludePathPatterns("/health", "/actuator/**")
  }
}
