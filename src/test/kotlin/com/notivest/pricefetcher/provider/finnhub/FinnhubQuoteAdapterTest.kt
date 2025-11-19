package com.notivest.pricefetcher.provider.finnhub

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

class FinnhubQuoteAdapterTest {
  private lateinit var mockWebServer: MockWebServer
  private lateinit var adapter: FinnhubQuoteAdapter
  private lateinit var properties: ProviderProperties

  @BeforeEach
  fun setUp() {
    mockWebServer = MockWebServer()
    mockWebServer.start()
    properties =
      ProviderProperties().apply {
        finnhub.baseUrl = mockWebServer.url("/").toString().removeSuffix("/")
        finnhub.apiKey = "test-token"
      }
    adapter = FinnhubQuoteAdapter(properties, WebClient.builder().build())
  }

  @AfterEach
  fun tearDown() {
    mockWebServer.shutdown()
  }

  @Test
  fun `maps quote response into domain model`() {
    val response =
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
    mockWebServer.enqueue(response)

    val quotes = adapter.fetchQuotes(listOf(SymbolId.parse("AAPL")))

    assertEquals(1, quotes.size)
    val quote = quotes.first()
    assertEquals(SymbolId.parse("AAPL"), quote.symbol)
    assertEquals(BigDecimal("172.35"), quote.last)
    assertEquals(BigDecimal("171.0"), quote.open)
    assertEquals(BigDecimal("173.0"), quote.high)
    assertEquals(BigDecimal("170.5"), quote.low)
    assertEquals(BigDecimal("170.9"), quote.prevClose)
    assertEquals("FINNHUB", quote.source)
    assertEquals(Instant.ofEpochSecond(1713369600), quote.ts)
  }

  @Test
  fun `skips quotes when provider rate limits`() {
    mockWebServer.enqueue(
      MockResponse()
        .setResponseCode(429)
        .setBody("Rate limit exceeded"),
    )

    val quotes = adapter.fetchQuotes(listOf(SymbolId.parse("AAPL")))

    assertTrue(quotes.isEmpty())
  }

  @Test
  fun `requires provider configuration`() {
    val misconfigured =
      ProviderProperties().apply {
        finnhub.baseUrl = ""
        finnhub.apiKey = ""
      }

    val invalidAdapter = FinnhubQuoteAdapter(misconfigured, WebClient.builder().build())

    val exception =
      assertThrows<IllegalArgumentException> {
        invalidAdapter.fetchQuotes(listOf(SymbolId.parse("AAPL")))
      }

    assertTrue(exception.message!!.contains("Finnhub base-url/api-key not configured"))
  }
}
