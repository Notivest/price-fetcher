package com.notivest.pricefetcher.service

import com.notivest.pricefetcher.client.ProviderFactory
import com.notivest.pricefetcher.dto.QuoteDto
import com.notivest.pricefetcher.models.CandleSeries
import com.notivest.pricefetcher.models.Quote
import com.notivest.pricefetcher.models.SymbolId
import com.notivest.pricefetcher.models.Timeframe
import com.notivest.pricefetcher.repositories.interfaces.CandleRepository
import com.notivest.pricefetcher.repositories.interfaces.QuoteRepository
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class MarketDataService(
  private val providerFactory: ProviderFactory,
  private val quotes: QuoteRepository,
  private val candles: CandleRepository,
  private val watchList: WatchListService,
) {
  fun prefetch(symbols: List<SymbolId>): Int {
    val provider = providerFactory.primary()
    val list = provider.fetchQuotes(symbols)
    list.forEach(quotes::put)
    return list.size
  }

  fun getQuotes(ids: List<SymbolId>): List<QuoteDto> =
    ids
      .also { ensureTracked(it) }
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
    ensureTracked(listOf(symbol))
    ensureQuotesCached(listOf(symbol))
    candles.get(symbol, tf)?.let { return it }
    val cs = providerFactory.primary().fetchHistorical(symbol, from, to, tf)
    val adj = cs.copy(items = cs.items.map { it.copy(adjusted = adjusted) })
    candles.put(adj)
    return adj
  }

  private fun ensureTracked(symbols: Collection<SymbolId>) {
    symbols.forEach(watchList::ensureEnabled)
  }

  private fun ensureQuotesCached(symbols: Collection<SymbolId>) {
    val missing = symbols.filter { (quotes.get(it).first == null) }
    if (missing.isNotEmpty()) {
      prefetch(missing)
    }
  }

  private fun Quote.toDto(stale: Boolean) =
    QuoteDto(
      symbol = symbol.toString(),
      last = last, ts = ts, open = open, high = high, low = low, prevClose = prevClose,
      currency = currency, source = source, stale = stale,
    )
}
