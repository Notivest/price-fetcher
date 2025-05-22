package com.notivest.price_fetcher

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PriceFetcherApplication

fun main(args: Array<String>) {
	runApplication<PriceFetcherApplication>(*args)
}
