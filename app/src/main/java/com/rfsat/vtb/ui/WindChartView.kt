package com.rfsat.vtb.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/**
 * Minimal, dependency-free line chart used on the Results screen
 * (replaces com.jjoe64:graphview, which is abandoned and pre-AndroidX —
 * it crashed at inflate time with NoClassDefFoundError on
 * android.support.v4.widget.EdgeEffectCompat, VTB v5.0).
 *
 * Theme-aware: line/points use the theme's colorAccent, axes/labels use
 * android:textColorPrimary, so it follows all four display modes
 * (Dark / Day / Night Green / Night Red) automatically.
 */
class WindChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var title: String = ""
        set(value) { field = value; invalidate() }

    private var xs = DoubleArray(0)
    private var ys = DoubleArray(0)

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val accentColor = resolveAttr(com.google.android.material.R.attr.colorAccent, Color.YELLOW)
    private val textColor = resolveAttr(android.R.attr.textColorPrimary, Color.WHITE)
    private val gridColor = (textColor and 0x00FFFFFF) or (0x30 shl 24) // text colour @ ~19% alpha

    private fun resolveAttr(attr: Int, fallback: Int): Int {
        val tv = TypedValue()
        if (!context.theme.resolveAttribute(attr, tv, true)) return fallback
        return if (tv.resourceId != 0) context.getColor(tv.resourceId) else tv.data
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor; style = Paint.Style.STROKE; strokeWidth = dp(2f)
        strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor; style = Paint.Style.FILL
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor; style = Paint.Style.STROKE; strokeWidth = dp(1.2f)
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = gridColor; style = Paint.Style.STROKE; strokeWidth = dp(1f)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor; textSize = dp(11f)
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor; textSize = dp(13f); isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    /** Replace the plotted series. Pairs are (x = time s, y = value). */
    fun setSeries(points: List<Pair<Double, Double>>) {
        xs = DoubleArray(points.size) { points[it].first }
        ys = DoubleArray(points.size) { points[it].second }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val padL = dp(46f); val padR = dp(10f)
        val padT = if (title.isNotEmpty()) dp(28f) else dp(10f)
        val padB = dp(30f)
        val plotL = padL; val plotR = width - padR
        val plotT = padT; val plotB = height - padB
        if (plotR - plotL < dp(20f) || plotB - plotT < dp(20f)) return

        if (title.isNotEmpty()) {
            canvas.drawText(title, width / 2f, dp(18f), titlePaint)
        }

        if (xs.size < 2) {
            labelPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("No chart data.", width / 2f, (plotT + plotB) / 2f, labelPaint)
            labelPaint.textAlign = Paint.Align.LEFT
            return
        }

        var xMin = xs.min(); var xMax = xs.max()
        var yMin = ys.min(); var yMax = ys.max()
        if (xMax - xMin < 1e-9) { xMin -= 0.5; xMax += 0.5 }
        if (yMax - yMin < 1e-9) { yMin -= 1.0; yMax += 1.0 }
        // A touch of headroom so the trace doesn't hug the frame.
        val ySpan = yMax - yMin
        yMin -= ySpan * 0.05; yMax += ySpan * 0.05
        // Include zero when it's nearby — the sign of crosswind matters.
        if (yMin > 0 && yMin < ySpan * 0.5) yMin = 0.0
        if (yMax < 0 && -yMax < ySpan * 0.5) yMax = 0.0

        fun px(x: Double) = (plotL + (x - xMin) / (xMax - xMin) * (plotR - plotL)).toFloat()
        fun py(y: Double) = (plotB - (y - yMin) / (yMax - yMin) * (plotB - plotT)).toFloat()

        // Gridlines + labels at "nice" tick intervals.
        labelPaint.textAlign = Paint.Align.RIGHT
        for (t in niceTicks(yMin, yMax, 5)) {
            val y = py(t)
            canvas.drawLine(plotL, y, plotR, y, gridPaint)
            canvas.drawText(fmt(t), plotL - dp(6f), y + dp(4f), labelPaint)
        }
        labelPaint.textAlign = Paint.Align.CENTER
        for (t in niceTicks(xMin, xMax, 6)) {
            val x = px(t)
            canvas.drawLine(x, plotT, x, plotB, gridPaint)
            canvas.drawText(fmt(t), x, plotB + dp(16f), labelPaint)
        }
        labelPaint.textAlign = Paint.Align.LEFT

        // Zero line, emphasised, when it's inside the range.
        if (yMin < 0 && yMax > 0) {
            val y0 = py(0.0)
            val zero = Paint(gridPaint).apply { strokeWidth = dp(1.5f); alpha = 0x60 }
            canvas.drawLine(plotL, y0, plotR, y0, zero)
        }

        // Axes frame.
        canvas.drawLine(plotL, plotT, plotL, plotB, axisPaint)
        canvas.drawLine(plotL, plotB, plotR, plotB, axisPaint)

        // Series polyline + sample dots.
        val path = Path()
        for (i in xs.indices) {
            val x = px(xs[i]); val y = py(ys[i])
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, linePaint)
        val r = dp(2.5f)
        for (i in xs.indices) canvas.drawCircle(px(xs[i]), py(ys[i]), r, pointPaint)
    }

    /** Round tick positions (1/2/5 × 10^n steps) covering [lo, hi]. */
    private fun niceTicks(lo: Double, hi: Double, target: Int): List<Double> {
        val rawStep = (hi - lo) / max(1, target)
        val mag = 10.0.pow(floor(log10(rawStep)))
        val norm = rawStep / mag
        val step = when {
            norm <= 1.0 -> 1.0
            norm <= 2.0 -> 2.0
            norm <= 5.0 -> 5.0
            else -> 10.0
        } * mag
        val first = ceil(lo / step) * step
        val out = ArrayList<Double>()
        var t = first
        while (t <= hi + step * 1e-6) { out.add(t); t += step }
        return out
    }

    private fun fmt(v: Double): String =
        if (abs(v) >= 100 || v == floor(v) && abs(v) < 1e6) "%.0f".format(v) else "%.2f".format(v)
}
