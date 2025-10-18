package com.notivest.pricefetcher.service.strategy

import com.notivest.pricefetcher.models.CandleSeries
import com.notivest.pricefetcher.models.SymbolId
import com.notivest.pricefetcher.models.Timeframe
import java.time.Instant

interface HistoricalFetchingStrategy {
  fun fetch(
    symbol: SymbolId,
    from: Instant,
    to: Instant,
    timeframe: Timeframe,
  ): CandleSeries
}
