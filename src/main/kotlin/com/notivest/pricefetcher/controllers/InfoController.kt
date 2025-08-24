package com.notivest.pricefetcher.controllers

import com.notivest.pricefetcher.models.Timeframe
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class InfoController {
  @GetMapping("/info/timeframes")
  fun getTimeframes(): ResponseEntity<Map<String, Any>> {
    return ResponseEntity.ok(
      mapOf(
        "available_timeframes" to
          Timeframe.values().map { tf ->
            mapOf(
              "value" to tf.name,
              "description" to
                when (tf) {
                  Timeframe.T1M -> "1 minute"
                  Timeframe.T5M -> "5 minutes"
                  Timeframe.T15M -> "15 minutes"
                  Timeframe.T1H -> "1 hour"
                  Timeframe.T1D -> "1 day"
                },
            )
          },
        "default" to "T1D",
        "usage" to "Use the 'value' field as the 'tf' parameter in /historical endpoint",
      ),
    )
  }

  @GetMapping("/info/endpoints")
  fun getEndpoints(): ResponseEntity<Map<String, Any>> {
    return ResponseEntity.ok(
      mapOf(
        "endpoints" to
          listOf(
            mapOf(
              "path" to "/health",
              "method" to "GET",
              "description" to "Application health and cache status",
            ),
            mapOf(
              "path" to "/quotes",
              "method" to "GET",
              "description" to "Get current quotes for symbols",
              "parameters" to
                mapOf(
                  "symbols" to "Comma-separated list of symbols (e.g., AAPL,TSLA,MSFT.MX)",
                ),
            ),
            mapOf(
              "path" to "/historical",
              "method" to "GET",
              "description" to "Get historical candle data",
              "parameters" to
                mapOf(
                  "symbol" to "Single symbol (e.g., AAPL)",
                  "from" to "Start date in ISO-8601 format (e.g., 2024-01-01T00:00:00Z)",
                  "to" to "End date in ISO-8601 format (e.g., 2024-01-02T00:00:00Z)",
                  "tf" to "Timeframe (optional, default: T1D)",
                  "adjusted" to "Whether to use adjusted prices (optional, default: true)",
                ),
            ),
            mapOf(
              "path" to "/prefetch",
              "method" to "POST",
              "description" to "Force fetch current quotes for all enabled watchlist symbols",
            ),
            mapOf(
              "path" to "/watchlist",
              "method" to "GET",
              "description" to "Get all symbols in watchlist",
            ),
            mapOf(
              "path" to "/watchlist",
              "method" to "POST",
              "description" to "Add symbol to watchlist",
              "body" to
                mapOf(
                  "symbol" to "Symbol to add (required)",
                  "enabled" to "Whether symbol is enabled (optional, default: true)",
                  "priority" to "Symbol priority (optional)",
                ),
            ),
            mapOf(
              "path" to "/watchlist/{symbol}",
              "method" to "PATCH",
              "description" to "Update symbol in watchlist",
              "body" to
                mapOf(
                  "enabled" to "Whether symbol is enabled (optional)",
                  "priority" to "Symbol priority (optional)",
                ),
            ),
            mapOf(
              "path" to "/watchlist/{symbol}",
              "method" to "DELETE",
              "description" to "Remove symbol from watchlist",
            ),
          ),
      ),
    )
  }
}
