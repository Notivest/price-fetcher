package com.notivest.pricefetcher.service

import com.notivest.pricefetcher.models.Candle
import com.notivest.pricefetcher.models.CandleSeries
import com.notivest.pricefetcher.models.Quote
import com.notivest.pricefetcher.models.SymbolId
import com.notivest.pricefetcher.models.Timeframe
import com.notivest.pricefetcher.repositories.interfaces.CandleRepository
import com.notivest.pricefetcher.repositories.interfaces.QuoteRepository
import com.notivest.pricefetcher.service.strategy.HistoricalFetchingStrategy
import com.notivest.pricefetcher.service.strategy.QuoteFetchingStrategy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.assertEquals

class MarketDataServiceTest {
  private lateinit var quoteStrategy: QuoteFetchingStrategy
  private lateinit var historicalStrategy: HistoricalFetchingStrategy
  private lateinit var quoteRepository: QuoteRepository
  private lateinit var candleRepository: CandleRepository
  private lateinit var watchListService: WatchListService
  private lateinit var service: MarketDataService

  @BeforeEach
  fun setUp() {
    quoteStrategy = mock()
    historicalStrategy = mock()
    quoteRepository = mock()
    candleRepository = mock()
    watchListService = mock()
    service =
      MarketDataService(
        quoteStrategy,
        historicalStrategy,
        quoteRepository,
        candleRepository,
        watchListService,
      )
  }

  @Test
  fun `getQuotes should prefetch missing symbols and return data`() {
    val symbol = SymbolId.parse("AAPL")
    val quote = Quote(symbol, BigDecimal.TEN, null, null, null, null, ts = Instant.EPOCH)
    whenever(quoteRepository.get(symbol)).thenReturn(null to true, quote to false)
    whenever(quoteStrategy.fetch(listOf(symbol))).thenReturn(listOf(quote))

    val result = service.getQuotes(listOf(symbol))

    verify(watchListService).ensureEnabled(symbol)
    verify(quoteStrategy).fetch(listOf(symbol))
    verify(quoteRepository).put(quote)
    assertEquals("AAPL", result.single().symbol)
  }

  @Test
  fun `historical should ensure symbol is tracked and cached`() {
    val symbol = SymbolId.parse("MSFT")
    val from = Instant.parse("2024-01-01T00:00:00Z")
    val to = Instant.parse("2024-01-02T00:00:00Z")
    val timeframe = Timeframe.T1D
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
    whenever(quoteRepository.get(symbol)).thenReturn(null to true)
    whenever(quoteStrategy.fetch(listOf(symbol))).thenReturn(emptyList())
    whenever(candleRepository.get(symbol, timeframe)).thenReturn(null)
    whenever(historicalStrategy.fetch(symbol, from, to, timeframe)).thenReturn(candleSeries)

    val result = service.historical(symbol, from, to, timeframe, adjusted = true)

    verify(watchListService).ensureEnabled(symbol)
    verify(quoteStrategy).fetch(listOf(symbol))
    verify(candleRepository).put(candleSeries)
    assertEquals(candleSeries.copy(items = candleSeries.items.map { it.copy(adjusted = true) }), result)
  }
}
