package com.notivest.pricefetcher.controllers

import com.notivest.pricefetcher.dto.QuoteDto
import com.notivest.pricefetcher.models.SymbolId
import com.notivest.pricefetcher.service.MarketDataService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant

class QuotesControllerTest {
    private lateinit var service: MarketDataService
    private lateinit var controller: QuotesController

    @BeforeEach
    fun setUp() {
        service = mock()
        controller = QuotesController(service)
    }

    @Test
    fun `returns bad request when symbols is blank`() {
        val response = controller.getQuotes("   ")

        assertThat(response.statusCode.value()).isEqualTo(400)
        assertThat(response.body).isEqualTo(mapOf("error" to "symbols parameter is required and cannot be empty"))
    }

    @Test
    fun `returns bad request when parsed symbols are empty`() {
        val response = controller.getQuotes(" , , ")

        assertThat(response.statusCode.value()).isEqualTo(400)
        assertThat(response.body).isEqualTo(mapOf("error" to "No valid symbols provided"))
    }

    @Test
    fun `returns bad request when too many symbols are requested`() {
        val symbols = (1..51).joinToString(",") { "SYM$it" }

        val response = controller.getQuotes(symbols)

        assertThat(response.statusCode.value()).isEqualTo(400)
        assertThat(response.body).isEqualTo(mapOf("error" to "Too many symbols requested (max 50)"))
    }

    @Test
    fun `returns not found when service has no quote`() {
        whenever(service.getQuotes(any())).thenThrow(NoSuchElementException("missing"))

        val response = controller.getQuotes("AAPL")

        assertThat(response.statusCode.value()).isEqualTo(404)
    }

    @Test
    fun `returns bad request when symbol format is invalid`() {
        whenever(service.getQuotes(any())).thenThrow(IllegalArgumentException("invalid symbol"))

        val response = controller.getQuotes("AAPL")

        assertThat(response.statusCode.value()).isEqualTo(400)
        assertThat((response.body as Map<*, *>)["error"].toString()).contains("Invalid symbol format")
    }

    @Test
    fun `returns internal server error on unexpected exception`() {
        whenever(service.getQuotes(any())).thenThrow(RuntimeException("boom"))

        val response = controller.getQuotes("AAPL")

        assertThat(response.statusCode.value()).isEqualTo(500)
        assertThat(response.body).isEqualTo(mapOf("error" to "Internal server error"))
    }

    @Test
    fun `returns quotes on valid request`() {
        val quote =
            QuoteDto(
                symbol = "AAPL",
                last = BigDecimal("201.50"),
                ts = Instant.parse("2024-01-01T00:00:00Z"),
                open = BigDecimal("199.00"),
                high = BigDecimal("202.00"),
                low = BigDecimal("198.00"),
                prevClose = BigDecimal("198.50"),
                currency = "USD",
                source = "test",
                stale = false,
            )
        whenever(service.getQuotes(any())).thenReturn(listOf(quote))

        val response = controller.getQuotes(" AAPL , MSFT ")

        assertThat(response.statusCode.value()).isEqualTo(200)
        assertThat(response.body).isEqualTo(listOf(quote))
        verify(service).getQuotes(
            argThat { contains(SymbolId.parse("AAPL")) && contains(SymbolId.parse("MSFT")) && size == 2 },
        )
    }
}
