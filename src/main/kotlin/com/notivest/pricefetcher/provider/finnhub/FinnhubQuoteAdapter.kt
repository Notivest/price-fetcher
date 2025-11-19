package com.notivest.pricefetcher.provider.finnhub

import com.notivest.pricefetcher.config.ProviderProperties
import com.notivest.pricefetcher.models.Quote
import com.notivest.pricefetcher.models.SymbolId
import com.notivest.pricefetcher.provider.adapter.QuoteProviderAdapter
import com.notivest.pricefetcher.provider.finnhub.model.FinnhubQuotePayload
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
class FinnhubQuoteAdapter(
  private val props: ProviderProperties,
  private val webClient: WebClient,
) : QuoteProviderAdapter {
  private val logger = LoggerFactory.getLogger(FinnhubQuoteAdapter::class.java)

  override fun fetchQuotes(symbols: List<SymbolId>): List<Quote> {
    val base = props.finnhub.baseUrl.removeSuffix("/")
    val token = props.finnhub.apiKey
    require(base.isNotBlank() && token.isNotBlank()) { "Finnhub base-url/api-key not configured" }

    logger.debug("Fetching quotes for {} symbols from Finnhub", symbols.size)

    return symbols.mapNotNull { symbolId ->
      try {
        val symbol = symbolId.toString()
        val uri = "$base/quote?symbol=$symbol&token=$token"

        val payload =
          webClient.get().uri(uri)
            .retrieve()
            .onStatus({ it == HttpStatus.TOO_MANY_REQUESTS }) { response ->
              response.bodyToMono<String>().flatMap {
                logger.warn("Finnhub rate limited for symbol: {}", symbol)
                Mono.error(RuntimeException("Finnhub rate-limited for $symbol"))
              }
            }
            .onStatus({ it.is4xxClientError }) { response ->
              response.bodyToMono<String>().flatMap {
                logger.warn("Client error for symbol {}: {}", symbol, it)
                Mono.error(RuntimeException("Client error for $symbol: $it"))
              }
            }
            .onStatus({ it.is5xxServerError }) { response ->
              response.bodyToMono<String>().flatMap {
                logger.warn("Server error for symbol {}: {}", symbol, it)
                Mono.error(RuntimeException("Server error for $symbol: $it"))
              }
            }
            .bodyToMono<FinnhubQuotePayload>()
            .timeout(Duration.ofSeconds(10))
            .block()
            ?: FinnhubQuotePayload()

        val timestamp = payload.t?.let { Instant.ofEpochSecond(it) } ?: Instant.now()
        Quote(
          symbol = symbolId,
          last = payload.c?.let(BigDecimal::valueOf) ?: BigDecimal.ZERO,
          open = payload.o?.let(BigDecimal::valueOf),
          high = payload.h?.let(BigDecimal::valueOf),
          low = payload.l?.let(BigDecimal::valueOf),
          prevClose = payload.pc?.let(BigDecimal::valueOf),
          currency = "USD",
          source = getName(),
          ts = timestamp,
        )
      } catch (ex: Exception) {
        logger.error("Failed to fetch quote for symbol {}: {}", symbolId, ex.message)
        null
      }
    }
  }

  override fun getName(): String = FINNHUB_NAME

  companion object {
    const val FINNHUB_NAME = "FINNHUB"
  }
}
