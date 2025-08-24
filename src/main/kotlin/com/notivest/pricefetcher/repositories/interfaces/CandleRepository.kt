package com.notivest.pricefetcher.repositories.interfaces

import com.notivest.pricefetcher.models.Candle
import com.notivest.pricefetcher.models.CandleSeries
import com.notivest.pricefetcher.models.SymbolId
import com.notivest.pricefetcher.models.Timeframe

interface CandleRepository {
  fun put(series: CandleSeries)

  fun get(
    symbol: SymbolId,
    timeframe: Timeframe,
  ): CandleSeries?

  fun append(
    symbol: SymbolId,
    timeframe: Timeframe,
    newItems: List<Candle>,
    maxWindow: Int = 1000,
  ): CandleSeries

  fun count(): Int

  fun size(
    symbol: SymbolId,
    timeframe: Timeframe,
  ): Int
}
