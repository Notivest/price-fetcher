package com.notivest.pricefetcher.controllers

import com.notivest.pricefetcher.dto.QuoteDto
import com.notivest.pricefetcher.models.SymbolId
import com.notivest.pricefetcher.service.MarketDataService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class QuotesController(
  private val service: MarketDataService,
) {
  @GetMapping("/quotes")
  fun getQuotes(
    @RequestParam symbols: String,
  ): ResponseEntity<List<QuoteDto>> {
    return try {
      val ids = symbols.split(",").map { SymbolId.parse(it.trim()) }
      val quotes = service.getQuotes(ids)
      ResponseEntity.ok(quotes) // 200 OK con body
    } catch (ex: NoSuchElementException) {
      ResponseEntity.notFound().build() // 404 sin body
    }
  }
}
