package com.notivest.pricefetcher.client

import com.notivest.pricefetcher.client.polygon.dto.PolygonMarketStatusResponse
import com.notivest.pricefetcher.client.polygon.dto.PolygonTickerDetailsResponse
import com.notivest.pricefetcher.config.ProviderProperties
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration

@Component
class PolygonMarketClient(
  private val props: ProviderProperties,
  private val web: WebClient,
) {
  private val logger = LoggerFactory.getLogger(PolygonMarketClient::class.java)

  fun marketStatus(): PolygonMarketStatusResponse {
    val base = props.polygon.baseUrl.removeSuffix("/")
    val token = props.polygon.apiKey
    require(base.isNotBlank() && token.isNotBlank()) { "Polygon base-url/api-key not configured" }

    val uri = "$base/v1/marketstatus/now?apiKey=$token"

    return web.get().uri(uri)
      .retrieve()
      .onStatus({ it == HttpStatus.TOO_MANY_REQUESTS }) { resp ->
        resp.bodyToMono<String>().flatMap {
          logger.warn("Polygon market status rate limited: {}", it)
          Mono.error(RuntimeException("Polygon rate limited"))
        }
      }
      .onStatus({ it.is4xxClientError }) { resp ->
        resp.bodyToMono<String>().flatMap {
          logger.warn("Polygon market status client error: {}", it)
          Mono.error(RuntimeException("Polygon client error: $it"))
        }
      }
      .onStatus({ it.is5xxServerError }) { resp ->
        resp.bodyToMono<String>().flatMap {
          logger.warn("Polygon market status server error: {}", it)
          Mono.error(RuntimeException("Polygon server error: $it"))
        }
      }
      .bodyToMono<PolygonMarketStatusResponse>()
      .timeout(Duration.ofSeconds(10))
      .onErrorResume { e ->
        logger.warn("Failed to fetch Polygon market status: {}", e.message)
        Mono.empty()
      }
      .block() ?: PolygonMarketStatusResponse(status = "error")
  }

  fun tickerDetails(rawSymbol: String): PolygonTickerDetailsResponse {
    val base = props.polygon.baseUrl.removeSuffix("/")
    val token = props.polygon.apiKey
    require(base.isNotBlank() && token.isNotBlank()) { "Polygon base-url/api-key not configured" }

    val encoded = URLEncoder.encode(rawSymbol, StandardCharsets.UTF_8)
    val uri = "$base/v3/reference/tickers/$encoded?apiKey=$token"

    return web.get().uri(uri)
      .retrieve()
      .onStatus({ it == HttpStatus.TOO_MANY_REQUESTS }) { resp ->
        resp.bodyToMono<String>().flatMap {
          logger.warn("Polygon ticker details rate limited for {}: {}", rawSymbol, it)
          Mono.error(RuntimeException("Polygon rate limited for $rawSymbol"))
        }
      }
      .onStatus({ it.is4xxClientError }) { resp ->
        resp.bodyToMono<String>().flatMap {
          logger.warn("Polygon ticker details client error for {}: {}", rawSymbol, it)
          Mono.error(RuntimeException("Polygon client error for $rawSymbol: $it"))
        }
      }
      .onStatus({ it.is5xxServerError }) { resp ->
        resp.bodyToMono<String>().flatMap {
          logger.warn("Polygon ticker details server error for {}: {}", rawSymbol, it)
          Mono.error(RuntimeException("Polygon server error for $rawSymbol: $it"))
        }
      }
      .bodyToMono<PolygonTickerDetailsResponse>()
      .timeout(Duration.ofSeconds(10))
      .onErrorResume { e ->
        logger.warn("Failed to fetch Polygon ticker {}: {}", rawSymbol, e.message)
        Mono.empty()
      }
      .block() ?: PolygonTickerDetailsResponse(status = "error")
  }
}
