package com.notivest.pricefetcher.client

import com.notivest.pricefetcher.models.CandleSeries
import com.notivest.pricefetcher.models.Quote
import com.notivest.pricefetcher.models.SymbolId
import com.notivest.pricefetcher.models.Timeframe
import java.time.Instant

interface MarketDataProvider {
  fun fetchQuotes(symbols: List<SymbolId>): List<Quote>

  fun fetchHistorical(
    symbol: SymbolId,
    from: Instant,
    to: Instant,
    timeframe: Timeframe,
  ): CandleSeries

  fun getName(): String
}
