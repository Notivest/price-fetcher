package com.notivest.pricefetcher.dto

import java.math.BigDecimal
import java.time.Instant

data class QuoteDto(
  val symbol: String,
  val last: BigDecimal,
  val ts: Instant,
  val open: BigDecimal?,
  val high: BigDecimal?,
  val low: BigDecimal?,
  val prevClose: BigDecimal?,
  val currency: String,
  val source: String,
  val stale: Boolean,
)
