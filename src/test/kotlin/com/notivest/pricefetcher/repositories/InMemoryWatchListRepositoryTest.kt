package com.notivest.pricefetcher.repositories

import com.notivest.pricefetcher.models.WatchListItem
import com.notivest.pricefetcher.repositories.impl.InMemoryWatchListRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InMemoryWatchListRepositoryTest {
  private lateinit var repository: InMemoryWatchListRepository

  @BeforeEach
  fun setUp() {
    repository = InMemoryWatchListRepository()
  }

  @Test
  fun `should add item successfully`() {
    val item = WatchListItem("AAPL", enabled = true, priority = 1)
    val result = repository.add(item)

    assertTrue(result)
    assertTrue(repository.contains("AAPL"))
    assertEquals(1, repository.all().size)
  }

  @Test
  fun `should normalize symbols to uppercase`() {
    val item = WatchListItem("aapl", enabled = true)
    repository.add(item)

    assertTrue(repository.contains("AAPL"))
    assertTrue(repository.contains("aapl"))
    assertEquals("AAPL", repository.all().first().symbol)
  }

  @Test
  fun `should not add duplicate symbols`() {
    val item1 = WatchListItem("AAPL", enabled = true)
    val item2 = WatchListItem("aapl", enabled = false) // different case, same symbol

    assertTrue(repository.add(item1))
    assertFalse(repository.add(item2)) // Should fail due to duplicate
    assertEquals(1, repository.all().size)
  }

  @Test
  fun `should update existing item`() {
    val item = WatchListItem("AAPL", enabled = true, priority = 1)
    repository.add(item)

    val result = repository.update("AAPL", enabled = false, priority = 5)

    assertTrue(result)
    val updated = repository.all().first()
    assertFalse(updated.enabled)
    assertEquals(5, updated.priority)
  }

  @Test
  fun `should not update non-existent item`() {
    val result = repository.update("NONEXISTENT", enabled = false, priority = null)
    assertFalse(result)
  }

  @Test
  fun `should remove existing item`() {
    val item = WatchListItem("AAPL", enabled = true)
    repository.add(item)

    val result = repository.remove("AAPL")

    assertTrue(result)
    assertFalse(repository.contains("AAPL"))
    assertEquals(0, repository.all().size)
  }

  @Test
  fun `should not remove non-existent item`() {
    val result = repository.remove("NONEXISTENT")
    assertFalse(result)
  }

  @Test
  fun `should sort by priority then by symbol`() {
    repository.add(WatchListItem("TSLA", enabled = true, priority = 2))
    repository.add(WatchListItem("AAPL", enabled = true, priority = 1))
    repository.add(WatchListItem("MSFT", enabled = true, priority = null)) // null priority = lowest
    repository.add(WatchListItem("GOOGL", enabled = true, priority = 1))

    val sorted = repository.all()

    assertEquals("AAPL", sorted[0].symbol) // priority 1, alphabetically first
    assertEquals("GOOGL", sorted[1].symbol) // priority 1, alphabetically second
    assertEquals("TSLA", sorted[2].symbol) // priority 2
    assertEquals("MSFT", sorted[3].symbol) // null priority (lowest)
  }

  @Test
  fun `should handle partial updates`() {
    val item = WatchListItem("AAPL", enabled = true, priority = 1)
    repository.add(item)

    // Update only enabled, leave priority unchanged
    repository.update("AAPL", enabled = false, priority = null)
    val updated = repository.all().first()

    assertFalse(updated.enabled)
    assertEquals(1, updated.priority) // Should remain unchanged
  }
}
