package com.rfsat.vtb

import android.app.Application
import com.rfsat.vtb.log.Logger
import com.rfsat.vtb.ui.ThemeManager

/**
 * v19.1 startup hardening (after a startup-crash report):
 *  1. The uncaught-exception handler installs FIRST — previously it was
 *     installed after Logger/Theme/Units/AnalysisSession init, so a crash
 *     in any of those was never recorded anywhere.
 *  2. The handler writes the stack into its own prefs with commit()
 *     (synchronous) — the process is about to die, so async apply() and
 *     even the file log may never flush.
 *  3. SAFE MODE: if the previous launch crashed, stored-state restore is
 *     skipped this launch — a corrupt stored payload can then never crash
 *     the app twice — and MainActivity shows the recorded stack in a
 *     dialog the user can share. No adb required.
 */
class VtbApp : Application() {
    override fun onCreate() {
        super.onCreate()

        val crashPrefs = getSharedPreferences(CRASH_PREFS, MODE_PRIVATE)
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                crashPrefs.edit()
                    .putString(KEY_STACK, "thread ${thread.name}\n" + android.util.Log.getStackTraceString(throwable))
                    .putLong(KEY_TIME, System.currentTimeMillis())
                    .commit() // synchronous — the process dies next
            }
            runCatching { Logger.e("CRASH", "Uncaught exception on thread ${thread.name}", throwable) }
            previousHandler?.uncaughtException(thread, throwable)
        }

        val lastCrashed = crashPrefs.contains(KEY_STACK)
        runCatching { Logger.init(this) }
        runCatching { ThemeManager.init(this) }
        runCatching { com.rfsat.vtb.ui.UnitsManager.init(this) }
        if (lastCrashed) {
            runCatching { Logger.w("VtbApp", "Previous launch crashed — skipping stored-analysis restore (safe mode)") }
        } else {
            runCatching { com.rfsat.vtb.results.AnalysisSession.restore(this) }
        }
        runCatching { Logger.i("VtbApp", "Application started, VTB ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})") }
    }

    companion object {
        const val CRASH_PREFS = "vtb_crash"
        const val KEY_STACK = "stack"
        const val KEY_TIME = "time"
    }
}
