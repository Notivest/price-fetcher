package com.notivest.pricefetcher.client

import com.notivest.pricefetcher.config.ProviderProperties
import com.notivest.pricefetcher.models.*
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

@Component
class PolygonProvider(
  private val props: ProviderProperties,
  private val web: WebClient,
) : MarketDataProvider {
  private val logger = LoggerFactory.getLogger(PolygonProvider::class.java)

  data class AggsResp(val results: List<Agg> = emptyList())

  data class Agg(val t: Long, val o: Double, val h: Double, val l: Double, val c: Double, val v: Long)

  override fun fetchQuotes(symbols: List<SymbolId>): List<Quote> {
    // Polygon también puede dar last trade/nbbo, pero para MVP dejamos quotes en Finnhub
    throw UnsupportedOperationException("Use FinnhubProvider for quotes")
  }

  override fun fetchHistorical(
    symbol: SymbolId,
    from: Instant,
    to: Instant,
    timeframe: Timeframe,
  ): CandleSeries {
    val base = props.polygon.baseUrl.removeSuffix("/")
    val token = props.polygon.apiKey
    require(base.isNotBlank() && token.isNotBlank()) { "Polygon base-url/api-key not configured" }

    logger.debug(
      "Fetching historical data for {} from {} to {} with timeframe {}",
      symbol,
      from,
      to,
      timeframe,
    )

    val tf =
      when (timeframe) {
        Timeframe.T1D -> "1/day"
        Timeframe.T1H -> "1/hour"
        Timeframe.T15M -> "15/minute"
        Timeframe.T5M -> "5/minute"
        Timeframe.T1M -> "1/minute"
      }

    val sym = symbol.toString()
    val fromStr = from.toString().substring(0, 10) // YYYY-MM-DD format
    val toStr = to.toString().substring(0, 10)

    val uri = "$base/v2/aggs/ticker/$sym/range/$tf/$fromStr/$toStr?adjusted=true&limit=50000&apiKey=$token"

    return try {
      val body =
        web.get().uri(uri)
          .retrieve()
          .onStatus({ it == HttpStatus.TOO_MANY_REQUESTS }) { resp ->
            resp.bodyToMono<String>().flatMap {
              logger.warn("Polygon rate limited for symbol: {}", sym)
              Mono.error(RuntimeException("Polygon rate-limited for $sym"))
            }
          }
          .onStatus({ it.is4xxClientError }) { resp ->
            resp.bodyToMono<String>().flatMap {
              logger.warn("Client error for symbol {}: {}", sym, it)
              Mono.error(RuntimeException("Client error for $sym: $it"))
            }
          }
          .onStatus({ it.is5xxServerError }) { resp ->
            resp.bodyToMono<String>().flatMap {
              logger.warn("Server error for symbol {}: {}", sym, it)
              Mono.error(RuntimeException("Server error for $sym: $it"))
            }
          }
          .bodyToMono<AggsResp>()
          .timeout(Duration.ofSeconds(30))
          .block() ?: AggsResp()

      val items =
        body.results.sortedBy { it.t }.map { a ->
          Candle(
            ts = Instant.ofEpochMilli(a.t),
            o = BigDecimal.valueOf(a.o),
            h = BigDecimal.valueOf(a.h),
            l = BigDecimal.valueOf(a.l),
            c = BigDecimal.valueOf(a.c),
            v = a.v,
            adjusted = true,
          )
        }

      logger.debug("Successfully fetched {} candles for {}", items.size, symbol)
      CandleSeries(symbol = symbol, timeframe = timeframe, items = items)
    } catch (e: Exception) {
      logger.error("Failed to fetch historical data for symbol {}: {}", symbol, e.message)
      throw e
    }
  }

  override fun getName(): String {
    return "POLYGON"
  }
}
