package com.rfsat.vtb.log

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

enum class LogLevel(val label: String) { INFO("I"), WARNING("W"), ERROR("E") }

data class LogEntry(val timestampMs: Long, val level: LogLevel, val tag: String, val message: String) {
    override fun toString(): String {
        val ts = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date(timestampMs))
        return "$ts ${level.label}/$tag: $message"
    }
}

/**
 * App-wide debug log with three severity levels (INFO / WARNING / ERROR).
 *
 * Entries are BOTH kept in memory and appended synchronously to a file in
 * the app's private storage — the file is what survives a process death,
 * which is exactly when the log matters most (the earlier in-memory-only
 * design was why the log came up empty after crashes). On startup, the
 * tail of the persisted file is reloaded so the previous session's final
 * moments are visible in the Log tab.
 *
 * Every entry is also mirrored to android.util.Log for adb logcat.
 */
object Logger {
    private const val MAX_ENTRIES = 4000
    private const val MAX_FILE_BYTES = 512 * 1024L
    private val entries = CopyOnWriteArrayList<LogEntry>()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private var logFile: File? = null
    private val fileLock = Any()

    /** Call once from Application.onCreate. Reloads the persisted tail. */
    fun init(context: Context) {
        val f = File(context.filesDir, "vtb_log.txt")
        logFile = f
        try {
            if (f.exists()) {
                if (f.length() > MAX_FILE_BYTES) trimFile(f)
                val previous = f.readLines().takeLast(500)
                if (previous.isNotEmpty()) {
                    entries.add(LogEntry(System.currentTimeMillis(), LogLevel.INFO, "Logger",
                        "---- ${previous.size} line(s) restored from previous session below ----"))
                    previous.forEach { line ->
                        entries.add(LogEntry(System.currentTimeMillis(), levelFromLine(line), "prev", line))
                    }
                    entries.add(LogEntry(System.currentTimeMillis(), LogLevel.INFO, "Logger",
                        "---- end of previous session ----"))
                }
            }
        } catch (t: Throwable) {
            Log.e("Logger", "Failed to restore persisted log", t)
        }
        i("Logger", "Logger initialized; persisting to ${f.absolutePath}")
    }

    private fun levelFromLine(line: String): LogLevel = when {
        " E/" in line -> LogLevel.ERROR
        " W/" in line -> LogLevel.WARNING
        else -> LogLevel.INFO
    }

    private fun trimFile(f: File) {
        try {
            val tail = f.readLines().takeLast(1000).joinToString("\n")
            f.writeText(tail + "\n")
        } catch (_: Throwable) { f.delete() }
    }

    fun addListener(listener: () -> Unit) = listeners.add(listener)
    fun removeListener(listener: () -> Unit) = listeners.remove(listener)

    fun snapshot(minLevel: LogLevel? = null): List<LogEntry> =
        if (minLevel == null) entries.toList()
        else entries.filter { it.level.ordinal >= minLevel.ordinal }

    fun snapshotOfLevel(level: LogLevel?): List<LogEntry> =
        if (level == null) entries.toList() else entries.filter { it.level == level }

    fun clear() {
        entries.clear()
        synchronized(fileLock) { try { logFile?.writeText("") } catch (_: Throwable) {} }
        notifyListeners()
    }

    fun i(tag: String, message: String) = add(LogLevel.INFO, tag, message)
    fun w(tag: String, message: String) = add(LogLevel.WARNING, tag, message)
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val full = if (throwable != null)
            "$message :: ${throwable.javaClass.simpleName}: ${throwable.message}\n${Log.getStackTraceString(throwable)}"
        else message
        add(LogLevel.ERROR, tag, full)
    }
    // Debug-level messages are folded into INFO to keep the 3-level scheme.
    fun d(tag: String, message: String) = add(LogLevel.INFO, tag, message)

    private fun add(level: LogLevel, tag: String, message: String) {
        when (level) {
            LogLevel.INFO -> Log.i(tag, message)
            LogLevel.WARNING -> Log.w(tag, message)
            LogLevel.ERROR -> Log.e(tag, message)
        }
        val entry = LogEntry(System.currentTimeMillis(), level, tag, message)
        entries.add(entry)
        while (entries.size > MAX_ENTRIES) entries.removeAt(0)
        // Synchronous append: slower, but guarantees the line hits disk
        // before a crash can kill the process.
        synchronized(fileLock) {
            try {
                val f = logFile
                if (f != null) {
                    if (f.length() > MAX_FILE_BYTES) trimFile(f)
                    f.appendText(entry.toString() + "\n")
                }
            } catch (_: Throwable) { /* never let logging crash the app */ }
        }
        notifyListeners()
    }

    private fun notifyListeners() { listeners.forEach { it() } }

    fun asText(level: LogLevel? = null): String =
        snapshotOfLevel(level).joinToString("\n") { it.toString() }
}
