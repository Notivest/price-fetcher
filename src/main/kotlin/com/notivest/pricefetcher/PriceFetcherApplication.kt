package com.notivest.pricefetcher

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class PriceFetcherApplication

fun main(args: Array<String>) {
  runApplication<PriceFetcherApplication>(*args)
}
