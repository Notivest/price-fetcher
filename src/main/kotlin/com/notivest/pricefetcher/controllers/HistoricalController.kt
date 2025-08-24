package com.notivest.pricefetcher.controllers

import com.notivest.pricefetcher.models.SymbolId
import com.notivest.pricefetcher.models.Timeframe
import com.notivest.pricefetcher.service.MarketDataService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.format.DateTimeParseException

@RestController
class HistoricalController(
  private val service: MarketDataService,
) {
  private val logger = LoggerFactory.getLogger(HistoricalController::class.java)

  @GetMapping("/historical")
  fun historical(
    @RequestParam symbol: String,
    @RequestParam from: String,
    @RequestParam to: String,
    @RequestParam tf: String = "T1D",
    @RequestParam(required = false, defaultValue = "true") adjusted: Boolean,
  ): ResponseEntity<Any> {
    return try {
      // Validate inputs
      if (symbol.isBlank()) {
        logger.warn("Empty symbol parameter provided")
        return ResponseEntity.badRequest().body(
          mapOf("error" to "symbol parameter is required and cannot be empty"),
        )
      }

      if (from.isBlank() || to.isBlank()) {
        logger.warn("Empty from/to parameters provided")
        return ResponseEntity.badRequest().body(
          mapOf("error" to "from and to parameters are required"),
        )
      }

      val sid = SymbolId.parse(symbol.trim())
      val timeframe = Timeframe.valueOf(tf.uppercase())
      val fromTs = Instant.parse(from)
      val toTs = Instant.parse(to)

      // Validate date range
      if (fromTs.isAfter(toTs)) {
        logger.warn("Invalid date range: from {} is after to {}", from, to)
        return ResponseEntity.badRequest().body(
          mapOf("error" to "from date must be before to date"),
        )
      }

      logger.debug(
        "Fetching historical data for {} from {} to {} with timeframe {}",
        symbol,
        from,
        to,
        timeframe,
      )

      val result = service.historical(sid, fromTs, toTs, timeframe, adjusted)
      ResponseEntity.ok(result)
    } catch (e: IllegalArgumentException) {
      logger.warn("Invalid argument: {}", e.message)
      ResponseEntity.badRequest().body(
        mapOf("error" to "Invalid argument: ${e.message}"),
      )
    } catch (e: DateTimeParseException) {
      logger.warn("Invalid date format: {}", e.message)
      ResponseEntity.badRequest().body(
        mapOf("error" to "Invalid date format. Use ISO-8601 format (e.g., 2024-01-01T00:00:00Z)"),
      )
    } catch (e: Exception) {
      logger.error("Unexpected error fetching historical data: {}", e.message, e)
      ResponseEntity.internalServerError().body(
        mapOf("error" to "Internal server error"),
      )
    }
  }
}
