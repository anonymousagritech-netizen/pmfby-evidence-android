package com.pmfby.evidence.app.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

/**
 * Multipart file uploads (evidence photos, voice notes, emergency audio/video) go through
 * plain OkHttp rather than Retrofit's @Multipart (keeping ApiService's Map-body style
 * uniform for the JSON-only endpoints) — matches backend/src/routes/evidence.js and
 * callRecordings.js, both of which read the file off a multer `file` field plus ordinary
 * form fields (caseId, kind, consentAcknowledged, etc).
 */
class FileUploadApi(
    private val okHttpClient: okhttp3.OkHttpClient,
    private val baseUrl: String,
    moshi: Moshi,
) {
    private val mapAdapter = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java),
    )

    /** Uploads a case-evidence file (screenshot/voice_note/emergency_audio/emergency_video/other). */
    fun uploadEvidence(authHeader: String, file: File, mimeType: String, fields: Map<String, String>): Map<String, Any?> {
        val bodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
        fields.forEach { (key, value) -> bodyBuilder.addFormDataPart(key, value) }
        bodyBuilder.addFormDataPart("file", file.name, file.asRequestBody(mimeType.toMediaType()))

        val request = Request.Builder()
            .url("${baseUrl}evidence")
            .header("Authorization", authHeader)
            .post(bodyBuilder.build())
            .build()

        return executeAndParse(request)
    }

    /** Uploads a manually-initiated voice note / voice-message-playback log. */
    fun uploadCallRecording(authHeader: String, file: File?, mimeType: String, fields: Map<String, String>): Map<String, Any?> {
        val bodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
        fields.forEach { (key, value) -> bodyBuilder.addFormDataPart(key, value) }
        if (file != null) {
            bodyBuilder.addFormDataPart("file", file.name, file.asRequestBody(mimeType.toMediaType()))
        }

        val request = Request.Builder()
            .url("${baseUrl}call-recordings")
            .header("Authorization", authHeader)
            .post(bodyBuilder.build())
            .build()

        return executeAndParse(request)
    }

    @Suppress("UNCHECKED_CAST")
    private fun executeAndParse(request: Request): Map<String, Any?> {
        okHttpClient.newCall(request).execute().use { response ->
            val bodyString = response.body?.string() ?: "{}"
            if (!response.isSuccessful) {
                throw java.io.IOException("Upload failed: HTTP ${response.code} — $bodyString")
            }
            return mapAdapter.fromJson(bodyString) ?: emptyMap()
        }
    }
}
