package com.notivest.price_fetcher.repositories.`interface`

import com.notivest.price_fetcher.models.Quote
import com.notivest.price_fetcher.models.SymbolId

interface QuoteRepository {
    fun put(quote : Quote)
    fun get(symbol: SymbolId) : Pair<Quote? , Boolean>
    fun count() : Int
    fun symbols() : Set<SymbolId>
}