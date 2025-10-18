package com.notivest.pricefetcher.provider.polygon

import com.notivest.pricefetcher.config.ProviderProperties
import com.notivest.pricefetcher.models.Candle
import com.notivest.pricefetcher.models.CandleSeries
import com.notivest.pricefetcher.models.SymbolId
import com.notivest.pricefetcher.models.Timeframe
import com.notivest.pricefetcher.provider.adapter.HistoricalProviderAdapter
import com.notivest.pricefetcher.provider.polygon.model.PolygonAggPayload
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
class PolygonHistoricalAdapter(
  private val props: ProviderProperties,
  private val webClient: WebClient,
) : HistoricalProviderAdapter {
  private val logger = LoggerFactory.getLogger(PolygonHistoricalAdapter::class.java)

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

    val timeframeValue =
      when (timeframe) {
        Timeframe.T1D -> "1/day"
        Timeframe.T1H -> "1/hour"
        Timeframe.T15M -> "15/minute"
        Timeframe.T5M -> "5/minute"
        Timeframe.T1M -> "1/minute"
      }

    val rawSymbol = symbol.toString()
    val fromStr = from.toString().substring(0, 10)
    val toStr = to.toString().substring(0, 10)
    val uri =
      "$base/v2/aggs/ticker/$rawSymbol/range/$timeframeValue/$fromStr/$toStr?adjusted=true&limit=50000&apiKey=$token"

    return try {
      val payload =
        webClient.get().uri(uri)
          .retrieve()
          .onStatus({ it == HttpStatus.TOO_MANY_REQUESTS }) { response ->
            response.bodyToMono<String>().flatMap {
              logger.warn("Polygon rate limited for symbol: {}", rawSymbol)
              Mono.error(RuntimeException("Polygon rate-limited for $rawSymbol"))
            }
          }
          .onStatus({ it.is4xxClientError }) { response ->
            response.bodyToMono<String>().flatMap {
              logger.warn("Client error for symbol {}: {}", rawSymbol, it)
              Mono.error(RuntimeException("Client error for $rawSymbol: $it"))
            }
          }
          .onStatus({ it.is5xxServerError }) { response ->
            response.bodyToMono<String>().flatMap {
              logger.warn("Server error for symbol {}: {}", rawSymbol, it)
              Mono.error(RuntimeException("Server error for $rawSymbol: $it"))
            }
          }
          .bodyToMono<PolygonAggPayload>()
          .timeout(Duration.ofSeconds(30))
          .block()
          ?: PolygonAggPayload()

      val items =
        payload.results.sortedBy { it.t }.map { result ->
          Candle(
            ts = Instant.ofEpochMilli(result.t),
            o = BigDecimal.valueOf(result.o),
            h = BigDecimal.valueOf(result.h),
            l = BigDecimal.valueOf(result.l),
            c = BigDecimal.valueOf(result.c),
            v = result.v,
            adjusted = true,
          )
        }

      logger.debug("Successfully fetched {} candles for {}", items.size, symbol)
      CandleSeries(symbol = symbol, timeframe = timeframe, items = items)
    } catch (ex: Exception) {
      logger.error("Failed to fetch historical data for symbol {}: {}", symbol, ex.message)
      throw ex
    }
  }

  override fun getName(): String = POLYGON_NAME

  companion object {
    const val POLYGON_NAME = "POLYGON"
  }
}
