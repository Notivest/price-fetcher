package com.notivest.pricefetcher.service.strategy

import com.notivest.pricefetcher.config.ProviderProperties
import com.notivest.pricefetcher.models.CandleSeries
import com.notivest.pricefetcher.models.SymbolId
import com.notivest.pricefetcher.models.Timeframe
import com.notivest.pricefetcher.provider.adapter.HistoricalProviderAdapter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class ConfigurableHistoricalFetchingStrategy(
  private val providerProperties: ProviderProperties,
  private val adapters: List<HistoricalProviderAdapter>,
) : HistoricalFetchingStrategy {
  private val logger = LoggerFactory.getLogger(ConfigurableHistoricalFetchingStrategy::class.java)

  override fun fetch(
    symbol: SymbolId,
    from: Instant,
    to: Instant,
    timeframe: Timeframe,
  ): CandleSeries {
    val adapter = resolveAdapter(providerProperties.historicalPrimary ?: providerProperties.primary)
    logger.debug("Using {} historical adapter", adapter.getName())
    return adapter.fetchHistorical(symbol, from, to, timeframe)
  }

  private fun resolveAdapter(targetName: String?): HistoricalProviderAdapter {
    require(adapters.isNotEmpty()) { "No historical provider adapters configured" }

    val normalizedName = targetName?.uppercase()?.trim()
    return adapters.firstOrNull { it.getName().equals(normalizedName, ignoreCase = true) }
      ?: adapters.first()
  }
}
