package com.notivest.pricefetcher.service

import com.notivest.pricefetcher.client.MarketDataProvider
import com.notivest.pricefetcher.client.ProviderFactory
import com.notivest.pricefetcher.models.MarketClock
import com.notivest.pricefetcher.models.Quote
import com.notivest.pricefetcher.models.RefreshPolicy
import com.notivest.pricefetcher.models.SymbolId
import com.notivest.pricefetcher.repositories.interfaces.QuoteRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.math.BigDecimal

class RefreshSchedulerTest {
  private lateinit var watchListService: WatchListService
  private lateinit var providerFactory: ProviderFactory
  private lateinit var quoteRepository: QuoteRepository
  private lateinit var marketClock: MarketClock
  private lateinit var refreshPolicy: RefreshPolicy
  private lateinit var marketDataProvider: MarketDataProvider
  private lateinit var scheduler: RefreshScheduler

  @BeforeEach
  fun setUp() {
    watchListService = mock()
    providerFactory = mock()
    quoteRepository = mock()
    marketClock = mock()
    refreshPolicy = mock()
    marketDataProvider = mock()

    whenever(providerFactory.primary()).thenReturn(marketDataProvider)

    scheduler =
      RefreshScheduler(
        watchlist = watchListService,
        providerFactory = providerFactory,
        quotes = quoteRepository,
        marketClock = marketClock,
        policy = refreshPolicy,
        refreshMs = 2000L,
      )
  }

  @Test
  fun `should fetch quotes for enabled symbols and store them`() {
    // Setup: Mock enabled symbols
    val symbols =
      listOf(
        SymbolId.parse("AAPL"),
        SymbolId.parse("TSLA"),
        SymbolId.parse("MSFT"),
      )
    whenever(watchListService.enabledSymbols()).thenReturn(symbols)

    // Setup: Mock market phase and batch size
    whenever(marketClock.phase()).thenReturn(MarketClock.Phase.REGULAR)
    whenever(refreshPolicy.batchSize(MarketClock.Phase.REGULAR)).thenReturn(60)

    // Setup: Mock provider response
    val mockQuotes =
      symbols.map { symbol ->
        Quote(
          symbol = symbol,
          // Different price per symbol
          last = BigDecimal("${100 + symbol.ticker.length}.00"),
          open = null,
          high = null,
          low = null,
          prevClose = null,
          source = "TEST",
        )
      }
    whenever(marketDataProvider.fetchQuotes(symbols)).thenReturn(mockQuotes)

    // Execute
    scheduler.tick()

    // Verify: Provider was called with correct symbols
    verify(marketDataProvider).fetchQuotes(symbols)

    // Verify: All quotes were stored
    verify(quoteRepository, times(3)).put(any<Quote>())
    mockQuotes.forEach { quote ->
      verify(quoteRepository).put(quote)
    }
  }

  @Test
  fun `should not fetch when no enabled symbols`() {
    whenever(watchListService.enabledSymbols()).thenReturn(emptyList())

    scheduler.tick()

    verify(marketDataProvider, never()).fetchQuotes(any())
    verify(quoteRepository, never()).put(any<Quote>())
  }

  @Test
  fun `should respect batch size and chunk symbols correctly`() {
    // Setup: 5 symbols but batch size of 2
    val symbols =
      listOf(
        SymbolId.parse("AAPL"),
        SymbolId.parse("TSLA"),
        SymbolId.parse("MSFT"),
        SymbolId.parse("GOOGL"),
        SymbolId.parse("AMZN"),
      )
    whenever(watchListService.enabledSymbols()).thenReturn(symbols)

    // Setup: Small batch size during NIGHT phase
    whenever(marketClock.phase()).thenReturn(MarketClock.Phase.NIGHT)
    whenever(refreshPolicy.batchSize(MarketClock.Phase.NIGHT)).thenReturn(2)

    // Setup: Mock provider to return quotes for each chunk
    val mockQuote1 =
      Quote(
        symbol = SymbolId.parse("AAPL"),
        last = BigDecimal("150.00"),
        open = null,
        high = null,
        low = null,
        prevClose = null,
        source = "TEST",
      )
    val mockQuote2 =
      Quote(
        symbol = SymbolId.parse("TSLA"),
        last = BigDecimal("200.00"),
        open = null,
        high = null,
        low = null,
        prevClose = null,
        source = "TEST",
      )
    val mockQuote3 =
      Quote(
        symbol = SymbolId.parse("MSFT"),
        last = BigDecimal("300.00"),
        open = null,
        high = null,
        low = null,
        prevClose = null,
        source = "TEST",
      )
    val mockQuote4 =
      Quote(
        symbol = SymbolId.parse("GOOGL"),
        last = BigDecimal("2500.00"),
        open = null,
        high = null,
        low = null,
        prevClose = null,
        source = "TEST",
      )
    val mockQuote5 =
      Quote(
        symbol = SymbolId.parse("AMZN"),
        last = BigDecimal("3000.00"),
        open = null,
        high = null,
        low = null,
        prevClose = null,
        source = "TEST",
      )

    whenever(marketDataProvider.fetchQuotes(listOf(symbols[0], symbols[1])))
      .thenReturn(listOf(mockQuote1, mockQuote2))
    whenever(marketDataProvider.fetchQuotes(listOf(symbols[2], symbols[3])))
      .thenReturn(listOf(mockQuote3, mockQuote4))
    whenever(marketDataProvider.fetchQuotes(listOf(symbols[4])))
      .thenReturn(listOf(mockQuote5))

    // Execute
    scheduler.tick()

    // Verify: Provider was called 3 times (chunks of 2, 2, 1)
    verify(marketDataProvider, times(3)).fetchQuotes(any())
    verify(marketDataProvider).fetchQuotes(listOf(symbols[0], symbols[1]))
    verify(marketDataProvider).fetchQuotes(listOf(symbols[2], symbols[3]))
    verify(marketDataProvider).fetchQuotes(listOf(symbols[4]))

    // Verify: All quotes were stored
    verify(quoteRepository, times(5)).put(any<Quote>())
  }

