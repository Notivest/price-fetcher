package com.notivest.pricefetcher.repositories.interfaces

import com.notivest.pricefetcher.models.Quote
import com.notivest.pricefetcher.models.SymbolId

interface QuoteRepository {
  fun put(quote: Quote)

  fun get(symbol: SymbolId): Pair<Quote?, Boolean>

  fun count(): Int

  fun symbols(): Set<SymbolId>
}
