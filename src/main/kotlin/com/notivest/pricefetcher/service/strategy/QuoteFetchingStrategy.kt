package com.notivest.pricefetcher.service.strategy

import com.notivest.pricefetcher.models.Quote
import com.notivest.pricefetcher.models.SymbolId

interface QuoteFetchingStrategy {
  fun fetch(symbols: List<SymbolId>): List<Quote>
}
