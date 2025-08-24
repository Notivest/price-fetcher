package com.notivest.pricefetcher.models

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.LocalTime
import java.time.ZoneId

@Component
class MarketClock(
  @Value("\${pricefetcher.market.timezone:America/New_York}") private val tz: String,
  @Value("\${pricefetcher.market.schedule.premarket:06:00-09:30}") private val pre: String,
  @Value("\${pricefetcher.market.schedule.regular:09:30-16:00}") private val reg: String,
  @Value("\${pricefetcher.market.schedule.after:16:00-20:00}") private val aft: String,
) {
  private val zone = ZoneId.of(tz)

  enum class Phase { PRE, REGULAR, AFTER, NIGHT }

  fun phase(): Phase {
    val t = LocalTime.now(zone)
    val inRange = { range: String ->
      val (a, b) = range.split("-").map(LocalTime::parse)
      !t.isBefore(a) && !t.isAfter(b)
    }
    return when {
      inRange(reg) -> Phase.REGULAR
      inRange(pre) -> Phase.PRE
      inRange(aft) -> Phase.AFTER
      else -> Phase.NIGHT
    }
  }
}
