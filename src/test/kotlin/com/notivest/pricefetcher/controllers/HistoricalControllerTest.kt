package com.notivest.pricefetcher.controllers

import com.notivest.pricefetcher.models.Candle
import com.notivest.pricefetcher.models.CandleSeries
import com.notivest.pricefetcher.models.SymbolId
import com.notivest.pricefetcher.models.Timeframe
import com.notivest.pricefetcher.service.MarketDataService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.math.BigDecimal
import java.time.Instant

@WebMvcTest(HistoricalController::class)
class HistoricalControllerTest {
  @Autowired
  private lateinit var mockMvc: MockMvc

  @MockBean
  private lateinit var marketDataService: MarketDataService

  @Test
  fun `should return historical data with valid parameters`() {
    val symbol = "AAPL"
    val from = "2024-01-01T00:00:00Z"
    val to = "2024-01-02T00:00:00Z"
    val tf = "T1D"

    val mockCandles =
      listOf(
        Candle(
          ts = Instant.parse("2024-01-01T09:30:00Z"),
          o = BigDecimal("150.00"),
          h = BigDecimal("155.00"),
          l = BigDecimal("149.00"),
          c = BigDecimal("154.00"),
          v = 1000000,
          adjusted = true,
        ),
      )

    val mockSeries =
      CandleSeries(
        symbol = SymbolId.parse(symbol),
        timeframe = Timeframe.T1D,
        items = mockCandles,
      )

    whenever(marketDataService.historical(any(), any(), any(), any(), any())).thenReturn(mockSeries)

    mockMvc.perform(
      get("/historical")
        .param("symbol", symbol)
        .param("from", from)
        .param("to", to)
        .param("tf", tf)
        .param("adjusted", "true"),
    )
      .andExpect(status().isOk)
      .andExpect(content().contentType(MediaType.APPLICATION_JSON))
      .andExpect(jsonPath("$.symbol.ticker").value(symbol))
      .andExpect(jsonPath("$.timeframe").value("T1D"))
      .andExpect(jsonPath("$.items").isArray)
      .andExpect(jsonPath("$.items.length()").value(1))
  }

  @Test
  fun `should return 400 for invalid symbol`() {
    mockMvc.perform(
      get("/historical")
        .param("symbol", "")
        .param("from", "2024-01-01T00:00:00Z")
        .param("to", "2024-01-02T00:00:00Z")
        .param("tf", "T1D"),
    )
      .andExpect(status().isBadRequest)
  }

  @Test
  fun `should return 400 for invalid date format`() {
    mockMvc.perform(
      get("/historical")
        .param("symbol", "AAPL")
        .param("from", "invalid-date")
        .param("to", "2024-01-02T00:00:00Z")
        .param("tf", "T1D"),
    )
      .andExpect(status().isBadRequest)
  }
}
