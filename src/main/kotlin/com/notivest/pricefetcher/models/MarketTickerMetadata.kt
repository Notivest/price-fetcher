package com.notivest.pricefetcher.models

data class MarketTickerMetadata(
  val marketKind: MarketKind,
  val exchangeKey: String?,
  val timezone: String?,
)
