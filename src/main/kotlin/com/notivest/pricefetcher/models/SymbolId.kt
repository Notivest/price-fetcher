package com.notivest.pricefetcher.models

data class SymbolId(val exchange: String? = null, val ticker: String) {
  override fun toString(): String {
    return if (exchange.isNullOrBlank()) ticker else "$ticker.$exchange"
  }

  companion object {
    fun parse(raw: String): SymbolId {
      val parts = raw.split(".")
      return when (parts.size) {
        1 -> SymbolId(ticker = parts[0])
        else -> SymbolId(exchange = parts.last(), ticker = parts.dropLast(1).joinToString("."))
      }
    }
  }
}
