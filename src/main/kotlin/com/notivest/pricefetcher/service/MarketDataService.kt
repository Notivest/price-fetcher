package com.notivest.pricefetcher.service

import com.notivest.pricefetcher.dto.QuoteDto
import com.notivest.pricefetcher.models.CandleSeries
import com.notivest.pricefetcher.models.Quote
import com.notivest.pricefetcher.models.SymbolId
import com.notivest.pricefetcher.models.Timeframe
import com.notivest.pricefetcher.repositories.interfaces.CandleRepository
import com.notivest.pricefetcher.repositories.interfaces.QuoteRepository
import com.notivest.pricefetcher.service.strategy.HistoricalFetchingStrategy
import com.notivest.pricefetcher.service.strategy.QuoteFetchingStrategy
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Service
class MarketDataService(
  private val quoteFetchingStrategy: QuoteFetchingStrategy,
  private val historicalFetchingStrategy: HistoricalFetchingStrategy,
  private val quotes: QuoteRepository,
  private val candles: CandleRepository,
) {
  private val dailyCoverage = ConcurrentHashMap<SymbolId, RequestedRange>()
  private val historicalLocks = ConcurrentHashMap<Pair<SymbolId, Timeframe>, ReentrantLock>()

  fun prefetch(symbols: List<SymbolId>): Int {
    val list = quoteFetchingStrategy.fetch(symbols)
    list.forEach(quotes::put)
    return list.size
  }

  fun getQuotes(ids: List<SymbolId>): List<QuoteDto> =
    ids
      .also { ensureQuotesCached(it) }
      .map { id ->
        val (q, stale) = quotes.get(id)
        q?.toDto(stale) ?: throw NoSuchElementException("No quote for $id")
      }

  fun historical(
    symbol: SymbolId,
    from: Instant,
    to: Instant,
    tf: Timeframe,
    adjusted: Boolean,
  ): CandleSeries {
    val lock = historicalLocks.computeIfAbsent(symbol to tf) { ReentrantLock() }
    val canonicalSeries =
      lock.withLock {
        when (tf) {
          Timeframe.T1D -> historicalDailyIncremental(symbol, from, to)
          else -> historicalDefault(symbol, from, to, tf)
        }
      }
    return applyAdjusted(canonicalSeries, adjusted)
  }

  private fun ensureQuotesCached(symbols: Collection<SymbolId>) {
    val symbolsToRefresh =
      symbols.filter { symbol ->
        val (quote, stale) = quotes.get(symbol)
        quote == null || stale
      }
    if (symbolsToRefresh.isNotEmpty()) {
      prefetch(symbolsToRefresh)
    }
  }

  private fun historicalDefault(
    symbol: SymbolId,
    from: Instant,
    to: Instant,
    timeframe: Timeframe,
  ): CandleSeries {
    val cached = candles.get(symbol, timeframe)
    if (cached != null && coversRange(cached, from, to)) {
      return selectRange(cached, from, to)
    }

    val fetched = historicalFetchingStrategy.fetch(symbol, from, to, timeframe)
    val canonical = canonicalSeries(fetched)
    candles.put(canonical)
    return selectRange(canonical, from, to)
  }

  private fun historicalDailyIncremental(
    symbol: SymbolId,
    from: Instant,
    to: Instant,
  ): CandleSeries {
    var hydrated = candles.get(symbol, Timeframe.T1D)
    var coverage = dailyCoverage[symbol]

    if ((hydrated == null || hydrated.items.isEmpty()) && coverage != null && isCovered(coverage, from, to)) {
      return CandleSeries(symbol = symbol, timeframe = Timeframe.T1D, items = emptyList())
    }

    if (hydrated == null || hydrated.items.isEmpty()) {
      val fetched = historicalFetchingStrategy.fetch(symbol, from, to, Timeframe.T1D)
      val canonical = canonicalSeries(fetched)
      candles.put(canonical)
      dailyCoverage[symbol] = RequestedRange(from = from, to = to)
      return selectRange(canonical, from, to)
    }

    if (coverage == null) {
      coverage =
        RequestedRange(
          from = hydrated.items.first().ts,
          to = hydrated.items.last().ts,
        )
    }

    if (from.isBefore(coverage.from)) {
      val backfill = historicalFetchingStrategy.fetch(symbol, from, coverage.from, Timeframe.T1D)
      hydrated = candles.append(symbol, Timeframe.T1D, canonicalCandles(backfill.items), DAILY_CANDLE_MAX_WINDOW)
      coverage = coverage.copy(from = from)
    }

    if (to.isAfter(coverage.to)) {
      val forwardFill = historicalFetchingStrategy.fetch(symbol, coverage.to, to, Timeframe.T1D)
      hydrated = candles.append(symbol, Timeframe.T1D, canonicalCandles(forwardFill.items), DAILY_CANDLE_MAX_WINDOW)
      coverage = coverage.copy(to = to)
    }

    dailyCoverage[symbol] = coverage
    return selectRange(hydrated, from, to)
  }

  private fun selectRange(
    series: CandleSeries,
    from: Instant,
    to: Instant,
  ): CandleSeries =
    series.copy(
      items =
        series.items.filter { candle ->
          !candle.ts.isBefore(from) && !candle.ts.isAfter(to)
        },
    )

  private fun coversRange(
    series: CandleSeries,
    from: Instant,
    to: Instant,
  ): Boolean {
    if (series.items.isEmpty()) return false
    val first = series.items.first().ts
    val last = series.items.last().ts
    return !from.isBefore(first) && !to.isAfter(last)
  }

  private fun isCovered(
    coverage: RequestedRange,
    from: Instant,
    to: Instant,
  ): Boolean = !from.isBefore(coverage.from) && !to.isAfter(coverage.to)

  private fun canonicalSeries(series: CandleSeries): CandleSeries =
    series.copy(
      items = canonicalCandles(series.items),
    )

  private fun canonicalCandles(items: List<com.notivest.pricefetcher.models.Candle>) =
    items
      .sortedBy { it.ts }
      .map { candle ->
        if (candle.adjusted) candle else candle.copy(adjusted = true)
      }

  private fun applyAdjusted(
    series: CandleSeries,
    adjusted: Boolean,
  ): CandleSeries = series.copy(items = series.items.map { it.copy(adjusted = adjusted) })

  private fun Quote.toDto(stale: Boolean) =
    QuoteDto(
      symbol = symbol.toString(),
      last = last,
      ts = ts,
      open = open,
      high = high,
      low = low,
      prevClose = prevClose,
      currency = currency,
      source = source,
      stale = stale,
    )

  companion object {
    private const val DAILY_CANDLE_MAX_WINDOW = 10_000
  }

  private data class RequestedRange(
    val from: Instant,
    val to: Instant,
  )
}
