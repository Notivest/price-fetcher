package com.notivest.pricefetcher.repositories.impl

import com.notivest.pricefetcher.models.Quote
import com.notivest.pricefetcher.models.SymbolId
import com.notivest.pricefetcher.repositories.interfaces.QuoteRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Repository
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Repository
class InMemoryQuoteRepository(
  @Value("\${pricefetcher.quotes.ttl-seconds:120}") ttlSeconds: Long,
) : QuoteRepository {
  private val quotes = ConcurrentHashMap<SymbolId, Quote>()
  private val freshness = ConcurrentHashMap<SymbolId, Instant>()
  private val ttl: Duration = Duration.ofSeconds(ttlSeconds)

  override fun put(quote: Quote) {
    quotes[quote.symbol] = quote
    freshness[quote.symbol] = Instant.now()
  }

  override fun get(symbol: SymbolId): Pair<Quote?, Boolean> {
    val q = quotes[symbol]
    val t = freshness[symbol]
    val stale = t == null || Duration.between(t, Instant.now()) > ttl
    return q to stale
  }

  override fun count(): Int = quotes.size

  override fun symbols(): Set<SymbolId> = quotes.keys
}
