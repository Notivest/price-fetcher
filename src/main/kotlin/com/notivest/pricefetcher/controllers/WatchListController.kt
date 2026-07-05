package com.notivest.pricefetcher.controllers

import com.notivest.pricefetcher.dto.PatchBody
import com.notivest.pricefetcher.models.WatchListItem
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
  fun list(): ResponseEntity<List<WatchListItem>> {
    logger.debug("Getting watchlist")
    return ResponseEntity.ok(service.list())
  }

  @PostMapping("/watchlist")
  fun add(
    @RequestBody body: WatchListItem,
  ): ResponseEntity<Any> {
    logger.debug("Adding to watchlist: {}", body.symbol)
    return try {
      service.add(body)
      ResponseEntity.ok().build()
    } catch (e: IllegalArgumentException) {
      ResponseEntity.badRequest().body(ApiErrorResponses.body(e.message ?: "Invalid watchlist payload"))
    }
  }

  @PatchMapping("/watchlist/{symbol}")
  fun patch(
    @PathVariable symbol: String,
    @RequestBody body: PatchBody,
  ): ResponseEntity<Any> {
    logger.debug("Patching watchlist for symbol: {}", symbol)
    return try {
      service.patch(symbol, body.enabled, body.priority)
      ResponseEntity.noContent().build()
    } catch (e: IllegalArgumentException) {
      ResponseEntity.badRequest().body(ApiErrorResponses.body(e.message ?: "Invalid watchlist patch"))
    }
  }

  @DeleteMapping("/watchlist/{symbol}")
  fun delete(
    @PathVariable symbol: String,
  ): ResponseEntity<Any> {
    logger.debug("Deleting from watchlist: {}", symbol)
    return try {
      service.delete(symbol)
      ResponseEntity.noContent().build()
    } catch (e: IllegalArgumentException) {
      ResponseEntity.badRequest().body(ApiErrorResponses.body(e.message ?: "Invalid watchlist symbol"))
    }
  }
}
