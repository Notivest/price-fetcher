package com.notivest.pricefetcher.service

import com.notivest.pricefetcher.client.ProviderFactory
import com.notivest.pricefetcher.models.MarketClock
import com.notivest.pricefetcher.models.RefreshPolicy
import com.notivest.pricefetcher.repositories.interfaces.QuoteRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled

class RefreshScheduler(
  private val watchlist: WatchListService,
  private val providerFactory: ProviderFactory,
  private val quotes: QuoteRepository,
  private val marketClock: MarketClock,
  private val policy: RefreshPolicy,
  @Value("\${pricefetcher.quotes.refresh-ms:2000}") private val refreshMs: Long,
) {
  @Scheduled(fixedDelayString = "\${pricefetcher.quotes.refresh-ms:2000}")
  fun tick() {
    val symbols = watchlist.enabledSymbols()
    if (symbols.isEmpty()) return

    val provider = providerFactory.primary()
    val batch = policy.batchSize(marketClock.phase())

    symbols.chunked(batch).forEach { chunk ->
      val data = provider.fetchQuotes(chunk)
      data.forEach(quotes::put)
    }
  }
}
