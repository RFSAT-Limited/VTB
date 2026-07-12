package com.rfsat.vtb.capture

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import com.rfsat.vtb.log.Logger
import kotlin.math.abs
import kotlin.math.max

/**
 * Listens on the mic for a sudden, loud transient — the muzzle report —
 * and calls [onShotDetected] once (on the main thread) the first time it
 * fires. Since the phone is mounted on the rifle itself, the report
 * arrives at the mic essentially the instant the bullet exits the barrel,
 * so this is a reasonable proxy for shot-break timing.
 *
 * CAVEAT: there's real latency between "callback fires" and "video frames
 * actually start landing" — mic buffer read latency, the detection loop's
 * own polling interval, and CameraX's recorder startup time all add up to
 * roughly [CaptureActivity.AUTO_TRIGGER_LATENCY_S] worth of trail that may
 * already be gone before recording begins. Not zero-latency triggering —
 * a decent approximation for a rimfire at moderate range, worth validating
 * against a known trail length if used at longer range or higher velocity.
 */
class ShotDetector(private val onShotDetected: () -> Unit) {

    @Volatile private var listening = false
    @Volatile private var triggered = false
    private var thread: Thread? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object { private const val TAG = "ShotDetector" }

    /**
     * [sensitivityPercent] 0..100 — higher means more sensitive (triggers
     * on quieter sounds). Tune in the field: a noisy range needs a lower
     * number than a quiet one.
     */
    @SuppressLint("MissingPermission") // caller (CaptureActivity) verifies RECORD_AUDIO first
    fun start(sensitivityPercent: Int = 70) {
        if (listening) return
        triggered = false
        listening = true

        val sampleRate = 44100
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) {
            Logger.e(TAG, "AudioRecord.getMinBufferSize failed ($minBuf) — device may not support this config")
            listening = false
            return
        }
        val bufferSize = max(minBuf, 2048)

        // Map sensitivity (0..100) to a peak-amplitude threshold (0..32767).
        // Higher sensitivity -> lower threshold -> easier to trigger.
        val clampedSensitivity = sensitivityPercent.coerceIn(0, 100)
        val threshold = (32767 * (1.0 - clampedSensitivity / 100.0 * 0.9)).toInt().coerceIn(500, 32767)

        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC, sampleRate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize
            )
        } catch (t: Throwable) {
            Logger.e(TAG, "Failed to create AudioRecord", t)
            listening = false
            return
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            Logger.e(TAG, "AudioRecord failed to initialize")
            listening = false
            return
        }

        thread = Thread {
            val buf = ShortArray(bufferSize)
            try {
                recorder.startRecording()
                Logger.i(TAG, "Listening for shot (threshold=$threshold, sensitivity=$clampedSensitivity%)")
                while (listening && !triggered) {
                    val read = recorder.read(buf, 0, buf.size)
                    if (read > 0) {
                        var peak = 0
                        for (i in 0 until read) {
                            val a = abs(buf[i].toInt())
                            if (a > peak) peak = a
                        }
                        if (peak >= threshold) {
                            triggered = true
                            Logger.i(TAG, "Shot detected: peak=$peak >= threshold=$threshold")
                            mainHandler.post { onShotDetected() }
                        }
                    }
                }
            } catch (t: Throwable) {
                Logger.e(TAG, "Shot detection loop failed", t)
            } finally {
                try { recorder.stop() } catch (_: Throwable) {}
                recorder.release()
            }
        }.also { it.start() }
    }

    fun stop() {
        listening = false
        thread?.interrupt()
        thread = null
    }

    fun isListening(): Boolean = listening
}
