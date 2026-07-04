package com.rfsat.vtb

import android.app.Application
import com.rfsat.vtb.log.Logger
import com.rfsat.vtb.ui.ThemeManager

/**
 * Installs a default uncaught-exception handler that writes the crash to
 * the persistent log BEFORE the process dies — this is what makes crashes
 * diagnosable from the Log tab on next launch (previously the in-memory
 * log evaporated with the process, which is why it showed up empty).
 */
class VtbApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Logger.init(this)
        ThemeManager.init(this)

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Logger.e("CRASH", "Uncaught exception on thread ${thread.name}", throwable)
            previousHandler?.uncaughtException(thread, throwable)
        }
        Logger.i("VtbApp", "Application started, VTB ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
    }
}
