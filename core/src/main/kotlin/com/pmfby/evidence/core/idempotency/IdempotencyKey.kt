package com.pmfby.evidence.core.idempotency

import java.util.UUID

/**
 * Idempotency-key generation for the offline upload/event queue (call-events,
 * message-events, emergency-incidents, evidence all accept an `idempotencyKey` per
 * each backend-and-web/backend/src/routes .js route file, so a retried offline-queue upload after a
 * flaky connection doesn't create a duplicate server-side row).
 *
 * Deterministic variant (same inputs -> same key) lets a WorkManager retry regenerate an
 * identical key without needing to persist the original — useful as a belt-and-suspenders
 * check, though the primary path stores a random key alongside the queued event at
 * enqueue time via [randomKey].
 */
object IdempotencyKey {

    /** A random, collision-safe key for a freshly-queued offline event. */
    fun randomKey(): String = UUID.randomUUID().toString()

    /**
     * A deterministic key derived from the event's own identifying fields, for cases
     * where regenerating the exact same key on retry (without reading back a stored
     * value) is useful. NOT cryptographically secure — just stable hashing, which is all
     * idempotency de-duping needs.
     */
    fun deterministicKey(vararg parts: String): String {
        val joined = parts.joinToString("|")
        val hash = joined.hashCode().toUInt().toString(16).padStart(8, '0')
        return "det-$hash-${parts.size}"
    }
}

/**
 * Client-side mirror of the backend's filename sanitizer
 * (backend-and-web/backend/src/utils/filename.js) — used when naming locally-cached
 * evidence files before upload, so a farmer name/village containing `/`, `\`, `:` etc.
 * (which happens with real Excel-imported data) can never produce a path-traversal or
 * invalid filesystem path on-device.
 */
object FilenameSafety {
    private val UNSAFE_CHARS = Regex("[/\\\\:*?\"<>|]")
    private val WHITESPACE = Regex("\\s+")
    private val REPEATED_UNDERSCORE = Regex("_+")

    fun sanitizePart(part: String?): String {
        if (part.isNullOrEmpty()) return "Unknown"
        val cleaned = part
            .replace(UNSAFE_CHARS, "_")
            .replace(WHITESPACE, "_")
            .replace(REPEATED_UNDERSCORE, "_")
            .trim('_')
            .take(80)
        return cleaned.ifEmpty { "Unknown" }
    }

    /** Builds a local cache filename, same scheme as the backend's ZIP naming. */
    fun buildEvidenceFilename(farmerName: String?, kind: String, extension: String): String {
        val safeName = sanitizePart(farmerName)
        val safeKind = sanitizePart(kind)
        val ext = if (extension.startsWith(".")) extension else ".$extension"
        return "${safeName}_${safeKind}_${System.currentTimeMillis()}$ext"
    }

    /** Collision-free rename, same algorithm as the backend's dedupeFilename(). */
    fun dedupeFilename(desiredName: String, existingNames: Set<String>): String {
        if (desiredName !in existingNames) return desiredName
        val dotIndex = desiredName.lastIndexOf('.')
        val base = if (dotIndex == -1) desiredName else desiredName.substring(0, dotIndex)
        val ext = if (dotIndex == -1) "" else desiredName.substring(dotIndex)
        var i = 1
        var candidate = "${base}_$i$ext"
        while (candidate in existingNames) {
            i += 1
            candidate = "${base}_$i$ext"
        }
        return candidate
    }
}
