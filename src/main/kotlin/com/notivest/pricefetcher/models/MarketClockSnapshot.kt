package com.notivest.pricefetcher.models

import java.time.Instant

data class MarketClockSnapshot(
  val fetchedAt: Instant,
  val overallPhase: MarketClockPhase,
  val phasesBySymbol: Map<SymbolId, MarketClockPhase>,
  val timezoneBySymbol: Map<SymbolId, String?>,
)
