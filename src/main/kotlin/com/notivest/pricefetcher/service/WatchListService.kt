package com.notivest.pricefetcher.service

import com.notivest.pricefetcher.models.SymbolId
import com.notivest.pricefetcher.models.WatchListItem
import com.notivest.pricefetcher.repositories.interfaces.WatchListRepository
import org.springframework.stereotype.Service

@Service
class WatchListService(private val repo: WatchListRepository) {
  fun list(): List<WatchListItem> = repo.all()

  fun add(item: WatchListItem) {
    require(item.symbol.isNotBlank()) { "symbol is required" }
    val ok = repo.add(item)
    require(ok) { "symbol already exists" }
  }

  fun patch(
    symbol: String,
    enabled: Boolean?,
    priority: Int?,
  ) {
    require(repo.update(symbol, enabled, priority)) { "symbol not found" }
  }

  fun delete(symbol: String) {
    require(repo.remove(symbol)) { "symbol not found" }
  }

  fun enabledSymbols(): List<SymbolId> = repo.all().filter { it.enabled }.map { SymbolId.parse(it.symbol) }
}
