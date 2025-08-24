package com.notivest.pricefetcher.service

import com.notivest.pricefetcher.models.SymbolId
import com.notivest.pricefetcher.models.WatchListItem
import com.notivest.pricefetcher.repositories.interfaces.WatchListRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals

class WatchListServiceTest {
  private lateinit var repository: WatchListRepository
  private lateinit var service: WatchListService

  @BeforeEach
  fun setUp() {
    repository = mock()
    service = WatchListService(repository)
  }

  @Test
  fun `should add valid item`() {
    val item = WatchListItem("AAPL", enabled = true)
    whenever(repository.add(item)).thenReturn(true)

    service.add(item)

    verify(repository).add(item)
  }

  @Test
  fun `should throw exception when adding blank symbol`() {
    val item = WatchListItem("", enabled = true)

    val exception =
      assertThrows<IllegalArgumentException> {
        service.add(item)
      }
    assertEquals("symbol is required", exception.message)
  }

  @Test
  fun `should throw exception when adding duplicate symbol`() {
    val item = WatchListItem("AAPL", enabled = true)
    whenever(repository.add(item)).thenReturn(false)

    val exception =
      assertThrows<IllegalArgumentException> {
        service.add(item)
      }
    assertEquals("symbol already exists", exception.message)
  }

  @Test
  fun `should patch existing symbol`() {
    whenever(repository.update("AAPL", false, 5)).thenReturn(true)

    service.patch("AAPL", enabled = false, priority = 5)

    verify(repository).update("AAPL", false, 5)
  }

  @Test
  fun `should throw exception when patching non-existent symbol`() {
    whenever(repository.update("NONEXISTENT", false, null)).thenReturn(false)

    val exception =
      assertThrows<IllegalArgumentException> {
        service.patch("NONEXISTENT", enabled = false, priority = null)
      }
    assertEquals("symbol not found", exception.message)
  }

  @Test
  fun `should delete existing symbol`() {
    whenever(repository.remove("AAPL")).thenReturn(true)

    service.delete("AAPL")

    verify(repository).remove("AAPL")
  }

  @Test
  fun `should throw exception when deleting non-existent symbol`() {
    whenever(repository.remove("NONEXISTENT")).thenReturn(false)

    val exception =
      assertThrows<IllegalArgumentException> {
        service.delete("NONEXISTENT")
      }
    assertEquals("symbol not found", exception.message)
  }

  @Test
  fun `should return enabled symbols as SymbolId list`() {
    val items =
      listOf(
        WatchListItem("AAPL", enabled = true),
        WatchListItem("TSLA", enabled = false),
        WatchListItem("MSFT.MX", enabled = true),
      )
    whenever(repository.all()).thenReturn(items)

    val result = service.enabledSymbols()

    assertEquals(2, result.size)
    assertEquals(SymbolId.parse("AAPL"), result[0])
    assertEquals(SymbolId.parse("MSFT.MX"), result[1])
  }

  @Test
  fun `should list all items`() {
    val items = listOf(WatchListItem("AAPL"), WatchListItem("TSLA"))
    whenever(repository.all()).thenReturn(items)

    val result = service.list()

    assertEquals(items, result)
    verify(repository).all()
  }
}
