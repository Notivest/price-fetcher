package com.notivest.pricefetcher.controllers

import com.notivest.pricefetcher.models.MarketClock
import com.notivest.pricefetcher.models.MarketClockPhase
import com.notivest.pricefetcher.repositories.interfaces.CandleRepository
import com.notivest.pricefetcher.repositories.interfaces.QuoteRepository
import com.notivest.pricefetcher.service.WatchListService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
class HealthController(
  private val quotes: QuoteRepository,
  private val candles: CandleRepository,
  private val watchListService: WatchListService,
  private val marketClock: MarketClock,
) {
  @GetMapping("/health")
  fun health(): ResponseEntity<Map<String, Any>> {
    val enabledSymbols = watchListService.enabledSymbols()
    val snapshot = marketClock.snapshot(enabledSymbols)
    val allSymbols = watchListService.list()

    val openSymbols =
      snapshot.phasesBySymbol.filterValues { it != MarketClockPhase.NIGHT }.keys.map { it.toString() }
    val closedSymbols =
      snapshot.phasesBySymbol.filterValues { it == MarketClockPhase.NIGHT }.keys.map { it.toString() }

    return ResponseEntity.ok(
      mapOf(
        "status" to "UP",
        "timestamp" to Instant.now(),
        "market" to
          mapOf(
            "phase" to snapshot.overallPhase.name,
            "fetched_at" to snapshot.fetchedAt,
            "open_symbols" to openSymbols,
            "closed_symbols" to closedSymbols,
            "timezones" to snapshot.timezoneBySymbol.mapKeys { it.key.toString() },
          ),
        "cache" to
          mapOf(
            "quotes" to quotes.count(),
            "candles" to candles.count(),
            "symbols" to quotes.symbols().map { it.toString() },
          ),
        "watchlist" to
          mapOf(
            "total" to allSymbols.size,
            "enabled" to enabledSymbols.size,
            "disabled" to allSymbols.size - enabledSymbols.size,
            "enabled_symbols" to enabledSymbols.map { it.toString() },
          ),
        "version" to
          mapOf(
            "app" to "price-fetcher",
            "version" to "0.0.1-SNAPSHOT",
          ),
      ),
    )
  }
}
