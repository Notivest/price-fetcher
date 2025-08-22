package com.notivest.price_fetcher.models

import java.math.BigDecimal
import java.time.Instant

data class Quote(
    val symbol: SymbolId,
    val last : BigDecimal,
    val open: BigDecimal?,
    val high : BigDecimal?,
    val low : BigDecimal?,
    val prevClose: BigDecimal?,
    val currency: String = "USD",
    val source: String = "FAKE",
    val ts: Instant = Instant.now()
)