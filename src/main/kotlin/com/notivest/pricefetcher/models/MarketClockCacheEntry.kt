package com.notivest.pricefetcher.models

import java.time.Instant

data class MarketClockCacheEntry<T>(
  val value: T,
  val fetchedAt: Instant,
)
