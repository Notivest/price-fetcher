package com.notivest.pricefetcher.client

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
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PolygonProviderTest {
  private lateinit var mockWebServer: MockWebServer
  private lateinit var provider: PolygonProvider
  private lateinit var props: ProviderProperties

  @BeforeEach
  fun setUp() {
    mockWebServer = MockWebServer()
    mockWebServer.start()

    props =
      ProviderProperties().apply {
        polygon.baseUrl = mockWebServer.url("/").toString().removeSuffix("/")
        polygon.apiKey = "test-api-key"
      }

    val webClient = WebClient.builder().build()
    provider = PolygonProvider(props, webClient)
  }

  @AfterEach
  fun tearDown() {
    mockWebServer.shutdown()
  }

  @Test
  fun `should fetch historical data successfully and map correctly`() {
    val mockResponse =
      MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(
          """
          {
            "results": [
              {
                "t": 1713300000000,
                "o": 170.0,
                "h": 172.0,
                "l": 169.5,
                "c": 171.1,
                "v": 1000000
              },
              {
                "t": 1713386400000,
                "o": 171.1,
                "h": 173.0,
                "l": 170.7,
                "c": 172.2,
                "v": 1100000
              }
            ]
          }
          """.trimIndent(),
        )

    mockWebServer.enqueue(mockResponse)

    val symbol = SymbolId.parse("AAPL")
    val from = Instant.parse("2024-04-16T00:00:00Z")
    val to = Instant.parse("2024-04-17T00:00:00Z")
    val timeframe = Timeframe.T1D

    val series = provider.fetchHistorical(symbol, from, to, timeframe)

    assertEquals(symbol, series.symbol)
    assertEquals(timeframe, series.timeframe)
    assertEquals(2, series.items.size)

    val candle1 = series.items[0]
    assertEquals(Instant.ofEpochMilli(1713300000000L), candle1.ts)
    assertEquals(BigDecimal("170.0"), candle1.o)
    assertEquals(BigDecimal("172.0"), candle1.h)
    assertEquals(BigDecimal("169.5"), candle1.l)
    assertEquals(BigDecimal("171.1"), candle1.c)
    assertEquals(1000000L, candle1.v)
    assertTrue(candle1.adjusted)

    val candle2 = series.items[1]
    assertEquals(Instant.ofEpochMilli(1713386400000L), candle2.ts)
    assertEquals(BigDecimal("172.2"), candle2.c)

    // Verify request was made correctly
    val request = mockWebServer.takeRequest()
    assertEquals("GET", request.method)
    assertTrue(request.path!!.contains("/v2/aggs/ticker/AAPL/range/1/day"))
    assertTrue(request.path!!.contains("apiKey=test-api-key"))
    assertTrue(request.path!!.contains("adjusted=true"))
  }

  @Test
  fun `should sort results by timestamp ascending`() {
    val mockResponse =
      MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(
          """
          {
            "results": [
              {
                "t": 1713386400000,
                "o": 171.1,
                "h": 173.0,
                "l": 170.7,
                "c": 172.2,
                "v": 1100000
              },
              {
                "t": 1713300000000,
                "o": 170.0,
                "h": 172.0,
                "l": 169.5,
                "c": 171.1,
                "v": 1000000
              }
            ]
          }
          """.trimIndent(),
        )

    mockWebServer.enqueue(mockResponse)

    val symbol = SymbolId.parse("AAPL")
    val from = Instant.parse("2024-04-16T00:00:00Z")
    val to = Instant.parse("2024-04-17T00:00:00Z")
    val timeframe = Timeframe.T1D

    val series = provider.fetchHistorical(symbol, from, to, timeframe)

    assertEquals(2, series.items.size)
    // Should be sorted by timestamp (earliest first)
    assertTrue(series.items[0].ts.isBefore(series.items[1].ts))
    assertEquals(Instant.ofEpochMilli(1713300000000L), series.items[0].ts)
    assertEquals(Instant.ofEpochMilli(1713386400000L), series.items[1].ts)
  }

  @Test
  fun `should handle different timeframes correctly`() {
    val timeframeMappings =
      mapOf(
        Timeframe.T1D to "1/day",
        Timeframe.T1H to "1/hour",
        Timeframe.T15M to "15/minute",
        Timeframe.T5M to "5/minute",
        Timeframe.T1M to "1/minute",
      )

    timeframeMappings.forEach { (timeframe, expectedPath) ->
      val mockResponse =
        MockResponse()
          .setResponseCode(200)
          .setHeader("Content-Type", "application/json")
          .setBody("""{"results": []}""")

      mockWebServer.enqueue(mockResponse)

      val symbol = SymbolId.parse("AAPL")
      val from = Instant.parse("2024-04-16T00:00:00Z")
      val to = Instant.parse("2024-04-17T00:00:00Z")

      provider.fetchHistorical(symbol, from, to, timeframe)

      val request = mockWebServer.takeRequest()
      assertTrue(request.path!!.contains("/v2/aggs/ticker/AAPL/range/$expectedPath"))
    }
  }

  @Test
  fun `should handle rate limiting with 429 status`() {
    val mockResponse =
      MockResponse()
        .setResponseCode(429)
        .setHeader("Content-Type", "text/plain")
        .setBody("Rate limit exceeded")

    mockWebServer.enqueue(mockResponse)

    val symbol = SymbolId.parse("AAPL")
    val from = Instant.parse("2024-04-16T00:00:00Z")
    val to = Instant.parse("2024-04-17T00:00:00Z")
    val timeframe = Timeframe.T1D

    val exception =
      assertThrows<RuntimeException> {
        provider.fetchHistorical(symbol, from, to, timeframe)
      }

    assertTrue(exception.message!!.contains("Polygon rate-limited"))
  }

  @Test
  fun `should handle empty results gracefully`() {
    val mockResponse =
      MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody("""{"results": []}""")

    mockWebServer.enqueue(mockResponse)

    val symbol = SymbolId.parse("AAPL")
    val from = Instant.parse("2024-04-16T00:00:00Z")
    val to = Instant.parse("2024-04-17T00:00:00Z")
    val timeframe = Timeframe.T1D

    val series = provider.fetchHistorical(symbol, from, to, timeframe)

    assertEquals(symbol, series.symbol)
    assertEquals(timeframe, series.timeframe)
    assertEquals(0, series.items.size)
  }

  @Test
  fun `should handle missing results field gracefully`() {
    val mockResponse =
      MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody("""{}""")

    mockWebServer.enqueue(mockResponse)

    val symbol = SymbolId.parse("AAPL")
    val from = Instant.parse("2024-04-16T00:00:00Z")
    val to = Instant.parse("2024-04-17T00:00:00Z")
    val timeframe = Timeframe.T1D

    val series = provider.fetchHistorical(symbol, from, to, timeframe)

    assertEquals(0, series.items.size)
  }

  @Test
  fun `should handle null response gracefully`() {
    val mockResponse =
      MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody("null")

    mockWebServer.enqueue(mockResponse)

    val symbol = SymbolId.parse("AAPL")
    val from = Instant.parse("2024-04-16T00:00:00Z")
    val to = Instant.parse("2024-04-17T00:00:00Z")
    val timeframe = Timeframe.T1D

    val series = provider.fetchHistorical(symbol, from, to, timeframe)

    assertEquals(0, series.items.size)
  }

  @Test
  fun `should format dates correctly in URL`() {
    val mockResponse =
      MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody("""{"results": []}""")

    mockWebServer.enqueue(mockResponse)

    val symbol = SymbolId.parse("AAPL")
    val from = Instant.parse("2024-04-16T12:30:45Z") // Time should be stripped
    val to = Instant.parse("2024-04-17T18:15:30Z") // Time should be stripped
    val timeframe = Timeframe.T1D

    provider.fetchHistorical(symbol, from, to, timeframe)

    val request = mockWebServer.takeRequest()
    assertTrue(request.path!!.contains("2024-04-16"))
    assertTrue(request.path!!.contains("2024-04-17"))
    // Should not contain time components
    assertTrue(!request.path!!.contains("12:30"))
    assertTrue(!request.path!!.contains("18:15"))
  }

  @Test
  fun `should handle symbols with exchange notation`() {
    val mockResponse =
      MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody("""{"results": []}""")

    mockWebServer.enqueue(mockResponse)

    val symbol = SymbolId.parse("AAPL.MX")
    val from = Instant.parse("2024-04-16T00:00:00Z")
    val to = Instant.parse("2024-04-17T00:00:00Z")
    val timeframe = Timeframe.T1D

    val series = provider.fetchHistorical(symbol, from, to, timeframe)

    assertEquals(symbol, series.symbol)

    val request = mockWebServer.takeRequest()
    assertTrue(request.path!!.contains("/v2/aggs/ticker/AAPL.MX/range"))
  }

  @Test
  fun `should throw exception for missing configuration`() {
    val invalidProps =
      ProviderProperties().apply {
        polygon.baseUrl = ""
        polygon.apiKey = ""
      }

    val webClient = WebClient.builder().build()
    val invalidProvider = PolygonProvider(invalidProps, webClient)

    val exception =
      assertThrows<IllegalArgumentException> {
        invalidProvider.fetchHistorical(
          SymbolId.parse("AAPL"),
          Instant.now(),
          Instant.now(),
          Timeframe.T1D,
        )
      }

    assertTrue(exception.message!!.contains("Polygon base-url/api-key not configured"))
  }

  @Test
  fun `should throw UnsupportedOperationException for quotes`() {
    val exception =
      assertThrows<UnsupportedOperationException> {
        provider.fetchQuotes(listOf(SymbolId.parse("AAPL")))
      }

    assertTrue(exception.message!!.contains("Use FinnhubProvider for quotes"))
  }

  @Test
  fun `should return correct provider name`() {
    assertEquals("POLYGON", provider.getName())
  }

  @Test
  fun `should include correct query parameters`() {
    val mockResponse =
      MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody("""{"results": []}""")

    mockWebServer.enqueue(mockResponse)

    val symbol = SymbolId.parse("AAPL")
    val from = Instant.parse("2024-04-16T00:00:00Z")
    val to = Instant.parse("2024-04-17T00:00:00Z")
    val timeframe = Timeframe.T1D

    provider.fetchHistorical(symbol, from, to, timeframe)

    val request = mockWebServer.takeRequest()
    val path = request.path!!
    assertTrue(path.contains("adjusted=true"))
    assertTrue(path.contains("limit=50000"))
    assertTrue(path.contains("apiKey=test-api-key"))
  }

  @Test
  fun `should handle server error gracefully`() {
    val mockResponse =
      MockResponse()
        .setResponseCode(500)
        .setBody("Internal Server Error")

    mockWebServer.enqueue(mockResponse)

    val symbol = SymbolId.parse("AAPL")
    val from = Instant.parse("2024-04-16T00:00:00Z")
    val to = Instant.parse("2024-04-17T00:00:00Z")
    val timeframe = Timeframe.T1D

    // Should throw an exception for server errors
    assertThrows<RuntimeException> {
      provider.fetchHistorical(symbol, from, to, timeframe)
    }
  }
}
