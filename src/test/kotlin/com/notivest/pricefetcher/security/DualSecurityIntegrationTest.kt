package com.notivest.pricefetcher.security

import com.notivest.pricefetcher.service.MarketDataService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockitoBean
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest
@ActiveProfiles("test")
class DualSecurityIntegrationTest {

  @Autowired
  private lateinit var mockMvc: MockMvc

  @MockitoBean
  private lateinit var marketDataService: MarketDataService

  // ===============================
  // Tests para calls del Gateway (usuarios)
  // ===============================

  @Test
  fun `should allow gateway call with valid user headers`() {
    whenever(marketDataService.getQuotes(any())).thenReturn(emptyList())
    
    mockMvc.perform(
      get("/quotes?symbols=AAPL")
        .header("X-User-ID", "user123")
        .header("X-User-Email", "user@example.com")
        .header("X-User-Scopes", "read:prices,user:profile")
        .header("X-Session-ID", "session123")
    )
      .andExpect(status().isOk)
  }

  @Test
  fun `should reject gateway call with missing user headers`() {
    mockMvc.perform(
      get("/quotes?symbols=AAPL")
        .header("X-User-ID", "user123")
        // Falta X-User-Scopes
    )
      .andExpect(status().isUnauthorized)
      .andExpect(jsonPath("$.callType").value("gateway"))
  }

  // ===============================
  // Tests para calls directos (servicios)
  // ===============================

  @Test
  fun `should allow service call with valid JWT token`() {
    whenever(marketDataService.getQuotes(any())).thenReturn(emptyList())
    
    // JWT mock simplificado para test
    val mockJwt = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJwb3J0Zm9saW8tc2VydmljZUBjbGllbnRzIiwic2NvcGUiOiJyZWFkOnByaWNlcyBzZXJ2aWNlOmludGVybmFsIn0.mock-signature"
    
    mockMvc.perform(
      get("/quotes?symbols=AAPL")
        .header("Authorization", "Bearer $mockJwt")
        .header("X-Service-Name", "portfolio-service")
    )
      .andExpect(status().isOk)
  }

  @Test
  fun `should reject service call with invalid JWT token`() {
    mockMvc.perform(
      get("/quotes?symbols=AAPL")
        .header("Authorization", "Bearer invalid-token")
        .header("X-Service-Name", "portfolio-service")
    )
      .andExpect(status().isUnauthorized)
      .andExpect(jsonPath("$.callType").value("service"))
  }

  // ===============================
  // Tests para endpoints de servicio (usando endpoints públicos)
  // ===============================

  @Test
  fun `should allow service to access quotes endpoint with service token`() {
    whenever(marketDataService.getQuotes(any())).thenReturn(emptyList())
    val mockJwt = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhbGVydHMtc2VydmljZUBjbGllbnRzIiwic2NvcGUiOiJyZWFkOnByaWNlcyBzZXJ2aWNlOmludGVybmFsIn0.mock-signature"
    
    mockMvc.perform(
      get("/quotes?symbols=AAPL")
        .header("Authorization", "Bearer $mockJwt")
        .header("X-Service-Name", "alerts-service")
    )
      .andExpect(status().isOk)
  }

  @Test
  fun `should allow service to access prefetch endpoint with write scope`() {
    val mockJwt = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhZG1pbi1zZXJ2aWNlQGNsaWVudHMiLCJzY29wZSI6IndyaXRlOnByaWNlcyBzZXJ2aWNlOmludGVybmFsIn0.mock-signature"
    
    mockMvc.perform(
      post("/prefetch")
        .header("Authorization", "Bearer $mockJwt")
        .header("X-Service-Name", "admin-service")
        .contentType("application/json")
    )
      .andExpect(status().isOk)
  }

  // ===============================
  // Tests para health checks
  // ===============================

  @Test
  fun `should allow health check without authentication`() {
    mockMvc.perform(get("/health"))
      .andExpect(status().isOk)
  }

  @Test
  fun `should allow info endpoint access with service token`() {
    val mockJwt = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0LXNlcnZpY2VAY2xpZW50cyIsInNjb3BlIjoic2VydmljZTppbnRlcm5hbCJ9.mock-signature"
    
    mockMvc.perform(
      get("/info")
        .header("Authorization", "Bearer $mockJwt")
        .header("X-Service-Name", "test-service")
    )
      .andExpect(status().isOk)
  }

  // ===============================
  // Tests de detección automática
  // ===============================

  @Test
  fun `should detect gateway call type correctly`() {
    whenever(marketDataService.getQuotes(any())).thenReturn(emptyList())
    
    mockMvc.perform(
      get("/quotes?symbols=AAPL")
        .header("X-User-ID", "user123")
        .header("X-User-Scopes", "read:prices")
    )
      .andExpect(status().isOk)
      // Verificar que se detectó como gateway call en logs
  }

  @Test
  fun `should detect service call type correctly`() {
    whenever(marketDataService.getQuotes(any())).thenReturn(emptyList())
    
    val mockJwt = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0LXNlcnZpY2VAY2xpZW50cyIsInNjb3BlIjoicmVhZDpwcmljZXMifQ.mock-signature"
    
    mockMvc.perform(
      get("/quotes?symbols=AAPL")
        .header("Authorization", "Bearer $mockJwt")
        .header("X-Service-Name", "test-service")
    )
      .andExpect(status().isOk)
      // Verificar que se detectó como service call en logs
  }

  // ===============================
  // Tests de scopes insuficientes
  // ===============================

  @Test
  fun `should reject user call with insufficient scopes`() {
    mockMvc.perform(
      get("/quotes?symbols=AAPL")
        .header("X-User-ID", "user123")
        .header("X-User-Scopes", "user:profile") // Falta read:prices
    )
      .andExpect(status().isForbidden)
      .andExpect(jsonPath("$.error").value("forbidden"))
  }

  @Test
  fun `should reject service call with insufficient scopes`() {
    val mockJwt = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0LXNlcnZpY2VAY2xpZW50cyIsInNjb3BlIjoid3JpdGU6cHJpY2VzIn0.mock-signature"
    
    mockMvc.perform(
      get("/quotes?symbols=AAPL")
        .header("Authorization", "Bearer $mockJwt")
        .header("X-Service-Name", "test-service")
    )
      .andExpect(status().isForbidden)
      .andExpect(jsonPath("$.error").value("forbidden"))
  }
}
