package com.notivest.pricefetcher.service

import com.notivest.pricefetcher.models.MarketClock
import com.notivest.pricefetcher.models.MarketClockPhase
import com.notivest.pricefetcher.models.RefreshPolicy
import com.notivest.pricefetcher.observability.CorrelationContext
import com.notivest.pricefetcher.observability.PriceFetcherMetrics
import com.notivest.pricefetcher.repositories.interfaces.QuoteRepository
import com.notivest.pricefetcher.service.strategy.QuoteFetchingStrategy
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class RefreshScheduler(
  private val watchlist: WatchListService,
  private val quoteFetchingStrategy: QuoteFetchingStrategy,
  private val quotes: QuoteRepository,
  private val marketClock: MarketClock,
  private val policy: RefreshPolicy,
  private val metrics: PriceFetcherMetrics,
) {
  private val logger = LoggerFactory.getLogger(RefreshScheduler::class.java)

  @Scheduled(fixedDelayString = "\${pricefetcher.quotes.refresh-ms:2000}")
  fun tick() {
    val startedAt = Instant.now()
    val existingCorrelationId = CorrelationContext.currentCorrelationId()
    if (existingCorrelationId == null) {
      CorrelationContext.setCorrelationId(UUID.randomUUID().toString())
    }

    var outcome = "failed"
    var phase = "unknown"
    var trackedSymbols = 0
    var staleSymbolsCount = 0

    try {
      val symbols = watchlist.enabledSymbols()
      trackedSymbols = symbols.size
      if (symbols.isEmpty()) {
        logger.debug("No enabled symbols found, skipping refresh")
        outcome = "skipped_empty_watchlist"
        return
      }

      val snapshot = marketClock.snapshot(symbols)
      phase = snapshot.overallPhase.name
      val openSymbols =
        symbols.filter { sym -> snapshot.phasesBySymbol[sym] != MarketClockPhase.NIGHT }

      if (openSymbols.isEmpty()) {
        logger.debug("All tracked markets are closed, skipping refresh to avoid unnecessary requests")
        outcome = "skipped_markets_closed"
        return
      }

      val staleOrMissingSymbols =
        openSymbols.filter { symbol ->
          val (quote, stale) = quotes.get(symbol)
          quote == null || stale
        }
      staleSymbolsCount = staleOrMissingSymbols.size

      if (staleOrMissingSymbols.isEmpty()) {
        logger.debug("All tracked quotes are fresh, skipping refresh tick")
        outcome = "skipped_quotes_fresh"
        return
      }

      val batch = policy.batchSize(snapshot.overallPhase)

      logger.debug(
        "Refreshing {} symbols with batch size {} during {} phase",
        staleOrMissingSymbols.size,
        batch,
        snapshot.overallPhase,
      )

      staleOrMissingSymbols.chunked(batch).forEach { chunk ->
        try {
          val data = quoteFetchingStrategy.fetch(chunk)
          data.forEach(quotes::put)
          metrics.recordBatchResult(
            outcome = "success",
            phase = phase,
            requestedSymbols = chunk.size,
            refreshedQuotes = data.size,
          )
          logger.debug("Successfully refreshed {} quotes for chunk", data.size)
        } catch (e: Exception) {
          metrics.recordBatchResult(
            outcome = "failed",
            phase = phase,
            requestedSymbols = chunk.size,
            refreshedQuotes = 0,
          )
          logger.warn(
            "Failed to fetch quotes for chunk {}: {}",
            chunk.map { it.toString() },
            e.message,
          )
        }
      }
      outcome = "completed"
    } catch (e: Exception) {
      logger.error("Error in scheduler tick: {}", e.message, e)
      outcome = "failed"
    } finally {
      metrics.recordSchedulerTick(
        outcome = outcome,
        phase = phase,
        trackedSymbols = trackedSymbols,
        staleSymbols = staleSymbolsCount,
        duration = Duration.between(startedAt, Instant.now()),
      )

      if (existingCorrelationId == null) {
        CorrelationContext.clear()
      }
    }
  }
}
