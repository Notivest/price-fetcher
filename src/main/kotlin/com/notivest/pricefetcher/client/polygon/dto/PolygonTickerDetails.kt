package com.notivest.pricefetcher.client.polygon.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class PolygonTickerDetails(
  val ticker: String? = null,
  val market: String? = null,
  val locale: String? = null,
  @JsonProperty("primary_exchange") val primaryExchange: String? = null,
  val type: String? = null,
  val session: PolygonTradingSession? = null,
  val timezone: PolygonTimezoneDescriptor? = null,
)
