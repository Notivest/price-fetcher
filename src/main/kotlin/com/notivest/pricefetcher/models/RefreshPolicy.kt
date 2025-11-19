package com.notivest.pricefetcher.models

import org.springframework.stereotype.Component

@Component
class RefreshPolicy {
  fun batchSize(phase: MarketClockPhase): Int =
    when (phase) {
      MarketClockPhase.REGULAR -> 60
      MarketClockPhase.PRE,
      MarketClockPhase.AFTER,
      -> 40
      MarketClockPhase.NIGHT -> 10
    }
}
