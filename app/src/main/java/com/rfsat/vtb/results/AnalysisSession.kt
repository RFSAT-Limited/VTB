package com.rfsat.vtb.results

import android.content.Context
import com.google.gson.Gson
import com.rfsat.vtb.wind.WindSample

/**
 * Holder for the last capture's results. Persisted to SharedPreferences as
 * JSON (v10.1) so the Results toolbar item shows the last analysed data
 * even after the app is closed and reopened — not just for the process
 * lifetime. One shot's worth of data at a time is enough for the workflow:
 * capture -> analyze -> results. (A multi-shot history would move to Room.)
 */
object AnalysisSession {
    private const val PREFS = "vtb_last_analysis"
    private const val KEY = "payload"
    private val gson = Gson()

    var windSamples: List<WindSample> = emptyList()
    var adjustment: ScopeAdjustment? = null
    var targetDistanceYd: Double = 100.0

    /** Everything the Results screen needs, in one Gson-friendly bundle. */
    private data class Payload(
        val windSamples: List<WindSample>,
        val adjustment: ScopeAdjustment,
        val targetDistanceYd: Double
    )

    /** Call after a successful analysis to survive app restarts. */
    fun persist(context: Context) {
        val adj = adjustment ?: return
        val json = gson.toJson(Payload(windSamples, adj, targetDistanceYd))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, json).apply()
    }

    /** Call once at app start; no-op if nothing stored or already loaded. */
    fun restore(context: Context) {
        if (adjustment != null) return
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return
        // runCatching: a payload written by an older app version may not
        // match the current data classes — treat it as absent, don't crash.
        runCatching { gson.fromJson(json, Payload::class.java) }.getOrNull()?.let {
            windSamples = it.windSamples
            adjustment = it.adjustment
            targetDistanceYd = it.targetDistanceYd
        }
    }
}
