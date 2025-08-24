package com.notivest.pricefetcher.service

import com.notivest.pricefetcher.client.ProviderFactory
import com.notivest.pricefetcher.models.MarketClock
import com.notivest.pricefetcher.models.RefreshPolicy
import com.notivest.pricefetcher.repositories.interfaces.QuoteRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class RefreshScheduler(
  private val watchlist: WatchListService,
  private val providerFactory: ProviderFactory,
  private val quotes: QuoteRepository,
  private val marketClock: MarketClock,
  private val policy: RefreshPolicy,
  @Value("\${pricefetcher.quotes.refresh-ms:2000}") private val refreshMs: Long,
) {
  private val logger = LoggerFactory.getLogger(RefreshScheduler::class.java)

  @Scheduled(fixedDelayString = "\${pricefetcher.quotes.refresh-ms:2000}")
  fun tick() {
    try {
      val symbols = watchlist.enabledSymbols()
      if (symbols.isEmpty()) {
        logger.debug("No enabled symbols found, skipping refresh")
        return
      }

      val provider = providerFactory.primary()
      val phase = marketClock.phase()
      val batch = policy.batchSize(phase)

      logger.debug(
        "Refreshing {} symbols with batch size {} during {} phase",
        symbols.size,
        batch,
        phase,
      )

      symbols.chunked(batch).forEach { chunk ->
        try {
          val data = provider.fetchQuotes(chunk)
          data.forEach(quotes::put)
          logger.debug("Successfully refreshed {} quotes for chunk", data.size)
        } catch (e: Exception) {
          logger.warn(
            "Failed to fetch quotes for chunk {}: {}",
            chunk.map { it.toString() },
            e.message,
          )
        }
      }
    } catch (e: Exception) {
      logger.error("Error in scheduler tick: {}", e.message, e)
    }
  }
}
