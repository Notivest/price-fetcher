package com.notivest.pricefetcher.service

import com.notivest.pricefetcher.models.MarketClock
import com.notivest.pricefetcher.models.MarketClockPhase
import com.notivest.pricefetcher.models.MarketClockSnapshot
import com.notivest.pricefetcher.models.Quote
import com.notivest.pricefetcher.models.RefreshPolicy
import com.notivest.pricefetcher.models.SymbolId
import com.notivest.pricefetcher.repositories.interfaces.QuoteRepository
import com.notivest.pricefetcher.service.strategy.QuoteFetchingStrategy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant

class RefreshSchedulerTest {
  private lateinit var watchListService: WatchListService
  private lateinit var quoteFetchingStrategy: QuoteFetchingStrategy
  private lateinit var quoteRepository: QuoteRepository
  private lateinit var marketClock: MarketClock
  private lateinit var refreshPolicy: RefreshPolicy
  private lateinit var scheduler: RefreshScheduler

  @BeforeEach
  fun setUp() {
    watchListService = mock()
    quoteFetchingStrategy = mock()
    quoteRepository = mock()
    marketClock = mock()
    refreshPolicy = mock()

    scheduler =
      RefreshScheduler(
        watchlist = watchListService,
        quoteFetchingStrategy = quoteFetchingStrategy,
        quotes = quoteRepository,
        marketClock = marketClock,
        policy = refreshPolicy,
      )

    whenever(quoteRepository.get(any())).thenReturn(null to true)
  }

  @Test
  fun `fetches quotes for open symbols`() {
    val symbols =
      listOf(
        SymbolId.parse("AAPL"),
        SymbolId.parse("TSLA"),
        SymbolId.parse("MSFT"),
      )

    whenever(watchListService.enabledSymbols()).thenReturn(symbols)
    whenever(marketClock.snapshot(symbols)).thenReturn(snapshotFor(symbols, MarketClockPhase.REGULAR))
    whenever(refreshPolicy.batchSize(MarketClockPhase.REGULAR)).thenReturn(60)

    val quotes =
      symbols.mapIndexed { idx, symbol ->
        Quote(
          symbol = symbol,
          last = BigDecimal(100 + idx),
          open = null,
          high = null,
          low = null,
          prevClose = null,
          source = "TEST",
        )
      }
    whenever(quoteFetchingStrategy.fetch(symbols)).thenReturn(quotes)

    scheduler.tick()

    verify(quoteFetchingStrategy).fetch(symbols)
    quotes.forEach { verify(quoteRepository).put(it) }
  }

  @Test
  fun `skips closed markets`() {
    val symbols = listOf(SymbolId.parse("AAPL"), SymbolId.parse("X:BTCUSD"))
    whenever(watchListService.enabledSymbols()).thenReturn(symbols)

    val phases =
      mapOf(
        symbols[0] to MarketClockPhase.NIGHT,
        symbols[1] to MarketClockPhase.REGULAR,
      )
    whenever(marketClock.snapshot(symbols)).thenReturn(snapshotFor(phases, MarketClockPhase.REGULAR))
    whenever(refreshPolicy.batchSize(MarketClockPhase.REGULAR)).thenReturn(60)
    whenever(quoteFetchingStrategy.fetch(listOf(symbols[1]))).thenReturn(
      listOf(
        Quote(
          symbol = symbols[1],
          last = BigDecimal("50000.00"),
          open = null,
          high = null,
          low = null,
          prevClose = null,
          source = "TEST",
        ),
      ),
    )

    scheduler.tick()

    verify(quoteFetchingStrategy, never()).fetch(argThat { contains(symbols[0]) })
    verify(quoteFetchingStrategy).fetch(listOf(symbols[1]))
    verify(quoteRepository).put(check { require(it.symbol == symbols[1]) })
  }

  @Test
  fun `skips refresh when all quotes are fresh`() {
    val symbols = listOf(SymbolId.parse("AAPL"), SymbolId.parse("MSFT"))
    whenever(watchListService.enabledSymbols()).thenReturn(symbols)
    whenever(marketClock.snapshot(symbols)).thenReturn(snapshotFor(symbols, MarketClockPhase.REGULAR))
    symbols.forEach { symbol ->
      whenever(quoteRepository.get(symbol)).thenReturn(quote(symbol, "100") to false)
    }

    scheduler.tick()

    verify(quoteFetchingStrategy, never()).fetch(any())
    verify(quoteRepository, never()).put(any())
  }

  @Test
  fun `chunks symbols using batch size`() {
    val symbols =
      listOf(
        SymbolId.parse("AAPL"),
        SymbolId.parse("TSLA"),
        SymbolId.parse("MSFT"),
        SymbolId.parse("GOOGL"),
        SymbolId.parse("AMZN"),
      )
    whenever(watchListService.enabledSymbols()).thenReturn(symbols)
    whenever(marketClock.snapshot(symbols)).thenReturn(snapshotFor(symbols, MarketClockPhase.PRE))
    whenever(refreshPolicy.batchSize(MarketClockPhase.PRE)).thenReturn(2)

    whenever(quoteFetchingStrategy.fetch(listOf(symbols[0], symbols[1])))
      .thenReturn(
        listOf(
          quote(symbols[0], "150"),
          quote(symbols[1], "700"),
        ),
      )
    whenever(quoteFetchingStrategy.fetch(listOf(symbols[2], symbols[3])))
      .thenReturn(
        listOf(
          quote(symbols[2], "300"),
          quote(symbols[3], "2500"),
        ),
      )
    whenever(quoteFetchingStrategy.fetch(listOf(symbols[4])))
      .thenReturn(listOf(quote(symbols[4], "3000")))

    scheduler.tick()

    verify(quoteFetchingStrategy).fetch(listOf(symbols[0], symbols[1]))
    verify(quoteFetchingStrategy).fetch(listOf(symbols[2], symbols[3]))
    verify(quoteFetchingStrategy).fetch(listOf(symbols[4]))
    verify(quoteRepository, times(5)).put(any())
  }

