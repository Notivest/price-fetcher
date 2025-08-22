package com.notivest.price_fetcher.models

import java.math.BigDecimal
import java.time.Instant

data class Candle(
    val ts: Instant,
    val o: BigDecimal,
    val h: BigDecimal,
    val l: BigDecimal,
    val c: BigDecimal,
    val v: Long,
    val adjusted: Boolean = true
)