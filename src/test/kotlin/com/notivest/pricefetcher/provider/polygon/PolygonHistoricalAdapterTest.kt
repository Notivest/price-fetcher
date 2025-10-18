package com.notivest.pricefetcher.provider.polygon

import com.notivest.pricefetcher.config.ProviderProperties
import com.notivest.pricefetcher.models.SymbolId
import com.notivest.pricefetcher.models.Timeframe
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.reactive.function.client.WebClient
import java.time.Instant
import kotlin.test.assertEquals

class PolygonHistoricalAdapterTest {
  private lateinit var mockWebServer: MockWebServer
  private lateinit var adapter: PolygonHistoricalAdapter
  private lateinit var properties: ProviderProperties

  @BeforeEach
  fun setUp() {
    mockWebServer = MockWebServer()
    mockWebServer.start()
    properties =
      ProviderProperties().apply {
        polygon.baseUrl = mockWebServer.url("/").toString().removeSuffix("/")
        polygon.apiKey = "test-token"
      }
    adapter = PolygonHistoricalAdapter(properties, WebClient.builder().build())
  }

  @AfterEach
  fun tearDown() {
    mockWebServer.shutdown()
  }

  @Test
  fun `transforms aggregated candles into series`() {
    val body =
      """
      {
        "results": [
          {"t": 1700000000000, "o": 100.0, "h": 105.0, "l": 99.0, "c": 102.5, "v": 10000},
          {"t": 1700000060000, "o": 102.5, "h": 106.0, "l": 101.0, "c": 104.0, "v": 8000}
        ]
      }
      """.trimIndent()

    mockWebServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body),
    )

    val symbol = SymbolId.parse("AAPL")
    val from = Instant.parse("2023-11-14T12:00:00Z")
    val to = Instant.parse("2023-11-14T12:10:00Z")
    val series = adapter.fetchHistorical(symbol, from, to, Timeframe.T5M)

    assertEquals(symbol, series.symbol)
    assertEquals(Timeframe.T5M, series.timeframe)
    assertEquals(2, series.items.size)
    assertEquals(Instant.ofEpochMilli(1700000000000), series.items.first().ts)
    assertEquals(true, series.items.first().adjusted)
  }

  @Test
  fun `propagates errors from provider`() {
    mockWebServer.enqueue(
      MockResponse()
        .setResponseCode(429)
        .setBody("Too many requests"),
    )

    assertThrows<RuntimeException> {
      adapter.fetchHistorical(
        SymbolId.parse("AAPL"),
        Instant.parse("2023-11-14T12:00:00Z"),
        Instant.parse("2023-11-14T12:10:00Z"),
        Timeframe.T5M,
      )
    }
  }
}
