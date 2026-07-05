package com.notivest.pricefetcher.observability

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class PriceFetcherMetrics(
  private val registry: MeterRegistry,
) {
  fun recordSchedulerTick(
    outcome: String,
    phase: String,
    trackedSymbols: Int,
    staleSymbols: Int,
    duration: Duration,
  ) {
    Timer.builder("pricefetcher.scheduler.tick.duration")
      .tag("outcome", outcome)
      .tag("phase", phase)
      .register(registry)
      .record(duration)

    Counter.builder("pricefetcher.scheduler.ticks")
      .tag("outcome", outcome)
      .tag("phase", phase)
      .register(registry)
      .increment()

    if (trackedSymbols > 0) {
      Counter.builder("pricefetcher.scheduler.symbols.tracked")
        .tag("phase", phase)
        .register(registry)
        .increment(trackedSymbols.toDouble())
    }

    if (staleSymbols > 0) {
      Counter.builder("pricefetcher.scheduler.symbols.stale")
        .tag("phase", phase)
        .register(registry)
        .increment(staleSymbols.toDouble())
    }
  }

  fun recordBatchResult(outcome: String, phase: String, requestedSymbols: Int, refreshedQuotes: Int) {
    Counter.builder("pricefetcher.scheduler.batches")
      .tag("outcome", outcome)
      .tag("phase", phase)
      .register(registry)
      .increment()

    if (requestedSymbols > 0) {
      Counter.builder("pricefetcher.scheduler.batch.symbols.requested")
        .tag("outcome", outcome)
        .tag("phase", phase)
        .register(registry)
        .increment(requestedSymbols.toDouble())
    }

    if (refreshedQuotes > 0) {
      Counter.builder("pricefetcher.scheduler.batch.quotes.refreshed")
        .tag("phase", phase)
        .register(registry)
        .increment(refreshedQuotes.toDouble())
    }
  }
}
