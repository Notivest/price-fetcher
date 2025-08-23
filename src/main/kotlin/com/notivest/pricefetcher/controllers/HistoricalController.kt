package com.notivest.pricefetcher.controllers

import com.notivest.pricefetcher.models.CandleSeries
import com.notivest.pricefetcher.models.SymbolId
import com.notivest.pricefetcher.models.Timeframe
import com.notivest.pricefetcher.service.MarketDataService
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
  @GetMapping("/historical")
  fun historical(
    @RequestParam symbol: String,
    @RequestParam from: String,
    @RequestParam to: String,
    @RequestParam tf: String = "T1D",
    @RequestParam(required = false, defaultValue = "true") adjusted: Boolean,
  ): ResponseEntity<CandleSeries> =
    try {
      val sid = SymbolId.parse(symbol.trim())
      val timeframe = Timeframe.valueOf(tf.uppercase())
      val fromTs = Instant.parse(from)
      val toTs = Instant.parse(to)

      ResponseEntity.ok(service.historical(sid, fromTs, toTs, timeframe, adjusted))
    } catch (e: IllegalArgumentException) {
      // Timeframe inválido u otro argumento
      ResponseEntity.badRequest().build()
    } catch (e: DateTimeParseException) {
      ResponseEntity.badRequest().build()
    }
}
