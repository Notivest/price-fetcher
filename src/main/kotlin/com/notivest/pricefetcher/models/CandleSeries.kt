package com.notivest.pricefetcher.models

data class CandleSeries(
  val symbol: SymbolId,
  val timeframe: Timeframe,
  val items: List<Candle>,
)
