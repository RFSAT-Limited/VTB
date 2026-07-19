package com.rfsat.vtb.profiles

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner

/**
 * Per-parameter unit selection for the Bullet and Rifle profile fields
 * (v20.9).
 *
 * The profile data classes DO NOT change — every value is stored in its
 * canonical unit (caliber inches, weight grains, MV fps, distance metres,
 * temperature degC). A FieldUnit binds one EditText to a small unit Spinner
 * and converts only at the display/entry boundary:
 *   - reading the field  -> convert the shown value back to canonical
 *   - showing a value    -> convert canonical to the chosen unit
 *   - changing the unit   -> convert the number in place, live
 * Each field's chosen unit persists under its own key so the screen
 * reopens the way the user left it.
 */
object FieldUnits {

    /** A convertible quantity: label pairs + canonical<->display factors. */
    enum class Kind(val units: List<UnitDef>, val prefsKey: String) {
        CALIBER(listOf(UnitDef("in", 1.0), UnitDef("mm", 25.4)), "u_caliber"),
        WEIGHT(listOf(UnitDef("gr", 1.0), UnitDef("g", 0.06479891)), "u_weight"),
        VELOCITY(listOf(UnitDef("fps", 1.0), UnitDef("m/s", 0.3048)), "u_velocity"),
        DISTANCE(listOf(UnitDef("m", 1.0), UnitDef("yd", 1.0 / 0.9144), UnitDef("ft", 1.0 / 0.3048)), "u_distance"),
        LENGTH_IN(listOf(UnitDef("in", 1.0), UnitDef("mm", 25.4), UnitDef("cm", 2.54)), "u_length"),
        TEMPERATURE(listOf(UnitDef("\u00b0C", 1.0), UnitDef("\u00b0F", 1.0)), "u_temperature");
    }

    /**
     * @param label   what the spinner shows
     * @param perCanonical  display = canonical * perCanonical (linear units)
     */
    data class UnitDef(val label: String, val perCanonical: Double)

    // Temperature is affine, not linear, so it gets explicit conversions.
    private fun toDisplay(kind: Kind, unitIdx: Int, canonical: Double): Double =
        if (kind == Kind.TEMPERATURE && unitIdx == 1) canonical * 9.0 / 5.0 + 32.0
        else canonical * kind.units[unitIdx].perCanonical

    private fun toCanonical(kind: Kind, unitIdx: Int, shown: Double): Double =
        if (kind == Kind.TEMPERATURE && unitIdx == 1) (shown - 32.0) * 5.0 / 9.0
        else shown / kind.units[unitIdx].perCanonical

    /** Binds a field to its unit spinner; call [set]/[get] for canonical values. */
    class Binding(
        private val ctx: Context,
        private val kind: Kind,
        private val edit: EditText,
        private val spinner: Spinner
    ) {
        private val prefs = ctx.getSharedPreferences("vtb_field_units", Context.MODE_PRIVATE)
        private var unitIdx = prefs.getInt(kind.prefsKey, 0).coerceIn(0, kind.units.size - 1)

        // v20.16: cache the EXACT canonical value and the display string we
        // last wrote. A unit switch re-derives the display from this cache
        // instead of re-parsing the ROUNDED box — so 40 gr -> g -> gr comes
        // back to exactly 40. The cache is trusted only while the box still
        // holds what we wrote; if the user typed something else, we reparse.
        private var cachedCanonical: Double? = null
        private var lastShown: String? = null

        init {
            spinner.adapter = ArrayAdapter(
                ctx, android.R.layout.simple_spinner_item, kind.units.map { it.label }
            ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            spinner.setSelection(unitIdx, false)
            spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                    if (pos == unitIdx) return
                    val canonical = currentCanonical() // exact when unedited
                    unitIdx = pos
                    prefs.edit().putInt(kind.prefsKey, pos).apply()
                    if (canonical != null) showCanonical(canonical)
                }
                override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
            }
        }

        private fun readCanonicalUsing(idx: Int): Double? =
            edit.text.toString().toDoubleOrNull()?.let { toCanonical(kind, idx, it) }

        /**
         * Canonical value of what is in the box. If the box still holds the
         * exact string we last wrote, return the cached exact canonical (no
         * round-trip error); otherwise the user edited it, so parse the text.
         */
        private fun currentCanonical(): Double? {
            val text = edit.text.toString()
            val cached = cachedCanonical
            if (cached != null && text == lastShown) return cached
            return readCanonicalUsing(unitIdx)
        }

        /** Current field value in canonical units, or null if blank/invalid. */
        fun get(): Double? = currentCanonical()

        /** Show a canonical value in the currently selected unit. */
        fun set(canonical: Double) = showCanonical(canonical)

        private fun showCanonical(canonical: Double) {
            cachedCanonical = canonical // remember the EXACT value we displayed
            val shown = trimNumber(toDisplay(kind, unitIdx, canonical))
            lastShown = shown
            edit.setText(shown)
        }

        fun unitLabel(): String = kind.units[unitIdx].label

        private fun trimNumber(v: Double): String =
            if (v == v.toLong().toDouble()) v.toLong().toString() else "%.6f".format(v).trimEnd('0').trimEnd('.')
    }
}
