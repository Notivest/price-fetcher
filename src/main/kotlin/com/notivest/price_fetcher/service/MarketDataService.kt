package com.notivest.price_fetcher.service

import com.notivest.price_fetcher.client.ProviderFactory
import com.notivest.price_fetcher.dto.QuoteDto
import com.notivest.price_fetcher.models.CandleSeries
import com.notivest.price_fetcher.models.Quote
import com.notivest.price_fetcher.models.SymbolId
import com.notivest.price_fetcher.models.Timeframe
import com.notivest.price_fetcher.repositories.`interface`.CandleRepository
import com.notivest.price_fetcher.repositories.`interface`.QuoteRepository
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class MarketDataService(
    private val providerFactory: ProviderFactory,
    private val quotes: QuoteRepository,
    private val candles: CandleRepository
) {
    fun prefetch(symbols: List<SymbolId>): Int {
        val provider = providerFactory.primary()
        val list = provider.fetchQuotes(symbols)
        list.forEach(quotes::put)
        return list.size
    }

    fun getQuotes(ids: List<SymbolId>): List<QuoteDto> =
        ids.map { id ->
            val (q, stale) = quotes.get(id)
            q?.toDto(stale) ?: throw NoSuchElementException("No quote for $id")
        }

    fun historical(symbol: SymbolId, from: Instant, to: Instant, tf: Timeframe, adjusted: Boolean): CandleSeries {
        candles.get(symbol, tf)?.let { return it }
        val cs = providerFactory.primary().fetchHistorical(symbol, from, to, tf)
        val adj = cs.copy(items = cs.items.map { it.copy(adjusted = adjusted) })
        candles.put(adj)
        return adj
    }

    private fun Quote.toDto(stale: Boolean) = QuoteDto(
        symbol = symbol.toString(),
        last = last, ts = ts, open = open, high = high, low = low, prevClose = prevClose,
        currency = currency, source = source, stale = stale
    )
}