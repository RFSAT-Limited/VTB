package com.rfsat.vtb.ui

import android.content.Context

enum class UnitSystem(val label: String) {
    IMPERIAL("Imperial (yd, mph, in)"),
    METRIC("Metric (m, m/s, cm)")
}

/**
 * App-wide measurement system for DISPLAY and INPUT. All internal physics
 * stays SI (metres, m/s) — this object only converts at the UI boundary.
 * Imperial is the default, matching the app's original yd/mph conventions.
 */
object UnitsManager {
    private const val PREFS = "vtb_units"
    private const val KEY = "system"
    private var current: UnitSystem = UnitSystem.IMPERIAL

    private const val M_PER_YD = 0.9144
    private const val MPS_TO_MPH = 2.23694
    private const val M_TO_IN = 39.3701

    fun init(context: Context) {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        current = UnitSystem.values().firstOrNull { it.name == saved } ?: UnitSystem.IMPERIAL
    }

    fun system(): UnitSystem = current
    fun isImperial(): Boolean = current == UnitSystem.IMPERIAL

    fun setSystem(context: Context, system: UnitSystem) {
        current = system
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, system.name).apply()
    }

    // ---- input: value typed by the user in the active unit -> SI ----

    /** Target-distance field value (yd or m depending on setting) -> metres. */
    fun inputDistanceToMeters(value: Double): Double =
        if (isImperial()) value * M_PER_YD else value

    fun distanceUnitLabel(): String = if (isImperial()) "yd" else "m"
    fun speedUnitLabel(): String = if (isImperial()) "mph" else "m/s"
    fun offsetUnitLabel(): String = if (isImperial()) "in" else "cm"

    // ---- output: SI -> display value in the active unit ----

    fun displayDistance(meters: Double): Double =
        if (isImperial()) meters / M_PER_YD else meters

    fun displaySpeed(mps: Double): Double =
        if (isImperial()) mps * MPS_TO_MPH else mps

    fun displayOffset(meters: Double): Double =
        if (isImperial()) meters * M_TO_IN else meters * 100.0
}
