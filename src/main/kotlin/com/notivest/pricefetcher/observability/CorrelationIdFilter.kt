package com.notivest.pricefetcher.observability

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CorrelationIdFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val correlationId = resolveCorrelationId(request)
        CorrelationContext.setCorrelationId(correlationId)
        response.setHeader(CorrelationContext.HEADER_CORRELATION_ID, correlationId)

        resolveUserId()?.let { CorrelationContext.setUserId(it) }

        try {
            filterChain.doFilter(request, response)
        } finally {
            CorrelationContext.clear()
        }
    }

    private fun resolveCorrelationId(request: HttpServletRequest): String {
        val fromHeader =
            request.getHeader(CorrelationContext.HEADER_CORRELATION_ID)
                ?: request.getHeader(CorrelationContext.HEADER_REQUEST_ID)
        return fromHeader?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
    }

    private fun resolveUserId(): String? {
        val authentication = SecurityContextHolder.getContext()?.authentication ?: return null
        if (!authentication.isAuthenticated) return null

        if (authentication is JwtAuthenticationToken) {
            return (authentication.token.subject ?: authentication.name)
                .takeIf { it.isNotBlank() && it != "anonymousUser" }
        }

        return authentication.name.takeIf { it.isNotBlank() && it != "anonymousUser" }
    }
}
