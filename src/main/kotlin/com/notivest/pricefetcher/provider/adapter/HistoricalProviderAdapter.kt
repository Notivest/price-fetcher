package com.notivest.pricefetcher.provider.adapter

import com.notivest.pricefetcher.models.CandleSeries
import com.notivest.pricefetcher.models.SymbolId
import com.notivest.pricefetcher.models.Timeframe
import java.time.Instant

/**
 * Adapter responsible for obtaining and mapping historical candles from an external API.
 */
interface HistoricalProviderAdapter {
  fun fetchHistorical(
    symbol: SymbolId,
    from: Instant,
    to: Instant,
    timeframe: Timeframe,
  ): CandleSeries

  fun getName(): String
}
