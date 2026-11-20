package com.casasegura.automacao

import android.Manifest
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private const val PRECISAO_DESEJADA_METROS = 15f
private const val TEMPO_BUSCA_MS = 25_000L
private const val IDADE_MAXIMA_NS = 15_000_000_000L

@RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
internal suspend fun buscarMelhorGps(gerenciador: LocationManager): Location? {
    var melhor: Location? = null
    withTimeoutOrNull(TEMPO_BUSCA_MS) {
        callbackFlow {
            val ouvinte = object : LocationListener {
                override fun onLocationChanged(local: Location) {
                    trySend(local)
                }

                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) = Unit
                @Deprecated("Callback mantido para compatibilidade com versões antigas do Android.")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            }
            gerenciador.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, ouvinte, Looper.getMainLooper())
            awaitClose { gerenciador.removeUpdates(ouvinte) }
        }.first { local ->
            val idade = SystemClock.elapsedRealtimeNanos() - local.elapsedRealtimeNanos
            if (idade !in 0..IDADE_MAXIMA_NS || !local.hasAccuracy() || local.accuracy <= 0f) {
                false
            } else {
                if (local.accuracy < (melhor?.accuracy ?: Float.POSITIVE_INFINITY)) melhor = local
                local.accuracy <= PRECISAO_DESEJADA_METROS
            }
        }
    }
    return melhor
}