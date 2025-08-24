package com.notivest.pricefetcher.repositories

import com.notivest.pricefetcher.models.Quote
import com.notivest.pricefetcher.models.SymbolId
import com.notivest.pricefetcher.repositories.impl.InMemoryQuoteRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryQuoteRepositoryTest {
  private lateinit var repository: InMemoryQuoteRepository
  private val fixedClock = Clock.fixed(Instant.parse("2024-01-01T12:00:00Z"), ZoneOffset.UTC)

  @BeforeEach
  fun setUp() {
    repository = InMemoryQuoteRepository(ttlSeconds = 30) // 30 seconds TTL for testing
  }

  @Test
  fun `should store and retrieve quote`() {
    val symbol = SymbolId.parse("AAPL")
    val quote =
      Quote(
        symbol = symbol,
        last = BigDecimal("150.00"),
        open = BigDecimal("149.00"),
        high = BigDecimal("151.00"),
        low = BigDecimal("148.00"),
        prevClose = BigDecimal("149.50"),
      )

    repository.put(quote)
    val (retrieved, stale) = repository.get(symbol)

    assertEquals(quote, retrieved)
    assertFalse(stale) // Should be fresh immediately after putting
  }

  @Test
  fun `should return null for non-existent symbol`() {
    val symbol = SymbolId.parse("NONEXISTENT")
    val (quote, stale) = repository.get(symbol)

    assertNull(quote)
    assertTrue(stale) // Non-existent quotes are considered stale
  }

  @Test
  fun `should update existing quote`() {
    val symbol = SymbolId.parse("AAPL")
    val quote1 =
      Quote(symbol = symbol, last = BigDecimal("150.00"), open = null, high = null, low = null, prevClose = null)
    val quote2 =
      Quote(symbol = symbol, last = BigDecimal("155.00"), open = null, high = null, low = null, prevClose = null)

    repository.put(quote1)
    repository.put(quote2)

    val (retrieved, _) = repository.get(symbol)
    assertEquals(BigDecimal("155.00"), retrieved?.last)
  }

  @Test
  fun `should mark quotes as stale after TTL expires`() {
    val symbol = SymbolId.parse("AAPL")
    val quote =
      Quote(symbol = symbol, last = BigDecimal("150.00"), open = null, high = null, low = null, prevClose = null)

    // Create repository with very short TTL (1 second)
    val shortTtlRepo = InMemoryQuoteRepository(ttlSeconds = 1)
    shortTtlRepo.put(quote)

    // Wait for TTL to expire
    Thread.sleep(1100)

    val (retrieved, stale) = shortTtlRepo.get(symbol)
    assertEquals(quote, retrieved)
    assertTrue(stale) // Should be stale after TTL expires
  }

  @Test
  fun `should return correct count of stored quotes`() {
    val symbol1 = SymbolId.parse("AAPL")
    val symbol2 = SymbolId.parse("TSLA")
    val quote1 =
      Quote(symbol = symbol1, last = BigDecimal("150.00"), open = null, high = null, low = null, prevClose = null)
    val quote2 =
      Quote(symbol = symbol2, last = BigDecimal("200.00"), open = null, high = null, low = null, prevClose = null)

    assertEquals(0, repository.count())

    repository.put(quote1)
    assertEquals(1, repository.count())

    repository.put(quote2)
    assertEquals(2, repository.count())

    // Updating existing quote shouldn't increase count
    repository.put(quote1.copy(last = BigDecimal("155.00")))
    assertEquals(2, repository.count())
  }

  @Test
  fun `should return correct symbols set`() {
    val symbol1 = SymbolId.parse("AAPL")
    val symbol2 = SymbolId.parse("TSLA.MX")
    val quote1 =
      Quote(symbol = symbol1, last = BigDecimal("150.00"), open = null, high = null, low = null, prevClose = null)
    val quote2 =
      Quote(symbol = symbol2, last = BigDecimal("200.00"), open = null, high = null, low = null, prevClose = null)

    repository.put(quote1)
    repository.put(quote2)

    val symbols = repository.symbols()
    assertEquals(2, symbols.size)
    assertTrue(symbols.contains(symbol1))
    assertTrue(symbols.contains(symbol2))
  }

  @Test
  fun `should handle quotes with exchange in symbol`() {
    val symbol = SymbolId.parse("AAPL.MX")
    val quote =
      Quote(symbol = symbol, last = BigDecimal("150.00"), open = null, high = null, low = null, prevClose = null)

    repository.put(quote)
    val (retrieved, stale) = repository.get(symbol)

    assertEquals(quote, retrieved)
    assertEquals("MX", retrieved?.symbol?.exchange)
    assertEquals("AAPL", retrieved?.symbol?.ticker)
    assertFalse(stale)
  }

  @Test
  fun `should handle concurrent access safely`() {
    val symbol = SymbolId.parse("AAPL")
    val threads =
      (1..10).map { i ->
        Thread {
          val quote =
            Quote(
              symbol = symbol,
              last = BigDecimal("${150 + i}.00"),
              open = null,
              high = null,
              low = null,
              prevClose = null,
            )
          repository.put(quote)
        }
      }

    threads.forEach { it.start() }
    threads.forEach { it.join() }

    assertEquals(1, repository.count()) // Should have only one symbol
    val (retrieved, _) = repository.get(symbol)
    assertTrue(retrieved?.last!! >= BigDecimal("150.00"))
    assertTrue(retrieved.last <= BigDecimal("160.00"))
  }
}
