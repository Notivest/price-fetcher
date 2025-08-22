package com.notivest.price_fetcher.controllers

import com.notivest.price_fetcher.client.WatchList
import com.notivest.price_fetcher.models.SymbolId
import com.notivest.price_fetcher.service.MarketDataService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController


@RestController
class PrefetchController(
    private val watchList: WatchList,
    private val service: MarketDataService
) {

    @PostMapping("/prefetch")
    fun prefetch() : ResponseEntity<Map<String, Any>> {
        val ids = watchList.getSymbols().filter { it.enabled }.map { SymbolId.parse(it.symbol)}
        val count = service.prefetch(ids)
        return ResponseEntity.ok(
            mapOf(
                "prefetched" to count,
                "symbols" to ids.map { it.toString() }
            )
        )
    }
}