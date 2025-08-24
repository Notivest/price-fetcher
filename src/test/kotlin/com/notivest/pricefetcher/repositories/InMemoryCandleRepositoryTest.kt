package com.notivest.pricefetcher.repositories

import com.notivest.pricefetcher.models.Candle
import com.notivest.pricefetcher.models.CandleSeries
import com.notivest.pricefetcher.models.SymbolId
import com.notivest.pricefetcher.models.Timeframe
import com.notivest.pricefetcher.repositories.impl.InMemoryCandleRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InMemoryCandleRepositoryTest {
  private lateinit var repository: InMemoryCandleRepository

  @BeforeEach
  fun setUp() {
    repository = InMemoryCandleRepository()
  }

  private fun createCandle(
    timestamp: String,
    open: Double,
    high: Double,
    low: Double,
    close: Double,
    volume: Long = 1000,
  ): Candle {
    return Candle(
      ts = Instant.parse(timestamp),
      o = BigDecimal.valueOf(open),
      h = BigDecimal.valueOf(high),
      l = BigDecimal.valueOf(low),
      c = BigDecimal.valueOf(close),
      v = volume,
    )
  }

  @Test
  fun `should store and retrieve candle series`() {
    val symbol = SymbolId.parse("AAPL")
    val timeframe = Timeframe.T1D
    val candles =
      listOf(
        createCandle("2024-01-01T09:30:00Z", 150.0, 155.0, 149.0, 154.0),
        createCandle("2024-01-02T09:30:00Z", 154.0, 158.0, 153.0, 157.0),
      )
    val series = CandleSeries(symbol, timeframe, candles)

    repository.put(series)
    val retrieved = repository.get(symbol, timeframe)

    assertEquals(series.symbol, retrieved?.symbol)
    assertEquals(series.timeframe, retrieved?.timeframe)
    assertEquals(2, retrieved?.items?.size)
  }

  @Test
  fun `should return null for non-existent series`() {
    val symbol = SymbolId.parse("NONEXISTENT")
    val timeframe = Timeframe.T1D

    val result = repository.get(symbol, timeframe)

    assertNull(result)
  }

  @Test
  fun `should sort candles by timestamp when storing`() {
    val symbol = SymbolId.parse("AAPL")
    val timeframe = Timeframe.T1D
    val candles =
      listOf(
        // Latest first
        createCandle("2024-01-03T09:30:00Z", 160.0, 165.0, 159.0, 164.0),
        // Earliest last
        createCandle("2024-01-01T09:30:00Z", 150.0, 155.0, 149.0, 154.0),
        // Middle
        createCandle("2024-01-02T09:30:00Z", 154.0, 158.0, 153.0, 157.0),
      )
    val series = CandleSeries(symbol, timeframe, candles)

    repository.put(series)
    val retrieved = repository.get(symbol, timeframe)

    val sortedItems = retrieved?.items ?: emptyList()
    assertEquals(Instant.parse("2024-01-01T09:30:00Z"), sortedItems[0].ts)
    assertEquals(Instant.parse("2024-01-02T09:30:00Z"), sortedItems[1].ts)
    assertEquals(Instant.parse("2024-01-03T09:30:00Z"), sortedItems[2].ts)
  }

  @Test
  fun `should append new items and merge by timestamp`() {
    val symbol = SymbolId.parse("AAPL")
    val timeframe = Timeframe.T1D

    // Store initial series
    val initialCandles =
      listOf(
        createCandle("2024-01-01T09:30:00Z", 150.0, 155.0, 149.0, 154.0),
        createCandle("2024-01-02T09:30:00Z", 154.0, 158.0, 153.0, 157.0),
      )
    repository.put(CandleSeries(symbol, timeframe, initialCandles))

    // Append new items
    val newCandles =
      listOf(
        createCandle("2024-01-03T09:30:00Z", 157.0, 162.0, 156.0, 161.0),
        // Override existing
        createCandle("2024-01-01T09:30:00Z", 151.0, 156.0, 150.0, 155.0),
      )

    val result = repository.append(symbol, timeframe, newCandles, maxWindow = 1000)

    // Should have 3 total (merged)
    assertEquals(3, result.items.size)
    // Updated value for 2024-01-01
    assertEquals(BigDecimal.valueOf(155.0), result.items[0].c)
    // Original 2024-01-02
    assertEquals(BigDecimal.valueOf(157.0), result.items[1].c)
    // New 2024-01-03
    assertEquals(BigDecimal.valueOf(161.0), result.items[2].c)
  }

  @Test
  fun `should respect max window size when appending`() {
    val symbol = SymbolId.parse("AAPL")
    val timeframe = Timeframe.T1D

    // Create 5 candles
    val candles =
      (1..5).map { day ->
        createCandle(
          "2024-01-${day.toString().padStart(2, '0')}T09:30:00Z",
          150.0 + day,
          155.0 + day,
          149.0 + day,
          154.0 + day,
        )
      }

    // Append with max window of 3
    val result = repository.append(symbol, timeframe, candles, maxWindow = 3)

    assertEquals(3, result.items.size)
    // Should keep the last 3 items (days 3, 4, 5)
    assertEquals(Instant.parse("2024-01-03T09:30:00Z"), result.items[0].ts)
    assertEquals(Instant.parse("2024-01-04T09:30:00Z"), result.items[1].ts)
    assertEquals(Instant.parse("2024-01-05T09:30:00Z"), result.items[2].ts)
  }

  @Test
  fun `should filter out candles with EPOCH timestamp`() {
    val symbol = SymbolId.parse("AAPL")
    val timeframe = Timeframe.T1D

    val candles =
      listOf(
        createCandle("2024-01-01T09:30:00Z", 150.0, 155.0, 149.0, 154.0),
        // Should be filtered out
        Candle(
          Instant.EPOCH,
          BigDecimal.valueOf(999.0),
          BigDecimal.valueOf(999.0),
          BigDecimal.valueOf(999.0),
          BigDecimal.valueOf(999.0),
          999,
        ),
      )

    val result = repository.append(symbol, timeframe, candles, maxWindow = 1000)

    assertEquals(1, result.items.size)
    assertEquals(Instant.parse("2024-01-01T09:30:00Z"), result.items[0].ts)
  }

  @Test
  fun `should return correct count of series`() {
    val symbol1 = SymbolId.parse("AAPL")
    val symbol2 = SymbolId.parse("TSLA")
    val candle = createCandle("2024-01-01T09:30:00Z", 150.0, 155.0, 149.0, 154.0)

    assertEquals(0, repository.count())

    repository.put(CandleSeries(symbol1, Timeframe.T1D, listOf(candle)))
    assertEquals(1, repository.count())

    // Different timeframe
    repository.put(CandleSeries(symbol1, Timeframe.T1H, listOf(candle)))
    assertEquals(2, repository.count())

    // Different symbol
    repository.put(CandleSeries(symbol2, Timeframe.T1D, listOf(candle)))
    assertEquals(3, repository.count())
  }

  @Test
  fun `should return correct size for specific series`() {
    val symbol = SymbolId.parse("AAPL")
    val timeframe = Timeframe.T1D
    val candles =
      listOf(
        createCandle("2024-01-01T09:30:00Z", 150.0, 155.0, 149.0, 154.0),
        createCandle("2024-01-02T09:30:00Z", 154.0, 158.0, 153.0, 157.0),
      )

    repository.put(CandleSeries(symbol, timeframe, candles))

    assertEquals(2, repository.size(symbol, timeframe))
    assertEquals(0, repository.size(SymbolId.parse("NONEXISTENT"), timeframe))
  }

  @Test
  fun `should handle different timeframes for same symbol`() {
    val symbol = SymbolId.parse("AAPL")
    val candle = createCandle("2024-01-01T09:30:00Z", 150.0, 155.0, 149.0, 154.0)

    repository.put(CandleSeries(symbol, Timeframe.T1D, listOf(candle)))
    repository.put(CandleSeries(symbol, Timeframe.T1H, listOf(candle, candle)))

    assertEquals(1, repository.size(symbol, Timeframe.T1D))
    assertEquals(2, repository.size(symbol, Timeframe.T1H))
  }

  @Test
  fun `should handle concurrent access safely`() {
    val symbol = SymbolId.parse("AAPL")
    val timeframe = Timeframe.T1D

    val threads =
      (1..10).map { i ->
        Thread {
          val candle =
            createCandle(
              "2024-01-${i.toString().padStart(2, '0')}T09:30:00Z",
              150.0 + i,
              155.0 + i,
              149.0 + i,
              154.0 + i,
            )
          repository.append(symbol, timeframe, listOf(candle), maxWindow = 1000)
        }
      }

    threads.forEach { it.start() }
    threads.forEach { it.join() }

    val result = repository.get(symbol, timeframe)
    assertEquals(10, result?.items?.size)
  }
}
