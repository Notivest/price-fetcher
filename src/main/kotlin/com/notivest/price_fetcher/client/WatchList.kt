package com.notivest.price_fetcher.client

import com.notivest.price_fetcher.models.WatchListItem
import org.springframework.stereotype.Component


@Component
class WatchList {
    fun getSymbols() : List<WatchListItem> = listOf(
        WatchListItem("AAPL"),
        WatchListItem("MSFT"),
        WatchListItem("TSLA"),
    )

}