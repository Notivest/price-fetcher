package com.notivest.pricefetcher.controllers

import com.notivest.pricefetcher.models.Candle
import com.notivest.pricefetcher.models.CandleSeries
import com.notivest.pricefetcher.models.SymbolId
import com.notivest.pricefetcher.models.Timeframe
import com.notivest.pricefetcher.provider.ProviderRateLimitException
import com.notivest.pricefetcher.service.MarketDataService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant

class HistoricalControllerTest {
    private lateinit var service: MarketDataService
    private lateinit var controller: HistoricalController

    @BeforeEach
    fun setUp() {
        service = mock()
        controller = HistoricalController(service)
    }

    @Test
    fun `returns bad request when symbol is blank`() {
        val response = controller.historical("   ", "2024-01-01T00:00:00Z", "2024-01-02T00:00:00Z", adjusted = true)

        assertThat(response.statusCode.value()).isEqualTo(400)
        assertThat(response.body).isEqualTo(mapOf("error" to "symbol parameter is required and cannot be empty"))
    }

    @Test
    fun `returns bad request when dates are blank`() {
        val response = controller.historical("AAPL", "", "", adjusted = true)

        assertThat(response.statusCode.value()).isEqualTo(400)
        assertThat(response.body).isEqualTo(mapOf("error" to "from and to parameters are required"))
    }

    @Test
    fun `returns bad request when from is after to`() {
        val response =
            controller.historical(
                symbol = "AAPL",
                from = "2024-01-03T00:00:00Z",
                to = "2024-01-02T00:00:00Z",
                adjusted = true,
            )

        assertThat(response.statusCode.value()).isEqualTo(400)
        assertThat(response.body).isEqualTo(mapOf("error" to "from date must be before to date"))
    }

    @Test
    fun `returns bad request on invalid timeframe`() {
        val response =
            controller.historical(
                symbol = "AAPL",
                from = "2024-01-01T00:00:00Z",
                to = "2024-01-02T00:00:00Z",
                tf = "invalid",
                adjusted = true,
            )

        assertThat(response.statusCode.value()).isEqualTo(400)
        assertThat((response.body as Map<*, *>)["error"].toString()).contains("Invalid argument")
    }

    @Test
    fun `returns bad request on invalid date format`() {
        val response =
            controller.historical(
                symbol = "AAPL",
                from = "invalid-date",
                to = "2024-01-02T00:00:00Z",
                adjusted = true,
            )

        assertThat(response.statusCode.value()).isEqualTo(400)
        assertThat((response.body as Map<*, *>)["error"])
            .isEqualTo("Invalid date format. Use ISO-8601 format (e.g., 2024-01-01T00:00:00Z)")
    }

    @Test
    fun `returns internal server error on unexpected exception`() {
        whenever(service.historical(any(), any(), any(), any(), any())).thenThrow(RuntimeException("boom"))

        val response =
            controller.historical(
                symbol = "AAPL",
                from = "2024-01-01T00:00:00Z",
                to = "2024-01-02T00:00:00Z",
                adjusted = true,
            )

        assertThat(response.statusCode.value()).isEqualTo(500)
        assertThat(response.body).isEqualTo(mapOf("error" to "Internal server error"))
    }

    @Test
    fun `returns too many requests when provider rate-limits`() {
        whenever(service.historical(any(), any(), any(), any(), any()))
            .thenThrow(ProviderRateLimitException("Polygon rate-limited for AAPL"))

        val response =
            controller.historical(
                symbol = "AAPL",
                from = "2024-01-01T00:00:00Z",
                to = "2024-01-02T00:00:00Z",
                adjusted = true,
            )

        assertThat(response.statusCode.value()).isEqualTo(429)
        assertThat(response.body).isEqualTo(mapOf("error" to "Polygon rate-limited for AAPL"))
    }

    @Test
    fun `returns candle series on valid request`() {
        val symbol = SymbolId.parse("AAPL")
        val from = Instant.parse("2024-01-01T00:00:00Z")
        val to = Instant.parse("2024-01-02T00:00:00Z")
        val series =
            CandleSeries(
                symbol = symbol,
                timeframe = Timeframe.T1D,
                items =
                    listOf(
                        Candle(
                            ts = from,
                            o = BigDecimal.ONE,
                            h = BigDecimal("2"),
                            l = BigDecimal("0.5"),
                            c = BigDecimal("1.5"),
                            v = 100L,
                        ),
                    ),
            )
        whenever(service.historical(eq(symbol), eq(from), eq(to), eq(Timeframe.T1D), eq(true))).thenReturn(series)

        val response =
            controller.historical(
                symbol = " AAPL ",
                from = "2024-01-01T00:00:00Z",
                to = "2024-01-02T00:00:00Z",
                tf = "t1d",
                adjusted = true,
            )

        assertThat(response.statusCode.value()).isEqualTo(200)
        assertThat(response.body).isEqualTo(series)
        verify(service).historical(eq(symbol), eq(from), eq(to), eq(Timeframe.T1D), eq(true))
    }
}
