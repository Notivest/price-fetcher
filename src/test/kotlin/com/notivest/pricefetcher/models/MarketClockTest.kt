package com.notivest.pricefetcher.models

import com.notivest.pricefetcher.client.PolygonMarketClient
import com.notivest.pricefetcher.client.polygon.dto.PolygonMarketStatusResponse
import com.notivest.pricefetcher.client.polygon.dto.PolygonTickerDetails
import com.notivest.pricefetcher.client.polygon.dto.PolygonTickerDetailsResponse
import com.notivest.pricefetcher.client.polygon.dto.PolygonTimezoneDescriptor
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarketClockTest {
  private val polygonClient: PolygonMarketClient = mock()

  @Test
  fun `stocks use exchange status`() {
    whenever(polygonClient.marketStatus()).thenReturn(
      PolygonMarketStatusResponse(
        market = "closed",
        exchanges = mapOf("nyse" to "open"),
        currencies = mapOf("crypto" to "open", "fx" to "open"),
      ),
    )

    whenever(polygonClient.tickerDetails("AAPL")).thenReturn(
      PolygonTickerDetailsResponse(
        results =
          PolygonTickerDetails(
            ticker = "AAPL",
            market = "stocks",
            primaryExchange = "XNYS",
            timezone = PolygonTimezoneDescriptor(name = "America/New_York"),
          ),
      ),
    )

    val clock = MarketClock(polygonClient)
    val snapshot = clock.snapshot(listOf(SymbolId(ticker = "AAPL")))

    assertEquals(MarketClockPhase.REGULAR, snapshot.overallPhase)
    assertEquals(MarketClockPhase.REGULAR, snapshot.phasesBySymbol[SymbolId(ticker = "AAPL")])
    assertEquals("America/New_York", snapshot.timezoneBySymbol[SymbolId(ticker = "AAPL")])
  }

  @Test
  fun `crypto stays open even if equities closed`() {
    whenever(polygonClient.marketStatus()).thenReturn(
      PolygonMarketStatusResponse(
        market = "closed",
        exchanges = mapOf("nyse" to "closed", "nasdaq" to "closed"),
        currencies = mapOf("crypto" to "open"),
      ),
    )

    whenever(polygonClient.tickerDetails("X:BTCUSD")).thenReturn(
      PolygonTickerDetailsResponse(
        results =
          PolygonTickerDetails(
            ticker = "X:BTCUSD",
            market = "crypto",
            timezone = PolygonTimezoneDescriptor(name = "Etc/UTC"),
          ),
      ),
    )

    val clock = MarketClock(polygonClient)
    val symbol = SymbolId(ticker = "X:BTCUSD")
    val snapshot = clock.snapshot(listOf(symbol))

    assertEquals(MarketClockPhase.REGULAR, snapshot.overallPhase)
    assertEquals(MarketClockPhase.REGULAR, snapshot.phasesBySymbol[symbol])
    assertEquals("Etc/UTC", snapshot.timezoneBySymbol[symbol])
  }

  @Test
  fun `stock closes when exchange closed`() {
    whenever(polygonClient.marketStatus()).thenReturn(
      PolygonMarketStatusResponse(
        market = "closed",
        exchanges = mapOf("nyse" to "closed"),
        currencies = emptyMap(),
      ),
    )

    whenever(polygonClient.tickerDetails("TSLA")).thenReturn(
      PolygonTickerDetailsResponse(
        results =
          PolygonTickerDetails(
            ticker = "TSLA",
            market = "stocks",
            primaryExchange = "XNYS",
          ),
      ),
    )

    val clock = MarketClock(polygonClient)
    val symbol = SymbolId(ticker = "TSLA")
    val snapshot = clock.snapshot(listOf(symbol))

    assertEquals(MarketClockPhase.NIGHT, snapshot.overallPhase)
    assertEquals(MarketClockPhase.NIGHT, snapshot.phasesBySymbol[symbol])
    assertTrue(snapshot.timezoneBySymbol[symbol]!!.contains("New_York"))
  }
}