  @Test
  fun `uses batch size according to snapshot phase`() {
    val symbols = listOf(SymbolId.parse("AAPL"))
    whenever(watchListService.enabledSymbols()).thenReturn(symbols)
    whenever(quoteFetchingStrategy.fetch(any())).thenReturn(listOf(quote(symbols[0], "150")))

    whenever(
      marketClock.snapshot(symbols),
    ).thenReturn(snapshotFor(symbols, MarketClockPhase.REGULAR))
      .thenReturn(snapshotFor(symbols, MarketClockPhase.PRE))
      .thenReturn(snapshotFor(symbols, MarketClockPhase.AFTER))
      .thenReturn(snapshotFor(symbols, MarketClockPhase.NIGHT))

    whenever(refreshPolicy.batchSize(MarketClockPhase.REGULAR)).thenReturn(60)
    whenever(refreshPolicy.batchSize(MarketClockPhase.PRE)).thenReturn(40)
    whenever(refreshPolicy.batchSize(MarketClockPhase.AFTER)).thenReturn(40)
    whenever(refreshPolicy.batchSize(MarketClockPhase.NIGHT)).thenReturn(10)

    repeat(4) { scheduler.tick() }

    verify(refreshPolicy).batchSize(MarketClockPhase.REGULAR)
    verify(refreshPolicy).batchSize(MarketClockPhase.PRE)
    verify(refreshPolicy).batchSize(MarketClockPhase.AFTER)
    verify(refreshPolicy, never()).batchSize(MarketClockPhase.NIGHT)
  }

  @Test
  fun `handles provider errors gracefully`() {
    val symbols = listOf(SymbolId.parse("AAPL"))
    whenever(watchListService.enabledSymbols()).thenReturn(symbols)
    whenever(marketClock.snapshot(symbols)).thenReturn(snapshotFor(symbols, MarketClockPhase.REGULAR))
    whenever(refreshPolicy.batchSize(MarketClockPhase.REGULAR)).thenReturn(60)

    whenever(quoteFetchingStrategy.fetch(symbols)).thenThrow(RuntimeException("Boom"))

    scheduler.tick()

    verify(quoteFetchingStrategy).fetch(symbols)
    verify(quoteRepository, never()).put(any())
  }

  @Test
  fun `handles empty provider response`() {
    val symbols = listOf(SymbolId.parse("AAPL"))
    whenever(watchListService.enabledSymbols()).thenReturn(symbols)
    whenever(marketClock.snapshot(symbols)).thenReturn(snapshotFor(symbols, MarketClockPhase.REGULAR))
    whenever(refreshPolicy.batchSize(MarketClockPhase.REGULAR)).thenReturn(60)

    whenever(quoteFetchingStrategy.fetch(symbols)).thenReturn(emptyList())

    scheduler.tick()

    verify(quoteFetchingStrategy).fetch(symbols)
    verify(quoteRepository, never()).put(any())
  }

  @Test
  fun `processes more symbols than batch size`() {
    val symbols = (1..150).map { SymbolId.parse("SYM$it") }
    whenever(watchListService.enabledSymbols()).thenReturn(symbols)
    whenever(marketClock.snapshot(symbols)).thenReturn(snapshotFor(symbols, MarketClockPhase.REGULAR))
    whenever(refreshPolicy.batchSize(MarketClockPhase.REGULAR)).thenReturn(60)

    whenever(quoteFetchingStrategy.fetch(any())).thenAnswer { invocation ->
      val requested = invocation.getArgument<List<SymbolId>>(0)
      requested.map { quote(it, "100") }
    }

    scheduler.tick()

    verify(quoteFetchingStrategy, times(3)).fetch(any())
    verify(quoteRepository, times(150)).put(any())
  }

  private fun snapshotFor(
    symbols: List<SymbolId>,
    overall: MarketClockPhase,
  ): MarketClockSnapshot = snapshotFor(symbols.associateWith { overall }, overall)

  private fun snapshotFor(
    phases: Map<SymbolId, MarketClockPhase>,
    overall: MarketClockPhase,
  ): MarketClockSnapshot {
    val now = Instant.now()
    val tz = phases.keys.associateWith { "America/New_York" }
    return MarketClockSnapshot(
      fetchedAt = now,
      overallPhase = overall,
      phasesBySymbol = phases,
      timezoneBySymbol = tz,
    )
  }

  private fun quote(
    symbolId: SymbolId,
    price: String,
  ): Quote =
    Quote(
      symbol = symbolId,
      last = BigDecimal(price),
      open = null,
      high = null,
      low = null,
      prevClose = null,
      source = "TEST",
    )
}
