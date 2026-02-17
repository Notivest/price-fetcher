package com.notivest.pricefetcher.controllers

import com.notivest.pricefetcher.dto.PatchBody
import com.notivest.pricefetcher.models.WatchListItem
import com.notivest.pricefetcher.service.WatchListService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class WatchListControllerTest {
    private lateinit var service: WatchListService
    private lateinit var controller: WatchListController

    @BeforeEach
    fun setUp() {
        service = mock()
        controller = WatchListController(service)
    }

    @Test
    fun `list returns watchlist items`() {
        val items = listOf(WatchListItem("AAPL"), WatchListItem("MSFT", enabled = false))
        whenever(service.list()).thenReturn(items)

        val response = controller.list()

        assertThat(response.statusCode.value()).isEqualTo(200)
        assertThat(response.body).isEqualTo(items)
    }

    @Test
    fun `add returns ok on success`() {
        val body = WatchListItem(symbol = "AAPL")

        val response = controller.add(body)

        assertThat(response.statusCode.value()).isEqualTo(200)
        verify(service).add(body)
    }

    @Test
    fun `add returns bad request on validation error`() {
        val body = WatchListItem(symbol = "AAPL")
        doThrow(IllegalArgumentException("invalid symbol")).whenever(service).add(any())

        val response = controller.add(body)

        assertThat(response.statusCode.value()).isEqualTo(400)
        assertThat(response.body).isEqualTo(mapOf("error" to "invalid symbol"))
    }

    @Test
    fun `patch returns no content on success`() {
        val response = controller.patch("AAPL", PatchBody(enabled = true, priority = 1))

        assertThat(response.statusCode.value()).isEqualTo(204)
        verify(service).patch("AAPL", true, 1)
    }

    @Test
    fun `patch returns bad request on validation error`() {
        doThrow(IllegalArgumentException("priority out of range")).whenever(service).patch(any(), any(), any())

        val response = controller.patch("AAPL", PatchBody(enabled = true, priority = 999))

        assertThat(response.statusCode.value()).isEqualTo(400)
        assertThat(response.body).isEqualTo(mapOf("error" to "priority out of range"))
    }

    @Test
    fun `delete returns no content on success`() {
        val response = controller.delete("AAPL")

        assertThat(response.statusCode.value()).isEqualTo(204)
        verify(service).delete("AAPL")
    }

    @Test
    fun `delete returns bad request on validation error`() {
        doThrow(IllegalArgumentException("symbol is required")).whenever(service).delete(any())

        val response = controller.delete(" ")

        assertThat(response.statusCode.value()).isEqualTo(400)
        assertThat(response.body).isEqualTo(mapOf("error" to "symbol is required"))
    }
}
