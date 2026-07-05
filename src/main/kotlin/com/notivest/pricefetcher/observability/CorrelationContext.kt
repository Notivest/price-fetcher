package com.notivest.pricefetcher.observability

import org.slf4j.MDC
import org.springframework.util.StringUtils

object CorrelationContext {
    const val HEADER_CORRELATION_ID = "X-Correlation-Id"
    const val HEADER_REQUEST_ID = "X-Request-Id"
    private const val MDC_CORRELATION_ID = "correlationId"
    private const val MDC_USER_ID = "userId"
    private const val MDC_TRACE_ID = "traceId"

    fun setCorrelationId(correlationId: String) {
        MDC.put(MDC_CORRELATION_ID, correlationId)
    }

    fun setUserId(userId: String) {
        MDC.put(MDC_USER_ID, userId)
    }

    fun clear() {
        MDC.remove(MDC_CORRELATION_ID)
        MDC.remove(MDC_USER_ID)
    }

    fun currentCorrelationId(): String? =
        MDC.get(MDC_CORRELATION_ID)?.takeIf(StringUtils::hasText)

    fun currentTraceId(): String? =
        MDC.get(MDC_TRACE_ID)?.takeIf(StringUtils::hasText)
}
