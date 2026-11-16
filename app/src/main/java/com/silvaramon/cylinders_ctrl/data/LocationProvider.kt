package com.silvaramon.cylinders_ctrl.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume


/**
 * Obtém a localização atual (GPS/rede) e faz geocodificação reversa para endereço.
 * Usa apenas APIs da plataforma (sem Google Play Services).
 */
class LocationProvider(context: Context) {

    private val appContext = context.applicationContext
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Posição atual; `null` se não houver permissão ou se não for possível obtê-la. */
    suspend fun capturePosition(timeoutMillis: Long = LOCATION_TIMEOUT_MS): Location? {
        if (!hasPermission()) return null
        return withTimeoutOrNull(timeoutMillis) { currentLocation() } ?: lastKnownLocation()
    }

    private fun bestProvider(): String? {
        val enabled = locationManager.getProviders(true)
        return when {
            LocationManager.GPS_PROVIDER in enabled -> LocationManager.GPS_PROVIDER
            LocationManager.NETWORK_PROVIDER in enabled -> LocationManager.NETWORK_PROVIDER
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                LocationManager.FUSED_PROVIDER in enabled -> LocationManager.FUSED_PROVIDER
            else -> enabled.firstOrNull { it != LocationManager.PASSIVE_PROVIDER }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(): Location? {
        val provider = bestProvider() ?: return null
        return suspendCancellableCoroutine { cont ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                locationManager.getCurrentLocation(
                    provider,
                    signal,
                    ContextCompat.getMainExecutor(appContext),
                ) { location -> if (cont.isActive) cont.resume(location) }
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        locationManager.removeUpdates(this)
                        if (cont.isActive) cont.resume(location)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) = Unit
                }
                cont.invokeOnCancellation { locationManager.removeUpdates(listener) }
                locationManager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnownLocation(): Location? =
        locationManager.getProviders(true)
            .mapNotNull { runCatching { locationManager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }

    /**
     * Geocodificação reversa (coordenadas → endereço). Normalmente exige internet;
     * retorna `null` quando não for possível calcular.
     */
    suspend fun reverseGeocode(lat: Double, lng: Double): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(appContext, Locale.getDefault())
        val address = withTimeoutOrNull(GEOCODE_TIMEOUT_MS) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<android.location.Address>) {
                            if (cont.isActive) cont.resume(addresses.firstOrNull())
                        }

                        override fun onError(errorMessage: String?) {
                            if (cont.isActive) cont.resume(null)
                        }
                    })
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    runCatching { geocoder.getFromLocation(lat, lng, 1)?.firstOrNull() }.getOrNull()
                }
            }
        } ?: return null
        return address.getAddressLine(0)
            ?: listOfNotNull(
                address.thoroughfare,
                address.subThoroughfare,
                address.subLocality,
                address.locality,
                address.adminArea,
            ).joinToString(", ").ifBlank { null }
    }

    private companion object {
        const val LOCATION_TIMEOUT_MS = 15_000L
        const val GEOCODE_TIMEOUT_MS = 10_000L
    }
}
