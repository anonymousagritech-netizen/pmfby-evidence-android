package com.pmfby.evidence.core.http

/**
 * Pure classification of an HTTP response (or its absence) into what the offline upload
 * queue should do next. Mirrors the deprecated React Native prototype's
 * `httpHelpers.classifyUploadOutcome` (see
 * ../../../../_deprecated_react-native-mobile/src/lib/httpHelpers.js /
 * httpHelpers.test.js) one-to-one, reimplemented here with zero Android/Retrofit
 * dependency so it is unit-testable on the plain JVM — see UploadOutcomeClassifierTest.
 *
 * Used by app.work.UploadWorker to decide, from a caught retrofit2.HttpException's status
 * code, whether a queued row should be retried again later or marked permanently failed:
 * 401/403/404/409 (auth revoked, reassignment, not-found, or a conflicting duplicate) are
 * never worth retrying automatically, while a 5xx or no response at all (network error)
 * is exactly the transient case WorkManager's periodic retry exists for.
 */
enum class UploadOutcome { SUCCESS, TERMINAL_FAILURE, RETRY }

object UploadOutcomeClassifier {

    private val TERMINAL_STATUS_CODES = setOf(401, 403, 404, 409)

    /** [statusCode] is null for "no response at all" (a network/IO error, not an HTTP one). */
    fun classify(statusCode: Int?): UploadOutcome = when {
        statusCode == null -> UploadOutcome.RETRY
        statusCode in 200..299 -> UploadOutcome.SUCCESS
        statusCode in TERMINAL_STATUS_CODES -> UploadOutcome.TERMINAL_FAILURE
        else -> UploadOutcome.RETRY
    }

    /**
     * Keeps only the first occurrence of each idempotency key, in order — the same
     * dedup the deprecated prototype applied to a locally-held queue before replaying it,
     * ported here as a defensive, generic helper (Room's own primary-key REPLACE already
     * prevents duplicate rows from persisting, but a caller merging queues from more than
     * one source should not have to re-derive this itself).
     */
    fun <T> dedupeByIdempotencyKey(items: List<T>, keyOf: (T) -> String): List<T> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<T>()
        for (item in items) {
            val key = keyOf(item)
            if (seen.add(key)) result.add(item)
        }
        return result
    }
}
