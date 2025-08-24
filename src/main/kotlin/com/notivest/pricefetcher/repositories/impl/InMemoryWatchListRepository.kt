package com.notivest.pricefetcher.repositories.impl

import com.notivest.pricefetcher.models.WatchListItem
import com.notivest.pricefetcher.repositories.interfaces.WatchListRepository
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

@Repository
class InMemoryWatchListRepository : WatchListRepository {
  private val map = ConcurrentHashMap<String, WatchListItem>() // key = symbol normalizado

  override fun all(): List<WatchListItem> {
    return map.values.sortedWith(compareBy<WatchListItem> { it.priority ?: Int.MAX_VALUE }.thenBy { it.symbol })
  }

  override fun add(item: WatchListItem): Boolean {
    val key = normalize(item.symbol)
    return map.putIfAbsent(key, item.copy(symbol = key)) == null
  }

  override fun update(
    symbol: String,
    enabled: Boolean?,
    priority: Int?,
  ): Boolean {
    val key = normalize(symbol)
    return map.computeIfPresent(key) { _, old ->
      old.copy(
        enabled = enabled ?: old.enabled,
        priority = priority ?: old.priority,
      )
    } != null
  }

  override fun remove(symbol: String): Boolean {
    return map.remove(normalize(symbol)) != null
  }

  override fun contains(symbol: String): Boolean {
    return map.containsKey(normalize(symbol))
  }

  private fun normalize(s: String) = s.trim().uppercase()
}
