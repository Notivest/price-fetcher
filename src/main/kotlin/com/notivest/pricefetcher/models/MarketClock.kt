package com.notivest.pricefetcher.models

import com.notivest.pricefetcher.client.PolygonMarketClient
import com.notivest.pricefetcher.client.polygon.dto.PolygonMarketStatusResponse
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Component
class MarketClock(
  private val polygonMarketClient: PolygonMarketClient,
) {
  private val tickerCache =
    ConcurrentHashMap<String, MarketClockCacheEntry<MarketTickerMetadata>>()

  @Volatile
  private var statusCache: MarketClockCacheEntry<PolygonMarketStatusResponse>? = null

  private val statusTtl = Duration.ofSeconds(45)
  private val tickerTtl = Duration.ofHours(12)

  fun phase(): MarketClockPhase {
    val status = ensureMarketStatus()
    return statusToPhase(status.market)
  }

  fun snapshot(symbols: List<SymbolId>): MarketClockSnapshot {
    val status = ensureMarketStatus()
    val now = Instant.now()

    val phases =
      symbols.associateWith { symbol ->
        val meta = tickerMeta(symbol, now)
        phaseFor(meta, symbol, status)
      }

    val timezones = symbols.associateWith { symbol -> tickerMeta(symbol, now).timezone }

    val overall =
      phases.values.fold(MarketClockPhase.NIGHT) { acc, phase ->
        if (phase.orderValue() > acc.orderValue()) phase else acc
      }

    return MarketClockSnapshot(
      fetchedAt = now,
      overallPhase = overall,
      phasesBySymbol = phases,
      timezoneBySymbol = timezones,
    )
  }

  private fun phaseFor(
    meta: MarketTickerMetadata,
    symbol: SymbolId,
    status: PolygonMarketStatusResponse,
  ): MarketClockPhase =
    when (meta.marketKind) {
      MarketKind.CRYPTO -> {
        val crypto = status.currencies["crypto"]
        if (crypto.isNullOrBlank()) MarketClockPhase.REGULAR else statusToPhase(crypto)
      }

      MarketKind.FX -> {
        val fx = status.currencies["fx"]
        if (fx.isNullOrBlank()) MarketClockPhase.REGULAR else statusToPhase(fx)
      }

      MarketKind.STOCKS -> {
        val key = meta.exchangeKey
        val exchangePhase = key?.let { status.exchanges[it.lowercase()] ?: status.exchanges[it] }
        val derived = statusToPhase(exchangePhase)
        if (derived != MarketClockPhase.NIGHT) {
          derived
        } else {
          statusToPhase(status.market)
        }
      }

      MarketKind.UNKNOWN -> {
        val overall = statusToPhase(status.market)
        if (overall == MarketClockPhase.NIGHT && looksLikeCrypto(symbol)) {
          MarketClockPhase.REGULAR
        } else {
          overall
        }
      }
    }

  private fun statusToPhase(status: String?): MarketClockPhase =
    when (status?.lowercase()) {
      "open" -> MarketClockPhase.REGULAR
      "extended-hours", "after-hours", "post-market" -> MarketClockPhase.AFTER
      "pre-market", "premarket" -> MarketClockPhase.PRE
      "closed", "halted" -> MarketClockPhase.NIGHT
      else -> MarketClockPhase.NIGHT
    }

  private fun ensureMarketStatus(now: Instant = Instant.now()): PolygonMarketStatusResponse {
    val cached = statusCache
    if (cached != null && Duration.between(cached.fetchedAt, now) <= statusTtl) {
      return cached.value
    }

    val fresh = polygonMarketClient.marketStatus()
    statusCache = MarketClockCacheEntry(fresh, now)
    return fresh
  }

  private fun tickerMeta(
    symbol: SymbolId,
    now: Instant,
  ): MarketTickerMetadata {
    val key = symbol.toString()
    val cached = tickerCache[key]
    if (cached != null && Duration.between(cached.fetchedAt, now) <= tickerTtl) {
      return cached.value
    }

    val meta = fetchTickerMeta(symbol)
    tickerCache[key] = MarketClockCacheEntry(meta, now)
    return meta
  }

  private fun fetchTickerMeta(symbol: SymbolId): MarketTickerMetadata {
    val rawSymbol = symbol.toString()
    val details = polygonMarketClient.tickerDetails(rawSymbol).results

    val marketKind =
      when (details?.market?.lowercase()) {
        "stocks" -> MarketKind.STOCKS
        "crypto" -> MarketKind.CRYPTO
        "fx" -> MarketKind.FX
        "otc" -> MarketKind.STOCKS
        else -> inferMarketKindFromSymbol(symbol)
      }

    val timezone = details?.timezone?.name
    val exchangeKey = details?.primaryExchange?.let { mapMicToExchangeKey(it) }

    return MarketTickerMetadata(
      marketKind = marketKind,
      exchangeKey = exchangeKey,
      timezone = timezone ?: defaultTimezoneFor(marketKind),
    )
  }

  private fun mapMicToExchangeKey(mic: String): String? =
    when (mic.uppercase()) {
      "XNYS", "XASE", "ARCX", "ARCA" -> "nyse"
      "XNAS", "XNCM", "XNMS", "XBOS", "EDGX", "IEXG", "BATS", "XPHL" -> "nasdaq"
      "OTCM", "PINX", "XOTC" -> "otc"
      else -> null
    }

  private fun inferMarketKindFromSymbol(symbol: SymbolId): MarketKind {
    val str = symbol.toString()
    return when {
      looksLikeCrypto(symbol) -> MarketKind.CRYPTO
      str.contains(":FX") -> MarketKind.FX
      else -> MarketKind.UNKNOWN
    }
  }

  private fun defaultTimezoneFor(kind: MarketKind): String? =
    when (kind) {
      MarketKind.STOCKS -> "America/New_York"
      MarketKind.CRYPTO -> "Etc/UTC"
      MarketKind.FX -> "Etc/UTC"
      MarketKind.UNKNOWN -> null
    }

  private fun MarketClockPhase.orderValue(): Int =
    when (this) {
      MarketClockPhase.NIGHT -> 0
      MarketClockPhase.PRE -> 1
      MarketClockPhase.AFTER -> 2
      MarketClockPhase.REGULAR -> 3
    }

  private fun looksLikeCrypto(symbol: SymbolId): Boolean {
    val raw = symbol.toString()
    val exchange = symbol.exchange?.uppercase() ?: ""
    return raw.startsWith("X:") ||
      exchange.contains("CRYPTO") ||
      exchange.contains("BINANCE") ||
      exchange.contains("COIN") ||
      raw.contains("USD", ignoreCase = true) && raw.length > 6 && !raw.contains(".")
  }
}
