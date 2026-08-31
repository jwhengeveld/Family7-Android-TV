package nl.family7.tv.data

import android.os.SystemClock

/**
 * Kleine cache in het geheugen met een houdbaarheidsduur.
 *
 * [snapshot] geeft de laatst geladen waarde terug, ongeacht ouderdom — genoeg om
 * een scherm meteen te vullen in plaats van eerst een laadscherm te tonen.
 * [fresh] geeft die waarde alleen terug als hij nog vers is, zodat snel heen en
 * weer navigeren geen nieuwe netwerkoproep kost.
 */
class TimedCache<T>(private val ttlMs: Long) {
    @Volatile private var value: T? = null
    @Volatile private var storedAt = 0L

    fun snapshot(): T? = value

    fun fresh(): T? {
        val v = value ?: return null
        return if (SystemClock.elapsedRealtime() - storedAt < ttlMs) v else null
    }

    fun put(v: T) {
        value = v
        storedAt = SystemClock.elapsedRealtime()
    }

    fun clear() {
        value = null
        storedAt = 0L
    }
}

/** Standaard houdbaarheid voor catalogusinhoud: vers genoeg, maar niet muf. */
const val CATALOG_TTL_MS = 5 * 60_000L

/** Hoe vaak de startpagina zichzelf stil ververst zolang hij op de voorgrond staat. */
const val BACKGROUND_REFRESH_MS = 10 * 60_000L
