package com.rfsat.vtb.profiles

import android.content.Context
import com.google.gson.Gson

/**
 * Simple JSON-in-SharedPreferences store for the currently active
 * rifle/bullet/scope profile. Good enough for a single-user field tool;
 * swap for Room if multi-profile libraries are wanted later.
 */
class ProfileRepository(context: Context) {
    private val prefs = context.getSharedPreferences("vtb_profiles", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getRifle(): RifleProfile =
        prefs.getString(KEY_RIFLE, null)?.let { gson.fromJson(it, RifleProfile::class.java) }
            ?: RifleProfile.DEFAULT

    fun saveRifle(profile: RifleProfile) {
        prefs.edit().putString(KEY_RIFLE, gson.toJson(profile)).apply()
    }

    fun getBullet(): BulletProfile =
        prefs.getString(KEY_BULLET, null)?.let { gson.fromJson(it, BulletProfile::class.java) }
            ?: BulletProfile.DEFAULT

    fun saveBullet(profile: BulletProfile) {
        prefs.edit().putString(KEY_BULLET, gson.toJson(profile)).apply()
    }

    fun getScope(): ScopeProfile {
        val json = prefs.getString(KEY_SCOPE, null) ?: return ScopeProfile.DEFAULT
        val parsed = runCatching { gson.fromJson(json, ScopeProfile::class.java) }.getOrNull()
            ?: return ScopeProfile.DEFAULT
        // MIGRATION: profiles saved before v4.0 predate the optical fields —
        // Gson leaves those as 0.0/null on old JSON, which silently overrode
        // the new Continental 5-30x56 default (this is why v4.0 appeared to
        // "not include" the new scope). Detect a stale/damaged profile and
        // replace it with the current default once.
        @Suppress("SENSELESS_COMPARISON")
        val stale = parsed.name == null || parsed.name.isBlank() ||
            parsed.name.contains("placeholder", ignoreCase = true) ||
            (parsed.clickUnit as ClickUnit?) == null ||
            parsed.zoomMax <= 0.0 || parsed.heightAboveBarrelIn <= 0.0
        return if (stale) {
            saveScope(ScopeProfile.DEFAULT)
            ScopeProfile.DEFAULT
        } else parsed
    }

    fun saveScope(profile: ScopeProfile) {
        prefs.edit().putString(KEY_SCOPE, gson.toJson(profile)).apply()
    }

    fun resetToDefaults() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_RIFLE = "rifle_profile"
        private const val KEY_BULLET = "bullet_profile"
        private const val KEY_SCOPE = "scope_profile"
    }
}
