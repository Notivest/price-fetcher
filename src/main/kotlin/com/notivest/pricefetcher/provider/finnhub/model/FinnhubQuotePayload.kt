package com.notivest.pricefetcher.provider.finnhub.model

data class FinnhubQuotePayload(
  val c: Double? = null,
  val o: Double? = null,
  val h: Double? = null,
  val l: Double? = null,
  val pc: Double? = null,
  val t: Long? = null,
)
