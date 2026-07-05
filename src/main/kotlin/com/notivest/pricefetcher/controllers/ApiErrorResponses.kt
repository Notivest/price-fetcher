package com.notivest.pricefetcher.controllers

import com.notivest.pricefetcher.observability.CorrelationContext

object ApiErrorResponses {
  fun body(
    error: String,
    message: String? = null,
  ): Map<String, Any> =
    buildMap {
      put("error", error)
      message?.let { put("message", it) }
      CorrelationContext.currentCorrelationId()?.let { put("correlationId", it) }
      CorrelationContext.currentTraceId()?.let { put("traceId", it) }
    }
}
