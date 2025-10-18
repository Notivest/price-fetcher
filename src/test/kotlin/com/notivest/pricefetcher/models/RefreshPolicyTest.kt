package com.notivest.pricefetcher.models

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RefreshPolicyTest {
  private lateinit var policy: RefreshPolicy

  @BeforeEach
  fun setUp() {
    policy = RefreshPolicy()
  }

  @Test
  fun `should return correct batch size for REGULAR phase`() {
    val batchSize = policy.batchSize(MarketClockPhase.REGULAR)
    assertEquals(60, batchSize)
  }

  @Test
  fun `should return correct batch size for PRE phase`() {
    val batchSize = policy.batchSize(MarketClockPhase.PRE)
    assertEquals(40, batchSize)
  }

  @Test
  fun `should return correct batch size for AFTER phase`() {
    val batchSize = policy.batchSize(MarketClockPhase.AFTER)
    assertEquals(40, batchSize)
  }

  @Test
  fun `should return correct batch size for NIGHT phase`() {
    val batchSize = policy.batchSize(MarketClockPhase.NIGHT)
    assertEquals(10, batchSize)
  }

  @Test
  fun `should have consistent batch sizes for PRE and AFTER phases`() {
    val preBatchSize = policy.batchSize(MarketClockPhase.PRE)
    val afterBatchSize = policy.batchSize(MarketClockPhase.AFTER)
    assertEquals(preBatchSize, afterBatchSize)
  }

  @Test
  fun `should have REGULAR phase with highest batch size`() {
    val regularBatch = policy.batchSize(MarketClockPhase.REGULAR)
    val preBatch = policy.batchSize(MarketClockPhase.PRE)
    val afterBatch = policy.batchSize(MarketClockPhase.AFTER)
    val nightBatch = policy.batchSize(MarketClockPhase.NIGHT)

    assert(regularBatch >= preBatch)
    assert(regularBatch >= afterBatch)
    assert(regularBatch >= nightBatch)
  }

  @Test
  fun `should have NIGHT phase with lowest batch size`() {
    val regularBatch = policy.batchSize(MarketClockPhase.REGULAR)
    val preBatch = policy.batchSize(MarketClockPhase.PRE)
    val afterBatch = policy.batchSize(MarketClockPhase.AFTER)
    val nightBatch = policy.batchSize(MarketClockPhase.NIGHT)

    assert(nightBatch <= regularBatch)
    assert(nightBatch <= preBatch)
    assert(nightBatch <= afterBatch)
  }
}
