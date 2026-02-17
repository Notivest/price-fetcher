package com.notivest.pricefetcher.service

import com.notivest.pricefetcher.models.Candle
import com.notivest.pricefetcher.models.CandleSeries
import com.notivest.pricefetcher.models.Quote
import com.notivest.pricefetcher.models.SymbolId
import com.notivest.pricefetcher.models.Timeframe
import com.notivest.pricefetcher.repositories.impl.InMemoryCandleRepository
import com.notivest.pricefetcher.repositories.interfaces.CandleRepository
import com.notivest.pricefetcher.repositories.interfaces.QuoteRepository
import com.notivest.pricefetcher.service.strategy.HistoricalFetchingStrategy
import com.notivest.pricefetcher.service.strategy.QuoteFetchingStrategy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarketDataServiceTest {
  private lateinit var quoteStrategy: QuoteFetchingStrategy
  private lateinit var historicalStrategy: HistoricalFetchingStrategy
  private lateinit var quoteRepository: QuoteRepository
  private lateinit var candleRepository: CandleRepository
  private lateinit var service: MarketDataService

  @BeforeEach
  fun setUp() {
    quoteStrategy = mock()
    historicalStrategy = mock()
    quoteRepository = mock()
    candleRepository = mock()
    service =
      MarketDataService(
        quoteStrategy,
        historicalStrategy,
        quoteRepository,
        candleRepository,
      )
  }

  @Test
  fun `getQuotes should prefetch missing symbols and return data`() {
    val symbol = SymbolId.parse("AAPL")
    val quote = Quote(symbol, BigDecimal.TEN, null, null, null, null, ts = Instant.EPOCH)
    whenever(quoteRepository.get(symbol)).thenReturn(null to true, quote to false)
    whenever(quoteStrategy.fetch(listOf(symbol))).thenReturn(listOf(quote))

    val result = service.getQuotes(listOf(symbol))

    verify(quoteStrategy).fetch(listOf(symbol))
    verify(quoteRepository).put(quote)
    assertEquals("AAPL", result.single().symbol)
  }

  @Test
  fun `historical should fetch candles without forcing quote prefetch`() {
    val symbol = SymbolId.parse("MSFT")
    val from = Instant.parse("2024-01-01T00:00:00Z")
    val to = Instant.parse("2024-01-02T00:00:00Z")
    val timeframe = Timeframe.T1H
    val candleSeries =
      CandleSeries(
        symbol,
        timeframe,
        listOf(
          Candle(
            ts = from,
            o = BigDecimal.ONE,
            h = BigDecimal.ONE,
            l = BigDecimal.ONE,
            c = BigDecimal.ONE,
            v = 1L,
            adjusted = true,
          ),
        ),
      )
    whenever(candleRepository.get(symbol, timeframe)).thenReturn(null)
    whenever(historicalStrategy.fetch(symbol, from, to, timeframe)).thenReturn(candleSeries)

    val result = service.historical(symbol, from, to, timeframe, adjusted = true)

    verify(quoteStrategy, never()).fetch(any())
    verify(candleRepository).put(candleSeries)
    assertEquals(candleSeries.copy(items = candleSeries.items.map { it.copy(adjusted = true) }), result)
  }

  @Test
  fun `historical daily should use cached range without fetching`() {
    val inMemoryRepository = useInMemoryCandleRepository()
    val symbol = SymbolId.parse("AAPL")
    val from = Instant.parse("2024-01-02T00:00:00Z")
    val to = Instant.parse("2024-01-03T00:00:00Z")

    inMemoryRepository.put(
      CandleSeries(
        symbol = symbol,
        timeframe = Timeframe.T1D,
        items =
          listOf(
            candle("2024-01-01T00:00:00Z", 100.0),
            candle("2024-01-02T00:00:00Z", 110.0),
            candle("2024-01-03T00:00:00Z", 120.0),
          ),
      ),
    )
    whenever(quoteRepository.get(symbol)).thenReturn(cachedQuote(symbol) to false)

    val result = service.historical(symbol, from, to, Timeframe.T1D, adjusted = false)

    verify(historicalStrategy, never()).fetch(any(), any(), any(), any())
    assertEquals(
      listOf(
        Instant.parse("2024-01-02T00:00:00Z"),
        Instant.parse("2024-01-03T00:00:00Z"),
      ),
      result.items.map { it.ts },
    )
    assertEquals(listOf(false, false), result.items.map { it.adjusted })
  }

  @Test
  fun `historical daily should backfill missing left side only`() {
    val inMemoryRepository = useInMemoryCandleRepository()
    val symbol = SymbolId.parse("TSLA")
    val from = Instant.parse("2024-01-01T00:00:00Z")
    val to = Instant.parse("2024-01-03T00:00:00Z")
    val cachedOldest = Instant.parse("2024-01-02T00:00:00Z")

    inMemoryRepository.put(
      CandleSeries(
        symbol = symbol,
        timeframe = Timeframe.T1D,
        items =
          listOf(
            candle("2024-01-02T00:00:00Z", 20.0),
            candle("2024-01-03T00:00:00Z", 30.0),
          ),
      ),
    )

    whenever(quoteRepository.get(symbol)).thenReturn(cachedQuote(symbol) to false)
    whenever(historicalStrategy.fetch(eq(symbol), eq(from), eq(cachedOldest), eq(Timeframe.T1D))).thenReturn(
      CandleSeries(
        symbol = symbol,
        timeframe = Timeframe.T1D,
        items =
          listOf(
            candle("2024-01-01T00:00:00Z", 10.0),
            candle("2024-01-02T00:00:00Z", 25.0),
          ),
      ),
    )

    val result = service.historical(symbol, from, to, Timeframe.T1D, adjusted = true)

    verify(historicalStrategy, times(1)).fetch(eq(symbol), eq(from), eq(cachedOldest), eq(Timeframe.T1D))
    assertEquals(3, inMemoryRepository.size(symbol, Timeframe.T1D))
    assertEquals(listOf("10.0", "25.0", "30.0"), result.items.map { it.c.toPlainString() })
  }

  @Test
  fun `historical daily should forward fill missing right side only`() {
    val inMemoryRepository = useInMemoryCandleRepository()
    val symbol = SymbolId.parse("NVDA")
    val from = Instant.parse("2024-01-01T00:00:00Z")
    val to = Instant.parse("2024-01-03T00:00:00Z")
    val cachedNewest = Instant.parse("2024-01-02T00:00:00Z")

    inMemoryRepository.put(
      CandleSeries(
        symbol = symbol,
        timeframe = Timeframe.T1D,
        items =
          listOf(
            candle("2024-01-01T00:00:00Z", 10.0),
            candle("2024-01-02T00:00:00Z", 20.0),
          ),
      ),
    )

    whenever(quoteRepository.get(symbol)).thenReturn(cachedQuote(symbol) to false)
    whenever(historicalStrategy.fetch(eq(symbol), eq(cachedNewest), eq(to), eq(Timeframe.T1D))).thenReturn(
      CandleSeries(
        symbol = symbol,
        timeframe = Timeframe.T1D,
        items =
          listOf(
            candle("2024-01-02T00:00:00Z", 21.0),
            candle("2024-01-03T00:00:00Z", 30.0),
          ),
      ),
    )

    val result = service.historical(symbol, from, to, Timeframe.T1D, adjusted = true)

    verify(historicalStrategy, times(1)).fetch(eq(symbol), eq(cachedNewest), eq(to), eq(Timeframe.T1D))
    assertEquals(3, inMemoryRepository.size(symbol, Timeframe.T1D))
    assertEquals(listOf("10.0", "21.0", "30.0"), result.items.map { it.c.toPlainString() })
  }

  @Test
  fun `historical daily should not refetch same left range when backfill returns empty`() {
    val inMemoryRepository = useInMemoryCandleRepository()
    val symbol = SymbolId.parse("AAPL")
    val from = Instant.parse("2026-01-18T20:40:13Z")
    val to = Instant.parse("2026-01-20T05:00:00Z")
    val cachedOldest = Instant.parse("2026-01-20T05:00:00Z")

    inMemoryRepository.put(
      CandleSeries(
        symbol = symbol,
        timeframe = Timeframe.T1D,
        items = listOf(candle("2026-01-20T05:00:00Z", 30.0)),
      ),
    )

    whenever(quoteRepository.get(symbol)).thenReturn(cachedQuote(symbol) to false)
    whenever(historicalStrategy.fetch(eq(symbol), eq(from), eq(cachedOldest), eq(Timeframe.T1D))).thenReturn(
      CandleSeries(
        symbol = symbol,
        timeframe = Timeframe.T1D,
        items = emptyList(),
      ),
    )

    service.historical(symbol, from, to, Timeframe.T1D, adjusted = true)
    service.historical(symbol, from, to, Timeframe.T1D, adjusted = true)

    verify(historicalStrategy, times(1)).fetch(eq(symbol), eq(from), eq(cachedOldest), eq(Timeframe.T1D))
  }

  @Test
  fun `historical should coalesce concurrent requests for same symbol and range`() {
    val inMemoryRepository = useInMemoryCandleRepository()
    val symbol = SymbolId.parse("META")
    val from = Instant.parse("2026-01-01T00:00:00Z")
    val to = Instant.parse("2026-01-31T23:59:59Z")
    val fetchCalls = AtomicInteger(0)
    val firstFetchEntered = CountDownLatch(1)
    val releaseFirstFetch = CountDownLatch(1)
    val done = CountDownLatch(2)

    whenever(quoteRepository.get(symbol)).thenReturn(cachedQuote(symbol) to false)
    whenever(historicalStrategy.fetch(eq(symbol), eq(from), eq(to), eq(Timeframe.T1D))).thenAnswer {
      val current = fetchCalls.incrementAndGet()
      if (current == 1) {
        firstFetchEntered.countDown()
        releaseFirstFetch.await(2, TimeUnit.SECONDS)
      }
      CandleSeries(
        symbol = symbol,
        timeframe = Timeframe.T1D,
        items = listOf(candle("2026-01-31T05:00:00Z", 600.0)),
      )
    }

    val thread1 =
      Thread {
        service.historical(symbol, from, to, Timeframe.T1D, adjusted = true)
        done.countDown()
      }
    val thread2 =
      Thread {
        service.historical(symbol, from, to, Timeframe.T1D, adjusted = true)
        done.countDown()
      }

    thread1.start()
    assertTrue(firstFetchEntered.await(2, TimeUnit.SECONDS))
    thread2.start()
    Thread.sleep(150)
    releaseFirstFetch.countDown()

    assertTrue(done.await(3, TimeUnit.SECONDS))
    assertEquals(1, fetchCalls.get())
    assertEquals(1, inMemoryRepository.size(symbol, Timeframe.T1D))
  }

  private fun useInMemoryCandleRepository(): InMemoryCandleRepository {
    val inMemoryRepository = InMemoryCandleRepository()
    candleRepository = inMemoryRepository
    service =
      MarketDataService(
        quoteStrategy,
        historicalStrategy,
        quoteRepository,
        candleRepository,
      )
    return inMemoryRepository
  }

  private fun cachedQuote(symbol: SymbolId) =
    Quote(
      symbol = symbol,
      last = BigDecimal.ONE,
      open = null,
      high = null,
      low = null,
      prevClose = null,
      ts = Instant.EPOCH,
    )

  private fun candle(
    timestamp: String,
    close: Double,
  ): Candle =
    Candle(
      ts = Instant.parse(timestamp),
      o = BigDecimal.valueOf(close),
      h = BigDecimal.valueOf(close),
      l = BigDecimal.valueOf(close),
      c = BigDecimal.valueOf(close),
      v = 100L,
      adjusted = true,
    )
}