  @Test
  fun `should use different batch sizes for different market phases`() {
    val symbols = listOf(SymbolId.parse("AAPL"))
    whenever(watchListService.enabledSymbols()).thenReturn(symbols)

    val mockQuote =
      Quote(
        symbol = symbols[0],
        last = BigDecimal("150.00"),
        open = null,
        high = null,
        low = null,
        prevClose = null,
        source = "TEST",
      )
    whenever(marketDataProvider.fetchQuotes(any())).thenReturn(listOf(mockQuote))

    // Test REGULAR phase
    whenever(marketClock.phase()).thenReturn(MarketClock.Phase.REGULAR)
    whenever(refreshPolicy.batchSize(MarketClock.Phase.REGULAR)).thenReturn(60)
    scheduler.tick()
    verify(refreshPolicy).batchSize(MarketClock.Phase.REGULAR)

    // Test PREMARKET phase
    whenever(marketClock.phase()).thenReturn(MarketClock.Phase.PRE)
    whenever(refreshPolicy.batchSize(MarketClock.Phase.PRE)).thenReturn(40)
    scheduler.tick()
    verify(refreshPolicy).batchSize(MarketClock.Phase.PRE)

    // Test AFTER phase
    whenever(marketClock.phase()).thenReturn(MarketClock.Phase.AFTER)
    whenever(refreshPolicy.batchSize(MarketClock.Phase.AFTER)).thenReturn(40)
    scheduler.tick()
    verify(refreshPolicy).batchSize(MarketClock.Phase.AFTER)

    // Test NIGHT phase
    whenever(marketClock.phase()).thenReturn(MarketClock.Phase.NIGHT)
    whenever(refreshPolicy.batchSize(MarketClock.Phase.NIGHT)).thenReturn(10)
    scheduler.tick()
    verify(refreshPolicy).batchSize(MarketClock.Phase.NIGHT)
  }

  @Test
  fun `should handle provider errors gracefully`() {
    val symbols = listOf(SymbolId.parse("AAPL"))
    whenever(watchListService.enabledSymbols()).thenReturn(symbols)
    whenever(marketClock.phase()).thenReturn(MarketClock.Phase.REGULAR)
    whenever(refreshPolicy.batchSize(any())).thenReturn(60)

    // Mock provider to throw exception
    whenever(marketDataProvider.fetchQuotes(any())).thenThrow(RuntimeException("Network error"))

    // Should not crash
    scheduler.tick()

    verify(marketDataProvider).fetchQuotes(symbols)
    // Repository should not be called if provider fails
    verify(quoteRepository, never()).put(any<Quote>())
  }

  @Test
  fun `should handle empty provider response`() {
    val symbols = listOf(SymbolId.parse("AAPL"))
    whenever(watchListService.enabledSymbols()).thenReturn(symbols)
    whenever(marketClock.phase()).thenReturn(MarketClock.Phase.REGULAR)
    whenever(refreshPolicy.batchSize(any())).thenReturn(60)

    // Mock provider to return empty list
    whenever(marketDataProvider.fetchQuotes(any())).thenReturn(emptyList())

    scheduler.tick()

    verify(marketDataProvider).fetchQuotes(symbols)
    verify(quoteRepository, never()).put(any<Quote>())
  }

  @Test
  fun `should fetch multiple batches when symbols exceed batch size`() {
    // Create 150 symbols
    val symbols = (1..150).map { SymbolId.parse("SYM$it") }
    whenever(watchListService.enabledSymbols()).thenReturn(symbols)

    whenever(marketClock.phase()).thenReturn(MarketClock.Phase.REGULAR)
    whenever(refreshPolicy.batchSize(MarketClock.Phase.REGULAR)).thenReturn(60)

    // Mock provider to return quotes for any chunk
    whenever(marketDataProvider.fetchQuotes(any())).thenAnswer { invocation ->
      val requestedSymbols = invocation.getArgument<List<SymbolId>>(0)
      requestedSymbols.map { symbol ->
        Quote(
          symbol = symbol,
          last = BigDecimal("100.00"),
          open = null,
          high = null,
          low = null,
          prevClose = null,
          source = "TEST",
        )
      }
    }

    scheduler.tick()

    // Should make 3 calls: 60 + 60 + 30
    verify(marketDataProvider, times(3)).fetchQuotes(any())

    // Should store all 150 quotes
    verify(quoteRepository, times(150)).put(any<Quote>())
  }

  @Test
  fun `should use primary provider from factory`() {
    val symbols = listOf(SymbolId.parse("AAPL"))
    whenever(watchListService.enabledSymbols()).thenReturn(symbols)
    whenever(marketClock.phase()).thenReturn(MarketClock.Phase.REGULAR)
    whenever(refreshPolicy.batchSize(any())).thenReturn(60)

    val mockQuote =
      Quote(
        symbol = symbols[0],
        last = BigDecimal("150.00"),
        open = null,
        high = null,
        low = null,
        prevClose = null,
        source = "TEST",
      )
    whenever(marketDataProvider.fetchQuotes(any())).thenReturn(listOf(mockQuote))

    scheduler.tick()

    verify(providerFactory).primary()
    verify(marketDataProvider).fetchQuotes(symbols)
  }
}
