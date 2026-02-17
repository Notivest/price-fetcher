package com.notivest.pricefetcher.controllers

import com.notivest.pricefetcher.models.MarketClock
import com.notivest.pricefetcher.models.MarketClockPhase
import com.notivest.pricefetcher.models.MarketClockSnapshot
import com.notivest.pricefetcher.models.SymbolId
import com.notivest.pricefetcher.models.WatchListItem
import com.notivest.pricefetcher.repositories.interfaces.CandleRepository
import com.notivest.pricefetcher.repositories.interfaces.QuoteRepository
import com.notivest.pricefetcher.service.WatchListService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class HealthControllerTest {
    private lateinit var quoteRepository: QuoteRepository
    private lateinit var candleRepository: CandleRepository
    private lateinit var watchListService: WatchListService
    private lateinit var marketClock: MarketClock
    private lateinit var controller: HealthController

    @BeforeEach
    fun setUp() {
        quoteRepository = mock()
        candleRepository = mock()
        watchListService = mock()
        marketClock = mock()
        controller = HealthController(quoteRepository, candleRepository, watchListService, marketClock)
    }

    @Test
    fun `returns health payload with market cache and watchlist details`() {
        val aapl = SymbolId.parse("AAPL")
        val btc = SymbolId.parse("BTCUSD")
        val enabledSymbols = listOf(aapl, btc)
        val allSymbols = listOf(WatchListItem("AAPL"), WatchListItem("BTCUSD"), WatchListItem("MSFT", enabled = false))
        val fetchedAt = Instant.parse("2024-01-01T12:00:00Z")

        whenever(watchListService.enabledSymbols()).thenReturn(enabledSymbols)
        whenever(watchListService.list()).thenReturn(allSymbols)
        whenever(quoteRepository.count()).thenReturn(2)
        whenever(candleRepository.count()).thenReturn(10)
        whenever(quoteRepository.symbols()).thenReturn(setOf(aapl, btc))
        whenever(marketClock.snapshot(eq(enabledSymbols))).thenReturn(
            MarketClockSnapshot(
                fetchedAt = fetchedAt,
                overallPhase = MarketClockPhase.REGULAR,
                phasesBySymbol = mapOf(aapl to MarketClockPhase.REGULAR, btc to MarketClockPhase.NIGHT),
                timezoneBySymbol = mapOf(aapl to "America/New_York", btc to "Etc/UTC"),
            ),
        )

        val response = controller.health()
        val body = response.body!!

        assertThat(response.statusCode.value()).isEqualTo(200)
        assertThat(body["status"]).isEqualTo("UP")

        @Suppress("UNCHECKED_CAST")
        val market = body["market"] as Map<String, Any?>
        assertThat(market["phase"]).isEqualTo("REGULAR")
        assertThat(market["fetched_at"]).isEqualTo(fetchedAt)
        assertThat(market["open_symbols"]).isEqualTo(listOf("AAPL"))
        assertThat(market["closed_symbols"]).isEqualTo(listOf("BTCUSD"))

        @Suppress("UNCHECKED_CAST")
        val cache = body["cache"] as Map<String, Any?>
        assertThat(cache["quotes"]).isEqualTo(2)
        assertThat(cache["candles"]).isEqualTo(10)
        assertThat(cache["symbols"]).isEqualTo(listOf("AAPL", "BTCUSD"))

        @Suppress("UNCHECKED_CAST")
        val watchlist = body["watchlist"] as Map<String, Any?>
        assertThat(watchlist["total"]).isEqualTo(3)
        assertThat(watchlist["enabled"]).isEqualTo(2)
        assertThat(watchlist["disabled"]).isEqualTo(1)
        assertThat(watchlist["enabled_symbols"]).isEqualTo(listOf("AAPL", "BTCUSD"))

        verify(marketClock).snapshot(enabledSymbols)
    }
}
