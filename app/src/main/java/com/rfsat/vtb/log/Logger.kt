package com.rfsat.vtb.log

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

data class LogEntry(val timestampMs: Long, val level: String, val tag: String, val message: String) {
    override fun toString(): String {
        val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestampMs))
        return "$ts $level/$tag: $message"
    }
}

/**
 * App-wide debug log, independent of logcat (which the user usually can't
 * see on a field device). Every entry also gets mirrored to android.util.Log
 * so `adb logcat` still works during development.
 */
object Logger {
    private const val MAX_ENTRIES = 4000
    private val entries = CopyOnWriteArrayList<LogEntry>()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun addListener(listener: () -> Unit) = listeners.add(listener)
    fun removeListener(listener: () -> Unit) = listeners.remove(listener)

    fun snapshot(): List<LogEntry> = entries.toList()

    fun clear() {
        entries.clear()
        notifyListeners()
    }

    fun d(tag: String, message: String) = add("D", tag, message)
    fun i(tag: String, message: String) = add("I", tag, message)
    fun w(tag: String, message: String) = add("W", tag, message)
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val full = if (throwable != null) "$message :: ${throwable.javaClass.simpleName}: ${throwable.message}\n${Log.getStackTraceString(throwable)}" else message
        add("E", tag, full)
    }

    private fun add(level: String, tag: String, message: String) {
        when (level) {
            "D" -> Log.d(tag, message)
            "I" -> Log.i(tag, message)
            "W" -> Log.w(tag, message)
            "E" -> Log.e(tag, message)
        }
        entries.add(LogEntry(System.currentTimeMillis(), level, tag, message))
        while (entries.size > MAX_ENTRIES) entries.removeAt(0)
        notifyListeners()
    }

    private fun notifyListeners() {
        listeners.forEach { it() }
    }

    fun asText(): String = entries.joinToString("\n") { it.toString() }
}
