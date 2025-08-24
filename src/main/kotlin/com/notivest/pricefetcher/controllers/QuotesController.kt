package com.notivest.pricefetcher.controllers

import com.notivest.pricefetcher.models.SymbolId
import com.notivest.pricefetcher.service.MarketDataService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class QuotesController(
  private val service: MarketDataService,
) {
  private val logger = LoggerFactory.getLogger(QuotesController::class.java)

  @GetMapping("/quotes")
  fun getQuotes(
    @RequestParam symbols: String,
  ): ResponseEntity<Any> {
    return try {
      // Validate input
      if (symbols.isBlank()) {
        logger.warn("Empty symbols parameter provided")
        return ResponseEntity.badRequest().body(
          mapOf("error" to "symbols parameter is required and cannot be empty"),
        )
      }

      val symbolsToFetch =
        symbols.split(",")
          .map { it.trim() }
          .filter { it.isNotEmpty() }

      if (symbolsToFetch.isEmpty()) {
        logger.warn("No valid symbols provided after parsing: {}", symbols)
        return ResponseEntity.badRequest().body(
          mapOf("error" to "No valid symbols provided"),
        )
      }

      if (symbolsToFetch.size > 50) {
        logger.warn("Too many symbols requested: {}", symbolsToFetch.size)
        return ResponseEntity.badRequest().body(
          mapOf("error" to "Too many symbols requested (max 50)"),
        )
      }

      val ids = symbolsToFetch.map { SymbolId.parse(it) }
      logger.debug("Fetching quotes for {} symbols: {}", ids.size, ids.map { it.toString() })

      val quotes = service.getQuotes(ids)
      ResponseEntity.ok(quotes)
    } catch (ex: NoSuchElementException) {
      logger.warn("Quote not found: {}", ex.message)
      ResponseEntity.notFound().build()
    } catch (ex: IllegalArgumentException) {
      logger.warn("Invalid symbol format: {}", ex.message)
      ResponseEntity.badRequest().body(
        mapOf("error" to "Invalid symbol format: ${ex.message}"),
      )
    } catch (ex: Exception) {
      logger.error("Unexpected error fetching quotes: {}", ex.message, ex)
      ResponseEntity.internalServerError().body(
        mapOf("error" to "Internal server error"),
      )
    }
  }
}
