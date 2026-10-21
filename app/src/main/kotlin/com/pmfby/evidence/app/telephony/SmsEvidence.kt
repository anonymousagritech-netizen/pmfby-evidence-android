package com.pmfby.evidence.app.telephony

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat

/** OS-level proof that an SMS really left the phone, and automatic pickup of the screenshot of it. */
object SmsEvidence {

    private const val TAG = "SmsEvidence"

    /** Rows in the system SMS store: type 2 = sent, 5 = failed, 4 = outbox, 6 = queued. */
    data class SentRecord(val box: Int, val dateMillis: Long)

    fun findInProvider(context: Context, phone: String, body: String, sinceMillis: Long): SentRecord? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return null
        val tail = phone.filter { it.isDigit() }.takeLast(10)
        val probe = body.trim().take(40)
        return try {
            context.contentResolver.query(
                Uri.parse("content://sms"),
                arrayOf("address", "body", "type", "date"),
                "date >= ? AND type IN (2,4,5,6)",
                arrayOf((sinceMillis - 5_000).toString()),
                "date DESC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val addr = c.getString(0)?.filter { it.isDigit() }.orEmpty()
                    val text = c.getString(1)?.trim().orEmpty()
                    if (addr.endsWith(tail) && text.startsWith(probe)) {
                        return SentRecord(c.getInt(2), c.getLong(3))
                    }
                }
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "SMS provider query failed: ${e.message}")
            null
        }
    }

    /** Newest screenshot taken after [sinceMillis] (needs READ_MEDIA_IMAGES). */
    fun newestScreenshotSince(context: Context, sinceMillis: Long): Uri? = try {
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Images.Media.DATE_ADDED} >= ?",
            arrayOf("%Screenshots%", (sinceMillis / 1000).toString()),
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { c ->
            if (c.moveToFirst()) ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0)) else null
        }
    } catch (e: Exception) {
        Log.w(TAG, "Screenshot query failed: ${e.message}")
        null
    }
}
