package com.notivest.pricefetcher.service

import com.notivest.pricefetcher.models.MarketClock
import com.notivest.pricefetcher.models.MarketClockPhase
import com.notivest.pricefetcher.models.RefreshPolicy
import com.notivest.pricefetcher.repositories.interfaces.QuoteRepository
import com.notivest.pricefetcher.service.strategy.QuoteFetchingStrategy
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class RefreshScheduler(
  private val watchlist: WatchListService,
  private val quoteFetchingStrategy: QuoteFetchingStrategy,
  private val quotes: QuoteRepository,
  private val marketClock: MarketClock,
  private val policy: RefreshPolicy,
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

      val snapshot = marketClock.snapshot(symbols)
      val openSymbols =
        symbols.filter { sym -> snapshot.phasesBySymbol[sym] != MarketClockPhase.NIGHT }

      if (openSymbols.isEmpty()) {
        logger.debug("All tracked markets are closed, skipping refresh to avoid unnecessary requests")
        return
      }

      val batch = policy.batchSize(snapshot.overallPhase)

      logger.debug(
        "Refreshing {} symbols with batch size {} during {} phase",
        openSymbols.size,
        batch,
        snapshot.overallPhase,
      )

      openSymbols.chunked(batch).forEach { chunk ->
        try {
          val data = quoteFetchingStrategy.fetch(chunk)
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
