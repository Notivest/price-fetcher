package com.notivest.pricefetcher.client

import com.notivest.pricefetcher.config.ProviderProperties
import com.notivest.pricefetcher.models.SymbolId
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

class FinnhubProviderTest {
  private lateinit var mockWebServer: MockWebServer
  private lateinit var provider: FinnhubProvider
  private lateinit var props: ProviderProperties

  @BeforeEach
  fun setUp() {
    mockWebServer = MockWebServer()
    mockWebServer.start()

    props =
      ProviderProperties().apply {
        finnhub.baseUrl = mockWebServer.url("/").toString().removeSuffix("/")
        finnhub.apiKey = "test-api-key"
      }

    val webClient = WebClient.builder().build()
    provider = FinnhubProvider(props, webClient)
  }

  @AfterEach
  fun tearDown() {
    mockWebServer.shutdown()
  }

  @Test
  fun `should fetch quotes successfully and map correctly`() {
    val mockResponse =
      MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(
          """
          {
            "c": 172.35,
            "o": 171.0,
            "h": 173.0,
            "l": 170.5,
            "pc": 170.9,
            "t": 1713369600
          }
          """.trimIndent(),
        )

    mockWebServer.enqueue(mockResponse)

    val symbols = listOf(SymbolId.parse("AAPL"))
    val quotes = provider.fetchQuotes(symbols)

    assertEquals(1, quotes.size)
    val quote = quotes[0]
    assertEquals(SymbolId.parse("AAPL"), quote.symbol)
    assertEquals(BigDecimal("172.35"), quote.last)
    assertEquals(BigDecimal("171.0"), quote.open)
    assertEquals(BigDecimal("173.0"), quote.high)
    assertEquals(BigDecimal("170.5"), quote.low)
    assertEquals(BigDecimal("170.9"), quote.prevClose)
    assertEquals("USD", quote.currency)
    assertEquals("FINNHUB", quote.source)
    assertEquals(Instant.ofEpochSecond(1713369600), quote.ts)

    // Verify request was made correctly
    val request = mockWebServer.takeRequest()
    assertEquals("GET", request.method)
    assertTrue(request.path!!.contains("/quote"))
    assertTrue(request.path!!.contains("symbol=AAPL"))
    assertTrue(request.path!!.contains("token=test-api-key"))
  }

  @Test
  fun `should handle missing fields gracefully`() {
    val mockResponse =
      MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(
          """
          {
            "c": 150.0
          }
          """.trimIndent(),
        )

    mockWebServer.enqueue(mockResponse)

    val symbols = listOf(SymbolId.parse("AAPL"))
    val quotes = provider.fetchQuotes(symbols)

    assertEquals(1, quotes.size)
    val quote = quotes[0]
    assertEquals(BigDecimal("150.0"), quote.last)
    assertEquals(null, quote.open)
    assertEquals(null, quote.high)
    assertEquals(null, quote.low)
    assertEquals(null, quote.prevClose)
  }

  @Test
  fun `should handle null response gracefully`() {
    val mockResponse =
      MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody("null")

    mockWebServer.enqueue(mockResponse)

    val symbols = listOf(SymbolId.parse("AAPL"))
    val quotes = provider.fetchQuotes(symbols)

    assertEquals(1, quotes.size)
    val quote = quotes[0]
    assertEquals(BigDecimal.ZERO, quote.last)
  }

  @Test
  fun `should handle rate limiting with 429 status`() {
    val mockResponse =
      MockResponse()
        .setResponseCode(429)
        .setHeader("Content-Type", "text/plain")
        .setBody("Rate limit exceeded")

    mockWebServer.enqueue(mockResponse)

    val symbols = listOf(SymbolId.parse("AAPL"))

    // Should return empty list since the provider now handles errors gracefully
    val quotes = provider.fetchQuotes(symbols)
    assertEquals(0, quotes.size)
  }

  @Test
  fun `should fetch multiple symbols correctly`() {
    val response1 =
      MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody("""{"c": 150.0, "o": 149.0, "h": 151.0, "l": 148.0, "pc": 149.5, "t": 1713369600}""")

    val response2 =
      MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody("""{"c": 200.0, "o": 199.0, "h": 201.0, "l": 198.0, "pc": 199.5, "t": 1713369700}""")

    mockWebServer.enqueue(response1)
    mockWebServer.enqueue(response2)

    val symbols = listOf(SymbolId.parse("AAPL"), SymbolId.parse("TSLA"))
    val quotes = provider.fetchQuotes(symbols)

    assertEquals(2, quotes.size)
    assertEquals(BigDecimal("150.0"), quotes[0].last)
    assertEquals(BigDecimal("200.0"), quotes[1].last)

    // Verify both requests were made
    val request1 = mockWebServer.takeRequest()
    val request2 = mockWebServer.takeRequest()
    assertTrue(request1.path!!.contains("symbol=AAPL"))
    assertTrue(request2.path!!.contains("symbol=TSLA"))
  }

  @Test
  fun `should handle symbols with exchange notation`() {
    val mockResponse =
      MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody("""{"c": 150.0, "t": 1713369600}""")

    mockWebServer.enqueue(mockResponse)

    val symbols = listOf(SymbolId.parse("AAPL.MX"))
    val quotes = provider.fetchQuotes(symbols)

    assertEquals(1, quotes.size)
    assertEquals(SymbolId.parse("AAPL.MX"), quotes[0].symbol)

    val request = mockWebServer.takeRequest()
    assertTrue(request.path!!.contains("symbol=AAPL.MX"))
  }

  @Test
  fun `should throw exception for missing configuration`() {
    val invalidProps =
      ProviderProperties().apply {
        finnhub.baseUrl = ""
        finnhub.apiKey = ""
      }

    val webClient = WebClient.builder().build()
    val invalidProvider = FinnhubProvider(invalidProps, webClient)

    val exception =
      assertThrows<IllegalArgumentException> {
        invalidProvider.fetchQuotes(listOf(SymbolId.parse("AAPL")))
      }

    assertTrue(exception.message!!.contains("Finnhub base-url/api-key not configured"))
  }

  @Test
  fun `should throw UnsupportedOperationException for historical data`() {
    val exception =
      assertThrows<UnsupportedOperationException> {
        provider.fetchHistorical(
          SymbolId.parse("AAPL"),
          Instant.now(),
          Instant.now(),
          com.notivest.pricefetcher.models.Timeframe.T1D,
        )
      }

    assertTrue(exception.message!!.contains("Use PolygonProvider for historical"))
  }

  @Test
  fun `should return correct provider name`() {
    assertEquals("FINNHUB", provider.getName())
  }

  @Test
  fun `should handle empty timestamp gracefully`() {
    val mockResponse =
      MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody("""{"c": 150.0}""") // No timestamp field

    mockWebServer.enqueue(mockResponse)

    val symbols = listOf(SymbolId.parse("AAPL"))
    val quotes = provider.fetchQuotes(symbols)

    assertEquals(1, quotes.size)
    val quote = quotes[0]
    // Should use current time when timestamp is missing
    assertTrue(quote.ts.isAfter(Instant.now().minusSeconds(5)))
  }

  @Test
  fun `should handle server error gracefully`() {
    val mockResponse =
      MockResponse()
        .setResponseCode(500)
        .setBody("Internal Server Error")

    mockWebServer.enqueue(mockResponse)

    val symbols = listOf(SymbolId.parse("AAPL"))

    // Should return empty list since the provider now handles errors gracefully
    val quotes = provider.fetchQuotes(symbols)
    assertEquals(0, quotes.size)
  }
}
