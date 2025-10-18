package com.notivest.pricefetcher.service.strategy

import com.notivest.pricefetcher.config.ProviderProperties
import com.notivest.pricefetcher.models.Quote
import com.notivest.pricefetcher.models.SymbolId
import com.notivest.pricefetcher.provider.adapter.QuoteProviderAdapter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ConfigurableQuoteFetchingStrategy(
  private val providerProperties: ProviderProperties,
  private val adapters: List<QuoteProviderAdapter>,
) : QuoteFetchingStrategy {
  private val logger = LoggerFactory.getLogger(ConfigurableQuoteFetchingStrategy::class.java)

  override fun fetch(symbols: List<SymbolId>): List<Quote> {
    val adapter = resolveAdapter(providerProperties.quotesPrimary ?: providerProperties.primary)
    logger.debug("Using {} quote adapter", adapter.getName())
    return adapter.fetchQuotes(symbols)
  }

  private fun resolveAdapter(targetName: String?): QuoteProviderAdapter {
    require(adapters.isNotEmpty()) { "No quote provider adapters configured" }

    val normalizedName = targetName?.uppercase()?.trim()
    return adapters.firstOrNull { it.getName().equals(normalizedName, ignoreCase = true) }
      ?: adapters.first()
  }
}
