package com.rfsat.vtb.capture

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Draws a simple scope-style crosshair over the live preview. The phone is
 * mounted parallel to the scope; the shooter mechanically aligns the
 * physical scope's crosshair to this on-screen one, then uses
 * [offsetXNorm]/[offsetYNorm] to fine-tune for any small mounting
 * misalignment. This drawn position is also exactly the boresight pixel
 * fed to [TrailCalibration] — no separate "tap to mark" step needed.
 */
class CrosshairOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var offsetXNorm: Double = 0.0
        set(value) { field = value; invalidate() }
    var offsetYNorm: Double = 0.0
        set(value) { field = value; invalidate() }

    // Follow the active display style: same colour as the theme's primary
    // text (gold in Dark, dark green in Day, pure green/red in night modes).
    private val themeTextColor: Int = run {
        val tv = android.util.TypedValue()
        if (context.theme.resolveAttribute(android.R.attr.textColorPrimary, tv, true)) {
            if (tv.resourceId != 0) context.getColor(tv.resourceId) else tv.data
        } else Color.parseColor("#C9A24B")
    }

    private val linePaint = Paint().apply {
        color = themeTextColor
        strokeWidth = 3f
        isAntiAlias = true
    }
    private val circlePaint = Paint().apply {
        color = themeTextColor
        strokeWidth = 3f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    fun centerPixelX(): Float = (0.5f + offsetXNorm.toFloat()) * width
    fun centerPixelY(): Float = (0.5f + offsetYNorm.toFloat()) * height

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = centerPixelX()
        val cy = centerPixelY()
        val armLen = width.coerceAtMost(height) * 0.12f
        val gap = armLen * 0.35f

        canvas.drawLine(cx - armLen, cy, cx - gap, cy, linePaint)
        canvas.drawLine(cx + gap, cy, cx + armLen, cy, linePaint)
        canvas.drawLine(cx, cy - armLen, cx, cy - gap, linePaint)
        canvas.drawLine(cx, cy + gap, cx, cy + armLen, linePaint)
        canvas.drawCircle(cx, cy, gap, circlePaint)
    }
}
