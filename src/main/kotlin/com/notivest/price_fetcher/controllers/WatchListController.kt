package com.notivest.price_fetcher.controllers

import com.notivest.price_fetcher.client.WatchList
import com.notivest.price_fetcher.models.WatchListItem
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class WatchListController (
    private val client: WatchList
) {
    @GetMapping("/watchlist")
    fun watchlist(): ResponseEntity<List<WatchListItem>> =
        ResponseEntity.ok(client.getSymbols())
}