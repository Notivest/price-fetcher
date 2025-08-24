package com.notivest.pricefetcher.repositories.impl

import com.notivest.pricefetcher.models.Candle
import com.notivest.pricefetcher.models.CandleSeries
import com.notivest.pricefetcher.models.SymbolId
import com.notivest.pricefetcher.models.Timeframe
import com.notivest.pricefetcher.repositories.interfaces.CandleRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Repository
class InMemoryCandleRepository() : CandleRepository {
  private val store = ConcurrentHashMap<Pair<SymbolId, Timeframe>, List<Candle>>()

  override fun put(series: CandleSeries) {
    val key = series.symbol to series.timeframe
    store[key] = series.items.sortedBy { it.ts }
  }

  override fun get(
    symbol: SymbolId,
    timeframe: Timeframe,
  ): CandleSeries? {
    val key = symbol to timeframe
    val items = store[key] ?: return null
    return CandleSeries(symbol, timeframe, items)
  }

  override fun append(
    symbol: SymbolId,
    timeframe: Timeframe,
    newItems: List<Candle>,
    maxWindow: Int,
  ): CandleSeries {
    val key = symbol to timeframe
    val normalizedNew = newItems.filter { it.ts != Instant.EPOCH }.sortedBy { it.ts }

    store.compute(key) { _, existing ->
      val merged = mergeAndLimit(existing.orEmpty(), normalizedNew, maxWindow)
      merged
    }

    val items = store[key].orEmpty()

    return CandleSeries(symbol, timeframe, items)
  }

  override fun count(): Int = store.size

  override fun size(
    symbol: SymbolId,
    timeframe: Timeframe,
  ): Int = store[symbol to timeframe]?.size ?: 0

  private fun mergeAndLimit(
    existing: List<Candle>,
    incoming: List<Candle>,
    maxWindow: Int,
  ): List<Candle> {
    if (incoming.isEmpty()) return existing
    val byTs = LinkedHashMap<Instant, Candle>(existing.size + incoming.size)

    // Carga existente
    existing.forEach { byTs[it.ts] = it }
    // Aplica incoming (overwrites)
    incoming.forEach { byTs[it.ts] = it }

    // Ordena por ts asc y limita a ventana
    val ordered = byTs.values.sortedBy { it.ts }
    return if (ordered.size <= maxWindow) {
      ordered
    } else {
      ordered.takeLast(maxWindow)
    }
  }
}
