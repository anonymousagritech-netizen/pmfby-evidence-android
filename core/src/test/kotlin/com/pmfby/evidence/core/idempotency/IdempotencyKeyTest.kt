package com.pmfby.evidence.core.idempotency

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IdempotencyKeyTest {

    @Test
    fun `randomKey produces distinct values each call`() {
        val a = IdempotencyKey.randomKey()
        val b = IdempotencyKey.randomKey()
        assertNotEquals(a, b)
        assertTrue(a.isNotBlank())
    }

    @Test
    fun `deterministicKey is stable for the same inputs`() {
        val a = IdempotencyKey.deterministicKey("case-1", "surveyor-9", "call")
        val b = IdempotencyKey.deterministicKey("case-1", "surveyor-9", "call")
        assertEquals(a, b)
    }

    @Test
    fun `deterministicKey differs when inputs differ`() {
        val a = IdempotencyKey.deterministicKey("case-1", "surveyor-9", "call")
        val b = IdempotencyKey.deterministicKey("case-2", "surveyor-9", "call")
        assertNotEquals(a, b)
    }
}

class FilenameSafetyTest {

    @Test
    fun `sanitizePart strips unsafe filesystem characters`() {
        assertEquals("Ramesh_Kumar", FilenameSafety.sanitizePart("Ramesh/Kumar"))
        assertEquals("A_B_C", FilenameSafety.sanitizePart("A:B*C"))
    }

    @Test
    fun `sanitizePart collapses whitespace and repeated underscores and trims edges`() {
        assertEquals("Ram_Lal", FilenameSafety.sanitizePart("  Ram   Lal  "))
        assertEquals("A_B", FilenameSafety.sanitizePart("A___B"))
    }

    @Test
    fun `sanitizePart falls back to Unknown for null or blank input`() {
        assertEquals("Unknown", FilenameSafety.sanitizePart(null))
        assertEquals("Unknown", FilenameSafety.sanitizePart(""))
        assertEquals("Unknown", FilenameSafety.sanitizePart("///"))
    }

    @Test
    fun `sanitizePart truncates to 80 characters`() {
        val long = "A".repeat(200)
        assertEquals(80, FilenameSafety.sanitizePart(long).length)
    }

    @Test
    fun `buildEvidenceFilename produces a sanitized, extension-correct filename`() {
        val name = FilenameSafety.buildEvidenceFilename("Ramesh/Kumar", "voice_note", "m4a")
        assertTrue(name.startsWith("Ramesh_Kumar_voice_note_"))
        assertTrue(name.endsWith(".m4a"))
    }

    @Test
    fun `dedupeFilename returns the original name when there is no collision`() {
        assertEquals("evidence.jpg", FilenameSafety.dedupeFilename("evidence.jpg", emptySet()))
    }

    @Test
    fun `dedupeFilename appends an incrementing suffix on collision`() {
        val existing = setOf("evidence.jpg", "evidence_1.jpg")
        assertEquals("evidence_2.jpg", FilenameSafety.dedupeFilename("evidence.jpg", existing))
    }

    @Test
    fun `dedupeFilename handles filenames with no extension`() {
        val existing = setOf("evidence")
        assertEquals("evidence_1", FilenameSafety.dedupeFilename("evidence", existing))
    }
}
