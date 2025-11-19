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

    if (base.isBlank() || token.isBlank()) {
      logger.error("Polygon configuration missing: base-url is blank={}, api-key is blank={}", base.isBlank(), token.isBlank())
      throw IllegalArgumentException("Polygon base-url/api-key not configured")
    }

    logger.info(
      "Starting Polygon historical data fetch: symbol={}, from={}, to={}, timeframe={}",
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

    logger.debug("Polygon API request URI (without key): {}/v2/aggs/ticker/{}/range/{}/{}/{}", base, rawSymbol, timeframeValue, fromStr, toStr)

    return try {
      logger.debug("Executing HTTP GET request to Polygon API for symbol: {}", rawSymbol)

      val payload =
        webClient.get().uri(uri)
          .retrieve()
          .onStatus({ it == HttpStatus.TOO_MANY_REQUESTS }) { response ->
            response.bodyToMono<String>().flatMap { body ->
              logger.warn("Polygon rate limited (429) for symbol: {} - Body: {}", rawSymbol, body)
              Mono.error(RuntimeException("Polygon rate-limited for $rawSymbol"))
            }
          }
          .onStatus({ it.is4xxClientError }) { response ->
            response.bodyToMono<String>().flatMap { body ->
              logger.error("Polygon client error for symbol {}: {}", rawSymbol, body)
              Mono.error(RuntimeException("Client error for $rawSymbol: $body"))
            }
          }
          .onStatus({ it.is5xxServerError }) { response ->
            response.bodyToMono<String>().flatMap { body ->
              logger.error("Polygon server error for symbol {}: {}", rawSymbol, body)
              Mono.error(RuntimeException("Server error for $rawSymbol: $body"))
            }
          }
          .bodyToMono<PolygonAggPayload>()
          .timeout(Duration.ofSeconds(30))
          .block()
          ?: PolygonAggPayload()

      logger.info("Polygon API response received for symbol: {}, results count: {}", rawSymbol, payload.results.size)

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

      logger.info("Successfully fetched and transformed {} candles for symbol: {} (timeframe: {})", items.size, symbol, timeframe)
      CandleSeries(symbol = symbol, timeframe = timeframe, items = items)
    } catch (ex: Exception) {
      logger.error("Failed to fetch historical data for symbol {}: {} - Error type: {}", symbol, ex.message, ex::class.simpleName, ex)
      throw ex
    }
  }

  override fun getName(): String = POLYGON_NAME

  companion object {
    const val POLYGON_NAME = "POLYGON"
  }
}
