package com.notivest.pricefetcher.client

import com.notivest.pricefetcher.models.WatchListItem
import org.springframework.stereotype.Component

@Component
class WatchList {
  fun getSymbols(): List<WatchListItem> =
    listOf(
      WatchListItem("AAPL"),
      WatchListItem("MSFT"),
      WatchListItem("TSLA"),
    )
}
