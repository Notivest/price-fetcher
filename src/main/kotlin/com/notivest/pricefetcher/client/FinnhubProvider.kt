package com.notivest.pricefetcher.client

import com.notivest.pricefetcher.config.ProviderProperties
import com.notivest.pricefetcher.models.CandleSeries
import com.notivest.pricefetcher.models.Quote
import com.notivest.pricefetcher.models.SymbolId
import com.notivest.pricefetcher.models.Timeframe
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.Instant

@Component
class FinnhubProvider(
  private val props: ProviderProperties,
  private val web: WebClient,
) : MarketDataProvider {
  data class FinnhubQuote(
    val c: Double?,
    val o: Double?,
    val h: Double?,
    val l: Double?,
    val pc: Double?,
    val t: Long?,
  )

  override fun fetchQuotes(symbols: List<SymbolId>): List<Quote> {
    val base = props.finnhub.baseUrl.removeSuffix("/")
    val token = props.finnhub.apiKey
    require(base.isNotBlank() && token.isNotBlank()) { "Finnhub base-url/api-key not configured" }

    return symbols.map { sid ->
      val sym = sid.toString()
      val uri = "$base/quote?symbol=$sym&token=$token"
      val q =
        web.get().uri(uri).retrieve()
          .onStatus({
            it == HttpStatus.TOO_MANY_REQUESTS
          }) { resp -> resp.bodyToMono<String>().flatMap { Mono.error(RuntimeException("Finnhub rate-limited")) } }
          .bodyToMono<FinnhubQuote>()
          .block() ?: FinnhubQuote(null, null, null, null, null, null)

      val now = q.t?.let { Instant.ofEpochSecond(it) } ?: Instant.now()
      Quote(
        symbol = sid,
        last = q.c?.let { BigDecimal.valueOf(it) } ?: BigDecimal.valueOf(Double.NaN),
        open = q.o?.let { BigDecimal.valueOf(it) },
        high = q.h?.let { BigDecimal.valueOf(it) },
        low = q.l?.let { BigDecimal.valueOf(it) },
        prevClose = q.pc?.let { BigDecimal.valueOf(it) },
        currency = "USD",
        source = getName(),
        ts = now,
      )
    }
  }

  override fun fetchHistorical(
    symbol: SymbolId,
    from: Instant,
    to: Instant,
    timeframe: Timeframe,
  ): CandleSeries {
    // Para MVP, delegamos históricos a PolygonProvider. Si quisieras usar Finnhub:
    // endpoint típico: /stock/candle?symbol=S&resolution=...&from=...&to=...&token=...
    throw UnsupportedOperationException("Use PolygonProvider for historical")
  }

  override fun getName(): String {
    return "FINNHUB"
  }
}
