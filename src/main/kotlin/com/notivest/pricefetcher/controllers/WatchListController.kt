package com.notivest.pricefetcher.controllers

import com.notivest.pricefetcher.dto.PatchBody
import com.notivest.pricefetcher.models.WatchListItem
import com.notivest.pricefetcher.service.WatchListService
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
  @GetMapping("/watchlist")
  fun list(): ResponseEntity<List<WatchListItem>> = ResponseEntity.ok(service.list())

  @PostMapping("/watchlist")
  fun add(
    @RequestBody body: WatchListItem,
  ): ResponseEntity<Any> =
    try {
      service.add(body)
      ResponseEntity.ok().build()
    } catch (e: IllegalArgumentException) {
      ResponseEntity.badRequest().body(mapOf("error" to e.message))
    }

  @PatchMapping("/watchlist/{symbol}")
  fun patch(
    @PathVariable symbol: String,
    @RequestBody body: PatchBody,
  ): ResponseEntity<Any> =
    try {
      service.patch(symbol, body.enabled, body.priority)
      ResponseEntity.noContent().build()
    } catch (e: IllegalArgumentException) {
      ResponseEntity.badRequest().body(mapOf("error" to e.message))
    }

  @DeleteMapping("/watchlist/{symbol}")
  fun delete(
    @PathVariable symbol: String,
  ): ResponseEntity<Any> =
    try {
      service.delete(symbol)
      ResponseEntity.noContent().build()
    } catch (e: IllegalArgumentException) {
      ResponseEntity.badRequest().body(mapOf("error" to e.message))
    }
}
