package com.pmfby.evidence.core.http

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UploadOutcomeClassifierTest {

    @Test
    fun `2xx statuses are success`() {
        assertEquals(UploadOutcome.SUCCESS, UploadOutcomeClassifier.classify(200))
        assertEquals(UploadOutcome.SUCCESS, UploadOutcomeClassifier.classify(201))
        assertEquals(UploadOutcome.SUCCESS, UploadOutcomeClassifier.classify(202))
        assertEquals(UploadOutcome.SUCCESS, UploadOutcomeClassifier.classify(299))
    }

    @Test
    fun `401 403 404 409 are terminal failures — never retried forever`() {
        assertEquals(UploadOutcome.TERMINAL_FAILURE, UploadOutcomeClassifier.classify(401))
        assertEquals(UploadOutcome.TERMINAL_FAILURE, UploadOutcomeClassifier.classify(403))
        assertEquals(UploadOutcome.TERMINAL_FAILURE, UploadOutcomeClassifier.classify(404))
        assertEquals(UploadOutcome.TERMINAL_FAILURE, UploadOutcomeClassifier.classify(409))
    }

    @Test
    fun `5xx and no response (network error) are retryable`() {
        assertEquals(UploadOutcome.RETRY, UploadOutcomeClassifier.classify(500))
        assertEquals(UploadOutcome.RETRY, UploadOutcomeClassifier.classify(503))
        assertEquals(UploadOutcome.RETRY, UploadOutcomeClassifier.classify(null))
    }

    @Test
    fun `an unrecognized 4xx not in the terminal set defaults to retry`() {
        assertEquals(UploadOutcome.RETRY, UploadOutcomeClassifier.classify(429))
    }

    @Test
    fun `dedupeByIdempotencyKey keeps only the first occurrence of each key`() {
        data class Item(val key: String, val payload: Int)
        val queue = listOf(Item("a", 1), Item("b", 2), Item("a", 3))
        val result = UploadOutcomeClassifier.dedupeByIdempotencyKey(queue) { it.key }
        assertEquals(listOf(Item("a", 1), Item("b", 2)), result)
    }

    @Test
    fun `dedupeByIdempotencyKey preserves original order`() {
        data class Item(val key: String)
        val queue = listOf(Item("c"), Item("a"), Item("b"), Item("a"))
        val result = UploadOutcomeClassifier.dedupeByIdempotencyKey(queue) { it.key }
        assertEquals(listOf("c", "a", "b"), result.map { it.key })
    }
}
