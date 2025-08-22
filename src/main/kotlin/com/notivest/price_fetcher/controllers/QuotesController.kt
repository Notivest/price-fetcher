package com.notivest.price_fetcher.controllers

import com.notivest.price_fetcher.dto.QuoteDto
import com.notivest.price_fetcher.models.SymbolId
import com.notivest.price_fetcher.service.MarketDataService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
class QuotesController(
    private val service : MarketDataService
) {

    @GetMapping("/quotes")
    fun getQuotes(@RequestParam symbols : String) : ResponseEntity<List<QuoteDto>> {
        return try {
            val ids = symbols.split(",").map { SymbolId.parse(it.trim()) }
            val quotes = service.getQuotes(ids)
            ResponseEntity.ok(quotes)   // 200 OK con body
        } catch (ex: NoSuchElementException) {
            ResponseEntity.notFound().build() // 404 sin body
        }
    }
}