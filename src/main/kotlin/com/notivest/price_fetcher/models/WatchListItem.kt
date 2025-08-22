package com.notivest.price_fetcher.models

data class WatchListItem(
    val symbol: String,
    val enabled: Boolean = true,
    val priority: Int? = null
)