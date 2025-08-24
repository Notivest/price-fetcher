package com.notivest.pricefetcher.controllers

import com.notivest.pricefetcher.models.SymbolId
import com.notivest.pricefetcher.service.MarketDataService
import com.notivest.pricefetcher.service.WatchListService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest(PrefetchController::class)
class PrefetchControllerTest {
  @Autowired
  private lateinit var mockMvc: MockMvc

  @MockitoBean
  private lateinit var watchListService: WatchListService

  @MockitoBean
  private lateinit var marketDataService: MarketDataService

  @Test
  fun `should prefetch quotes for enabled symbols`() {
    val enabledSymbols = listOf(SymbolId.parse("AAPL"), SymbolId.parse("TSLA"))

    whenever(watchListService.enabledSymbols()).thenReturn(enabledSymbols)
    whenever(marketDataService.prefetch(enabledSymbols)).thenReturn(2)

    mockMvc.perform(post("/prefetch"))
      .andExpect(status().isOk)
      .andExpect(content().contentType(MediaType.APPLICATION_JSON))
      .andExpect(jsonPath("$.prefetched").value(2))
      .andExpect(jsonPath("$.symbols").isArray)
      .andExpect(jsonPath("$.symbols.length()").value(2))
  }

  @Test
  fun `should handle empty watchlist gracefully`() {
    whenever(watchListService.enabledSymbols()).thenReturn(emptyList())

    mockMvc.perform(post("/prefetch"))
      .andExpect(status().isOk)
      .andExpect(content().contentType(MediaType.APPLICATION_JSON))
      .andExpect(jsonPath("$.prefetched").value(0))
      .andExpect(jsonPath("$.symbols").isArray)
      .andExpect(jsonPath("$.symbols.length()").value(0))
  }
}
