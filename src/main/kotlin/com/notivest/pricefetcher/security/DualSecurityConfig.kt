package com.notivest.pricefetcher.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
class DualSecurityConfig(
  private val dualAuthenticationFilter: DualAuthenticationFilter,
  private val dualAuthenticationEntryPoint: DualAuthenticationEntryPoint,
  @Value("\${pricefetcher.security.jwt.domain:your-domain.auth0.com}") private val jwtDomain: String
) {
  
  @Bean
  @Order(1)
  @ConditionalOnProperty(name = ["pricefetcher.security.enabled"], havingValue = "true", matchIfMissing = true)
  fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
    return http
      .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
      .csrf { it.disable() }
      .authorizeHttpRequests { auth ->
        auth
          // Health checks sin autenticación
          .requestMatchers("/health", "/actuator/**").permitAll()
          .requestMatchers("/info/**").permitAll()
          
          // Endpoints públicos de cotizaciones (para usuarios via Gateway)
          .requestMatchers("GET", "/quotes/**", "/historical/**").hasAuthority("read:prices")
          .requestMatchers("POST", "/prefetch").hasAuthority("write:prices")
          
          // Watchlist endpoints
          .requestMatchers("GET", "/watchlist/**").hasAuthority("read:prices")
          .requestMatchers("POST", "/watchlist/**").hasAuthority("write:prices")
          .requestMatchers("PATCH", "/watchlist/**").hasAuthority("write:prices")
          .requestMatchers("DELETE", "/watchlist/**").hasAuthority("write:prices")
          
          // Todos los endpoints están disponibles para ambos tipos de calls
          // La validación específica se hace con annotations en controllers
          
          // Todo lo demás requiere autenticación
          .anyRequest().authenticated()
      }
      .addFilterBefore(dualAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
      .exceptionHandling { it.authenticationEntryPoint(dualAuthenticationEntryPoint) }
      .build()
  }

  @Bean
  @Order(2)
  @ConditionalOnProperty(name = ["pricefetcher.security.enabled"], havingValue = "false")
  fun devSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
    return http
      .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
      .csrf { it.disable() }
      .authorizeHttpRequests { auth ->
        auth.anyRequest().permitAll()
      }
      .build()
  }
  
  @Bean
  @ConditionalOnProperty(name = ["pricefetcher.security.jwt.enabled"], havingValue = "true", matchIfMissing = true)
  fun jwtDecoder(): JwtDecoder {
    // Para Auth0, usar la URL del JWKS endpoint
    val jwkSetUri = "https://$jwtDomain/.well-known/jwks.json"
    return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build()
  }
}
