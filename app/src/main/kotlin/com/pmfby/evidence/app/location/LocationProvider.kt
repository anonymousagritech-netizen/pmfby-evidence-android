package com.pmfby.evidence.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class GeoTag(val lat: Double, val lon: Double, val accuracyM: Float, val capturedAtMillis: Long)

/**
 * Real FusedLocationProviderClient usage for the "GPS geotag on visit/call events"
 * requirement. Returns null (not a fake/zeroed coordinate) whenever permission isn't
 * granted or no fix is obtained — callers must treat a null geotag as "not available",
 * never silently store (0,0).
 */
class LocationProvider(private val context: Context) {

    private fun hasFineOrCoarseLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // permission checked explicitly above before this call
    suspend fun captureCurrentLocation(): GeoTag? {
        if (!hasFineOrCoarseLocationPermission()) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setDurationMillis(10_000)
            .build()

        return suspendCancellableCoroutine { continuation ->
            client.getCurrentLocation(request, null)
                .addOnSuccessListener { location ->
                    val result = location?.let {
                        GeoTag(lat = it.latitude, lon = it.longitude, accuracyM = it.accuracy, capturedAtMillis = System.currentTimeMillis())
                    }
                    if (continuation.isActive) continuation.resume(result)
                }
                .addOnFailureListener {
                    if (continuation.isActive) continuation.resume(null)
                }
        }
    }
}
