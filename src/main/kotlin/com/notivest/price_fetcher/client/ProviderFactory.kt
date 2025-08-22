package com.notivest.price_fetcher.client

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class ProviderFactory(
    private val fakeProvider: FakeProvider,
    @Value("\${pricefetcher.providers.primary:FAKE}") private val primary: String
) {
    fun primary(): MarketDataProvider =
        when (primary.uppercase()) {
            "FAKE" -> fakeProvider
            else -> fakeProvider
        }
}