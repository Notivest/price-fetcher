package com.notivest.pricefetcher.security

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2AuthenticationException

class AuthenticationErrorHandlerTest {
    private val objectMapper = jacksonObjectMapper().findAndRegisterModules()
    private lateinit var handler: AuthenticationErrorHandler
    private lateinit var request: MockHttpServletRequest
    private lateinit var response: MockHttpServletResponse

    @BeforeEach
    fun setUp() {
        handler = AuthenticationErrorHandler(objectMapper)
        request = MockHttpServletRequest()
        response = MockHttpServletResponse()
    }

    @Test
    fun `returns oauth invalid token response`() {
        val ex = OAuth2AuthenticationException(OAuth2Error("invalid_token", "bad token", null))

        handler.commence(request, response, ex)

        val payload = objectMapper.readTree(response.contentAsString)
        assertThat(response.status).isEqualTo(401)
        assertThat(payload["error"].asText()).isEqualTo("invalid_token")
        assertThat(payload["message"].asText()).isEqualTo("El token JWT proporcionado es inválido")
    }

    @Test
    fun `returns oauth insufficient scope response`() {
        val ex = OAuth2AuthenticationException(OAuth2Error("insufficient_scope", "missing scope", null))

        handler.commence(request, response, ex)

        val payload = objectMapper.readTree(response.contentAsString)
        assertThat(payload["error"].asText()).isEqualTo("insufficient_scope")
        assertThat(payload["message"].asText()).isEqualTo("El token no tiene los permisos necesarios")
    }

    @Test
    fun `returns jwt parsing error response`() {
        val ex = object : AuthenticationException("JWT expired") {}

        handler.commence(request, response, ex)

        val payload = objectMapper.readTree(response.contentAsString)
        assertThat(payload["error"].asText()).isEqualTo("jwt_error")
        assertThat(payload["details"].asText()).contains("Token malformado")
    }

    @Test
    fun `returns missing bearer token response`() {
        val ex = object : AuthenticationException("Missing Bearer token") {}

        handler.commence(request, response, ex)

        val payload = objectMapper.readTree(response.contentAsString)
        assertThat(payload["error"].asText()).isEqualTo("missing_token")
        assertThat(payload["details"].asText()).contains("Authorization: Bearer")
    }

    @Test
    fun `returns generic unauthorized response for unknown authentication error`() {
        val ex = object : AuthenticationException("auth failed") {}

        handler.commence(request, response, ex)

        val payload = objectMapper.readTree(response.contentAsString)
        assertThat(payload["error"].asText()).isEqualTo("unauthorized")
        assertThat(payload["message"].asText()).isEqualTo("Acceso no autorizado")
    }
}
