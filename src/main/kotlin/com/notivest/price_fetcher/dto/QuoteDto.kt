package com.notivest.price_fetcher.dto

import com.notivest.price_fetcher.models.SymbolId
import java.math.BigDecimal
import java.time.Instant

data class QuoteDto(
    val symbol : String,
    val last: BigDecimal,
    val ts: Instant,
    val open: BigDecimal?,
    val high: BigDecimal?,
    val low: BigDecimal?,
    val prevClose: BigDecimal?,
    val currency: String,
    val source : String,
    val stale : Boolean
)