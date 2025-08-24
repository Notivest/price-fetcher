package com.notivest.pricefetcher.client

import com.notivest.pricefetcher.config.ProviderProperties
import org.springframework.stereotype.Component

@Component
class ProviderFactory(
  private val props: ProviderProperties,
  private val finnhub: FinnhubProvider,
  private val polygon: PolygonProvider,
) {
  fun primary(): MarketDataProvider =
    when (props.primary.uppercase()) {
      "FINNHUB" -> finnhub
      "POLYGON" -> polygon
      else -> finnhub
    }

  fun quotesProvider(): MarketDataProvider = finnhub

  fun historicalProvider(): MarketDataProvider = polygon
}
