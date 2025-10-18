package com.notivest.pricefetcher.provider.adapter

import com.notivest.pricefetcher.models.Quote
import com.notivest.pricefetcher.models.SymbolId

/**
 * Adapter responsible for translating quotes from an external provider into the domain model.
 */
interface QuoteProviderAdapter {
  fun fetchQuotes(symbols: List<SymbolId>): List<Quote>

  fun getName(): String
}
