package com.notivest.price_fetcher.client

import com.notivest.price_fetcher.models.CandleSeries
import com.notivest.price_fetcher.models.Quote
import com.notivest.price_fetcher.models.SymbolId
import com.notivest.price_fetcher.models.Timeframe
import java.time.Instant

interface MarketDataProvider {
    fun fetchQuotes(symbols : List<SymbolId>) : List<Quote>
    fun fetchHistorical(symbol: SymbolId, from: Instant, to: Instant, timeframe: Timeframe): CandleSeries
    fun getName() : String
}