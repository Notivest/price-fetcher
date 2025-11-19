package com.notivest.pricefetcher.provider.polygon.model

data class PolygonAggPayload(
  val results: List<PolygonAggResultPayload> = emptyList(),
)
