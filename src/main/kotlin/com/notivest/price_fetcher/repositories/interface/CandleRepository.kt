package com.notivest.price_fetcher.repositories.`interface`

import com.notivest.price_fetcher.models.Candle
import com.notivest.price_fetcher.models.CandleSeries
import com.notivest.price_fetcher.models.SymbolId
import com.notivest.price_fetcher.models.Timeframe

interface CandleRepository {
    fun put(series: CandleSeries)
    fun get(symbol: SymbolId, timeframe: Timeframe) : CandleSeries?
    fun append(symbol: SymbolId, timeframe: Timeframe, newItems: List<Candle>, maxWindow: Int = 1000): CandleSeries
    fun count() : Int
    fun size(symbol: SymbolId, timeframe: Timeframe): Int
}