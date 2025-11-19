package com.notivest.pricefetcher.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "pricefetcher.providers")
class ProviderProperties {
  var primary: String = "FINNHUB"
  var quotesPrimary: String? = null
  var historicalPrimary: String? = "POLYGON"
  var finnhub: FinnhubProviderSettings = FinnhubProviderSettings()
  var polygon: PolygonProviderSettings = PolygonProviderSettings()
}
