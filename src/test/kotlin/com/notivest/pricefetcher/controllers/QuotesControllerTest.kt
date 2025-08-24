package com.notivest.pricefetcher.controllers

import com.notivest.pricefetcher.dto.QuoteDto
import com.notivest.pricefetcher.service.MarketDataService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.math.BigDecimal
import java.time.Instant

@WebMvcTest(QuotesController::class)
class QuotesControllerTest {
  @Autowired
  private lateinit var mockMvc: MockMvc

  @MockitoBean
  private lateinit var marketDataService: MarketDataService

  @Test
  fun `should return quotes for valid symbols`() {
    val mockQuotes =
      listOf(
        QuoteDto(
          symbol = "AAPL",
          last = BigDecimal("150.00"),
          open = BigDecimal("148.00"),
          high = BigDecimal("152.00"),
          low = BigDecimal("147.00"),
          prevClose = BigDecimal("149.00"),
          ts = Instant.now(),
          currency = "USD",
          source = "TEST",
          stale = false,
        ),
      )

    whenever(marketDataService.getQuotes(any())).thenReturn(mockQuotes)

    mockMvc.perform(
      get("/quotes")
        .param("symbols", "AAPL"),
    )
      .andExpect(status().isOk)
      .andExpect(content().contentType(MediaType.APPLICATION_JSON))
      .andExpect(jsonPath("$").isArray)
      .andExpect(jsonPath("$.length()").value(1))
      .andExpect(jsonPath("$[0].symbol").value("AAPL"))
      .andExpect(jsonPath("$[0].last").value(150.00))
  }

  @Test
  fun `should return 400 for empty symbols parameter`() {
    mockMvc.perform(
      get("/quotes")
        .param("symbols", ""),
    )
      .andExpect(status().isBadRequest)
  }

  @Test
  fun `should return 400 for too many symbols`() {
    val symbols = (1..51).map { "SYM$it" }.joinToString(",")

    mockMvc.perform(
      get("/quotes")
        .param("symbols", symbols),
    )
      .andExpect(status().isBadRequest)
  }
}
