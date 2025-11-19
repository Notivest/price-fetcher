package com.notivest.pricefetcher.security

import java.time.Instant

data class AuthenticationErrorResponse(
  val error: String,
  val message: String,
  val details: String,
  val timestamp: String = Instant.now().toString(),
  val status: Int = 401,
)
