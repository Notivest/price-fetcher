package com.notivest.pricefetcher.repositories.interfaces

import com.notivest.pricefetcher.models.WatchListItem

interface WatchListRepository {
  fun all(): List<WatchListItem>

  fun add(item: WatchListItem): Boolean

  fun update(
    symbol: String,
    enabled: Boolean?,
    priority: Int?,
  ): Boolean

  fun remove(symbol: String): Boolean

  fun contains(symbol: String): Boolean
}
