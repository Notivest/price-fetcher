package com.notivest.pricefetcher.models

import org.springframework.stereotype.Component

@Component
class RefreshPolicy {
  fun batchSize(phase: MarketClock.Phase): Int =
    when (phase) {
      MarketClock.Phase.REGULAR -> 60
      MarketClock.Phase.PRE,
      MarketClock.Phase.AFTER,
      -> 40
      MarketClock.Phase.NIGHT -> 10
    }
}
