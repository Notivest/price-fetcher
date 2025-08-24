package com.notivest.pricefetcher.controllers

import com.notivest.pricefetcher.service.MarketDataService
import com.notivest.pricefetcher.service.WatchListService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class PrefetchController(
  private val watchListService: WatchListService,
  private val marketDataService: MarketDataService,
) {
  private val logger = LoggerFactory.getLogger(PrefetchController::class.java)

  @PostMapping("/prefetch")
  fun prefetch(): ResponseEntity<Map<String, Any>> {
    return try {
      val enabledSymbols = watchListService.enabledSymbols()

      if (enabledSymbols.isEmpty()) {
        logger.warn("No enabled symbols found for prefetch")
        return ResponseEntity.ok(
          mapOf(
            "prefetched" to 0,
            "symbols" to emptyList<String>(),
            "message" to "No enabled symbols in watchlist",
          ),
        )
      }

      logger.info("Starting prefetch for {} symbols", enabledSymbols.size)
      val count = marketDataService.prefetch(enabledSymbols)

      logger.info("Successfully prefetched {} quotes", count)
      ResponseEntity.ok(
        mapOf(
          "prefetched" to count,
          "symbols" to enabledSymbols.map { it.toString() },
        ),
      )
    } catch (e: Exception) {
      logger.error("Error during prefetch: {}", e.message, e)
      ResponseEntity.internalServerError().body(
        mapOf(
          "error" to "Prefetch failed",
          "message" to (e.message ?: "Unknown error"),
        ),
      )
    }
  }
}
