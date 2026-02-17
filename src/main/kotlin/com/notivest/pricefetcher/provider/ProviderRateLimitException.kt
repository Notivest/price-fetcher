package com.notivest.pricefetcher.provider

class ProviderRateLimitException(
  message: String,
  cause: Throwable? = null,
) : RuntimeException(message, cause)
