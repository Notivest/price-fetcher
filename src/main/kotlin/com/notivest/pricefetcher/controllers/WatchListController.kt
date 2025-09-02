package com.notivest.pricefetcher.controllers

import com.notivest.pricefetcher.dto.PatchBody
import com.notivest.pricefetcher.models.WatchListItem
import com.notivest.pricefetcher.security.AllowBothCallTypes
import com.notivest.pricefetcher.security.logCaller
import com.notivest.pricefetcher.service.WatchListService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class WatchListController(
  private val service: WatchListService,
) {
  private val logger = LoggerFactory.getLogger(WatchListController::class.java)

  @GetMapping("/watchlist")
  @AllowBothCallTypes(
    userScopes = ["read:prices"],
    serviceScopes = ["read:prices", "service:internal"]
  )
  fun list(): ResponseEntity<List<WatchListItem>> {
    logger.logCaller("Watchlist")
    return ResponseEntity.ok(service.list())
  }

  @PostMapping("/watchlist")
  @AllowBothCallTypes(
    userScopes = ["write:prices"],
    serviceScopes = ["write:prices", "service:internal"]
  )
  fun add(
    @RequestBody body: WatchListItem,
  ): ResponseEntity<Any> {
    logger.logCaller("Watchlist add", "for symbol", body.symbol)
    return try {
      service.add(body)
      ResponseEntity.ok().build()
    } catch (e: IllegalArgumentException) {
      ResponseEntity.badRequest().body(mapOf("error" to e.message))
    }
  }

  @PatchMapping("/watchlist/{symbol}")
  @AllowBothCallTypes(
    userScopes = ["write:prices"],
    serviceScopes = ["write:prices", "service:internal"]
  )
  fun patch(
    @PathVariable symbol: String,
    @RequestBody body: PatchBody,
  ): ResponseEntity<Any> {
    logger.logCaller("Watchlist patch", "for symbol", symbol)
    return try {
      service.patch(symbol, body.enabled, body.priority)
      ResponseEntity.noContent().build()
    } catch (e: IllegalArgumentException) {
      ResponseEntity.badRequest().body(mapOf("error" to e.message))
    }
  }

  @DeleteMapping("/watchlist/{symbol}")
  @AllowBothCallTypes(
    userScopes = ["write:prices"],
    serviceScopes = ["write:prices", "service:internal"]
  )
  fun delete(
    @PathVariable symbol: String,
  ): ResponseEntity<Any> {
    logger.logCaller("Watchlist delete", "for symbol", symbol)
    return try {
      service.delete(symbol)
      ResponseEntity.noContent().build()
    } catch (e: IllegalArgumentException) {
      ResponseEntity.badRequest().body(mapOf("error" to e.message))
    }
  }
}
