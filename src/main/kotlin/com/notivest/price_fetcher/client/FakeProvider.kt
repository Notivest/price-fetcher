package com.notivest.price_fetcher.client

import com.notivest.price_fetcher.models.Candle
import com.notivest.price_fetcher.models.CandleSeries
import com.notivest.price_fetcher.models.Quote
import com.notivest.price_fetcher.models.SymbolId
import com.notivest.price_fetcher.models.Timeframe
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Instant
import kotlin.random.Random

@Component
class FakeProvider : MarketDataProvider {
    val name: String = "FAKE"
    
    override fun fetchQuotes(symbols: List<SymbolId>): List<Quote> {
        return symbols.map {
            val price = BigDecimal.valueOf(100 + Random.nextDouble(-1.0, 1.5))
            Quote(symbol = it, last = price, open = price, high = price, low = price, prevClose = price)
        }
    }

    override fun fetchHistorical(
        symbol: SymbolId,
        from: Instant,
        to: Instant,
        timeframe: Timeframe
    ): CandleSeries {
        val items = (1..60).map { i ->
            val p = BigDecimal.valueOf(100 + i * 0.1 + Random.nextDouble(-0.5, 0.5))
            Candle(ts = Instant.now().minusSeconds((60 - i) * 3600L), o = p, h = p, l = p, c = p, v = 1000L)
        }
        return CandleSeries(symbol, timeframe, items)
    }

    override fun getName(): String = name

}