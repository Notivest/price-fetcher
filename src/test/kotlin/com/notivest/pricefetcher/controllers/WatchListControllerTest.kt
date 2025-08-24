package com.notivest.pricefetcher.controllers

import com.notivest.pricefetcher.models.WatchListItem
import com.notivest.pricefetcher.service.WatchListService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest(WatchListController::class)
class WatchListControllerTest {
  @Autowired
  private lateinit var mockMvc: MockMvc

  @MockBean
  private lateinit var watchListService: WatchListService

  @Test
  fun `should return watchlist items`() {
    val mockItems =
      listOf(
        WatchListItem(
          symbol = "AAPL",
          enabled = true,
          priority = 1,
        ),
      )

    whenever(watchListService.list()).thenReturn(mockItems)

    mockMvc.perform(get("/watchlist"))
      .andExpect(status().isOk)
      .andExpect(content().contentType(MediaType.APPLICATION_JSON))
      .andExpect(jsonPath("$").isArray)
      .andExpect(jsonPath("$.length()").value(1))
      .andExpect(jsonPath("$[0].symbol").value("AAPL"))
      .andExpect(jsonPath("$[0].enabled").value(true))
  }

  @Test
  fun `should add new watchlist item`() {
    mockMvc.perform(
      post("/watchlist")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"symbol": "AAPL", "enabled": true, "priority": 1}"""),
    )
      .andExpect(status().isOk)
  }

  @Test
  fun `should return 400 for invalid symbol in POST`() {
    doThrow(IllegalArgumentException("symbol is required")).whenever(watchListService).add(any())

    mockMvc.perform(
      post("/watchlist")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"symbol": "", "enabled": true}"""),
    )
      .andExpect(status().isBadRequest)
  }

  @Test
  fun `should update watchlist item`() {
    // No exception means item exists
    mockMvc.perform(
      patch("/watchlist/AAPL")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"enabled": false}"""),
    )
      .andExpect(status().isNoContent)
  }

  @Test
  fun `should delete watchlist item`() {
    // No exception means item exists
    mockMvc.perform(delete("/watchlist/AAPL"))
      .andExpect(status().isNoContent)
  }
}
