package com.rfsat.vtb.capture

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.rfsat.vtb.log.Logger
import java.nio.ByteOrder

/**
 * Audio shot-break detection (v19.7, accuracy).
 *
 * Every analysis previously trusted the manual shot-break field (default
 * 0.5 s). An error there shifts the settle window and, in tracer mode,
 * directly biases the lag rule (deflection = W x (t - x/v0) — tens of
 * milliseconds matter). The muzzle report is an unmistakable transient in
 * the clip's own audio track, so t0 can be pinned to a few milliseconds —
 * for imported clips just as well as recorded ones.
 *
 * Method: decode the audio track to PCM, track peak amplitude per 5 ms
 * window, then take the FIRST window reaching >= 60% of the global peak
 * (first shot of a string, not the loudest). Two guards keep it honest:
 * no audio track -> null; peak under 4x the median window level (no
 * distinct transient — wind noise, music, silence) -> null. Callers fall
 * back to the manual field on null.
 */
object AudioShotDetector {

    private const val TAG = "AudioShotDetector"
    private const val WINDOW_S = 0.005
    private const val ONSET_FRACTION = 0.60
    private const val MIN_PEAK_OVER_MEDIAN = 4.0
    private const val DEQUEUE_TIMEOUT_US = 10_000L
    private const val MAX_DRAIN_TRIES = 500

    /** @return time of the shot report in seconds from clip start, or null
     *  if the clip has no audio or no distinct transient. */
    fun detectShotTimeS(videoPath: String): Double? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(videoPath)
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    track = i; format = f; break
                }
            }
            if (track < 0 || format == null) {
                Logger.i(TAG, "No audio track — using manual shot-break")
                return null
            }
            extractor.selectTrack(track)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            // Peak absolute sample per window, indexed by window number.
            val windowPeaks = ArrayList<Double>(4096)
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var drainTries = 0
            var baseTimeS = -1.0

            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US)
                when {
                    outIdx >= 0 -> {
                        drainTries = 0
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        if (info.size > 0) {
                            if (baseTimeS < 0) baseTimeS = info.presentationTimeUs / 1_000_000.0
                            // Output format may carry updated rate/channels.
                            val of = codec.outputFormat
                            if (of.containsKey(MediaFormat.KEY_SAMPLE_RATE))
                                sampleRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            if (of.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
                                channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            val samplesPerWindow = (sampleRate * WINDOW_S).toInt().coerceAtLeast(1)

                            val bb = codec.getOutputBuffer(outIdx)!!
                            bb.order(ByteOrder.LITTLE_ENDIAN)
                            val sb = bb.asShortBuffer()
                            val tS = info.presentationTimeUs / 1_000_000.0
                            var frame = 0
                            val frames = sb.remaining() / channels
                            while (frame < frames) {
                                // mono mix by first channel (peak detection
                                // doesn't need a proper downmix)
                                val v = sb.get(frame * channels).toInt()
                                val a = if (v < 0) -v.toDouble() else v.toDouble()
                                val wIdx = (((tS - baseTimeS) + frame.toDouble() / sampleRate) / WINDOW_S).toInt()
                                while (windowPeaks.size <= wIdx) windowPeaks.add(0.0)
                                if (a > windowPeaks[wIdx]) windowPeaks[wIdx] = a
                                frame++
                            }
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                    }
                    outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (++drainTries > MAX_DRAIN_TRIES) {
                            Logger.w(TAG, "Audio decoder stalled"); outputDone = true
                        }
                    }
                }
            }

            if (windowPeaks.size < 10) return null
            val globalPeak = windowPeaks.max()
            val median = windowPeaks.sorted()[windowPeaks.size / 2]
            if (globalPeak < MIN_PEAK_OVER_MEDIAN * (median + 1.0)) {
                Logger.i(TAG, "No distinct audio transient (peak/median=%.1f) — using manual shot-break"
                    .format(globalPeak / (median + 1.0)))
                return null
            }
            val threshold = globalPeak * ONSET_FRACTION
            val idx = windowPeaks.indexOfFirst { it >= threshold }
            if (idx < 0) return null
            val tShot = baseTimeS.coerceAtLeast(0.0) + idx * WINDOW_S
            Logger.i(TAG, "Shot report detected at %.3f s (peak/median=%.1f)"
                .format(tShot, globalPeak / (median + 1.0)))
            return tShot
        } catch (t: Throwable) {
            Logger.w(TAG, "Audio shot detection failed — using manual shot-break: ${t.message}")
            return null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }
}
