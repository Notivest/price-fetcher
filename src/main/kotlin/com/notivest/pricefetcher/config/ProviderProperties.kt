package com.notivest.pricefetcher.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "pricefetcher.providers")
class ProviderProperties {
  var primary: String = "FINNHUB"
  val finnhub = Finnhub()
  val polygon = Polygon()

  class Finnhub {
    var baseUrl: String = ""
    var apiKey: String = ""
  }

  class Polygon {
    var baseUrl: String = ""
    var apiKey: String = ""
  }
}
