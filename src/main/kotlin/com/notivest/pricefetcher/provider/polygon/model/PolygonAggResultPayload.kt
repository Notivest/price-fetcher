package com.notivest.pricefetcher.provider.polygon.model

data class PolygonAggResultPayload(
  val t: Long,
  val o: Double,
  val h: Double,
  val l: Double,
  val c: Double,
  val v: Long,
)
