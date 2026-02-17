package com.notivest.pricefetcher.controllers

import com.notivest.pricefetcher.models.SymbolId
import com.notivest.pricefetcher.service.MarketDataService
import com.notivest.pricefetcher.service.WatchListService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PrefetchControllerTest {
    private lateinit var watchListService: WatchListService
    private lateinit var marketDataService: MarketDataService
    private lateinit var controller: PrefetchController

    @BeforeEach
    fun setUp() {
        watchListService = mock()
        marketDataService = mock()
        controller = PrefetchController(watchListService, marketDataService)
    }

    @Test
    fun `returns zero prefetch result when no symbols are enabled`() {
        whenever(watchListService.enabledSymbols()).thenReturn(emptyList())

        val response = controller.prefetch()

        assertThat(response.statusCode.value()).isEqualTo(200)
        assertThat(response.body).isEqualTo(
            mapOf(
                "prefetched" to 0,
                "symbols" to emptyList<String>(),
                "message" to "No enabled symbols in watchlist",
            ),
        )
    }

    @Test
    fun `returns prefetch count and symbols on success`() {
        val symbols = listOf(SymbolId.parse("AAPL"), SymbolId.parse("MSFT"))
        whenever(watchListService.enabledSymbols()).thenReturn(symbols)
        whenever(marketDataService.prefetch(eq(symbols))).thenReturn(2)

        val response = controller.prefetch()

        assertThat(response.statusCode.value()).isEqualTo(200)
        assertThat(response.body).isEqualTo(
            mapOf(
                "prefetched" to 2,
                "symbols" to listOf("AAPL", "MSFT"),
            ),
        )
        verify(marketDataService).prefetch(symbols)
    }

    @Test
    fun `returns internal server error when prefetch fails`() {
        whenever(watchListService.enabledSymbols()).thenReturn(listOf(SymbolId.parse("AAPL")))
        whenever(marketDataService.prefetch(org.mockito.kotlin.any())).thenThrow(RuntimeException("upstream down"))

        val response = controller.prefetch()

        assertThat(response.statusCode.value()).isEqualTo(500)
        assertThat(response.body).isEqualTo(
            mapOf(
                "error" to "Prefetch failed",
                "message" to "upstream down",
            ),
        )
    }
}
