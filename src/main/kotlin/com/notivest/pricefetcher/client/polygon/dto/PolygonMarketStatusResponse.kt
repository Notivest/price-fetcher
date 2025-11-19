package com.notivest.pricefetcher.client.polygon.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class PolygonMarketStatusResponse(
  val market: String? = null,
  @JsonProperty("serverTime") val serverTime: String? = null,
  val exchanges: Map<String, String> = emptyMap(),
  val currencies: Map<String, String> = emptyMap(),
  val status: String? = null,
)
