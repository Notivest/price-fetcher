package com.notivest.price_fetcher.models

data class CandleSeries(
    val symbol : SymbolId,
    val timeframe: Timeframe,
    val items: List<Candle>
)