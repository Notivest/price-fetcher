package com.notivest.pricefetcher.controllers

import com.notivest.pricefetcher.repositories.interfaces.CandleRepository
import com.notivest.pricefetcher.repositories.interfaces.QuoteRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController(
  private val quotes: QuoteRepository,
  private val candles: CandleRepository,
) {
  @GetMapping("/health")
  fun health(): ResponseEntity<Map<String, Any>> {
    return ResponseEntity.ok(
      mapOf(
        "status" to "UP",
        "cache" to
          mapOf(
            "quotes" to quotes.count(),
            "candles" to candles.count(),
          ),
      ),
    )
  }
}
