package com.rfsat.vtb.wind

import com.rfsat.vtb.ballistics.Atmosphere
import com.rfsat.vtb.ballistics.BallisticsEngine
import com.rfsat.vtb.capture.PixelObservation
import com.rfsat.vtb.capture.TrailCalibration
import com.rfsat.vtb.log.Logger
import kotlin.math.tan

/**
 * Estimates wind from a TRACER round (v14.0) — the tracked point is the
 * BULLET, not the drifting air, so this is a different inverse problem
 * from [WindEstimator]'s trail-drift model.
 *
 * PHYSICAL MODEL — the classic lag rule: a bullet in a uniform crosswind
 * W acquires a lateral deflection
 *
 *     z(t) = W * (t - x(t)/v0)
 *
 * where x(t) is downrange distance from the (no-wind) trajectory and v0
 * the muzzle velocity. The bracket is the LAG TIME — how much longer the
 * bullet took to reach x than it would have in vacuum — which is why a
 * tracer is such a clean wind observable: everything on the right except
 * W comes from the ballistic solution the app already computes.
 *
 * From the boresighted camera the observation is the angular offset
 * theta_x(t), so z_obs(t) = x(t) * tan(theta_x(t)).
 *
 * BORESIGHT-BIAS SEPARATION: a constant angular misalignment theta0 of
 * the crosshair calibration contributes x(t) * theta0 to z_obs — which
 * GROWS with range and would masquerade as wind if ignored. So instead of
 * dividing point-by-point, we fit the two-parameter model
 *
 *     z_obs_i = W * lag_i + theta0 * x_i
 *
 * by weighted least squares. lag(t) grows super-linearly (the bullet
 * slows) while x(t) grows sub-linearly, so the two regressors are
 * distinguishable over the flight and W comes out bias-corrected. The
 * same fit on the vertical residual (observed minus the predicted
 * trajectory arc) yields the vertical wind.
 *
 * Emitted [WindSample]s use the per-point estimate with the FITTED bias
 * removed, so the Results chart shows honest scatter while averageWind /
 * AdjustmentCalculator (plausibility trims, confidence gates) work on
 * them unchanged.
 *
 * LIMITS: only frames while the bullet is in flight are usable, and the
 * lag is tiny early on — at .22LR/200 m expect ~15-25 usable frames at
 * 30 fps. A tracer also burns mass, so treat the profile BC as
 * approximate (see BulletProfile.isTracer note).
 */
object TracerWindEstimator {

    private const val TAG = "TracerWindEstimator"

    /** Ignore observations whose lag is below this (s): W = z/lag is
     *  singular at lag -> 0, and early-flight frames carry no wind signal. */
    private const val MIN_LAG_S = 0.02
    /** Per-sample emission needs a bit more lag than the fit does. */
    private const val MIN_SAMPLE_LAG_S = 0.05
    /** Ignore observations before the bullet is meaningfully downrange. */
    private const val MIN_DOWNRANGE_M = 5.0

    private const val MIN_POINTS = 6

    fun estimate(
        calibration: TrailCalibration,
        observations: List<PixelObservation>,
        bullet: com.rfsat.vtb.profiles.BulletProfile,
        atmosphere: Atmosphere,
        zeroDistanceM: Double,
        sightHeightM: Double,
        targetDistanceM: Double,
        minConfidence: Double = 0.02
    ): List<WindSample> {
        // No-wind zeroed trajectory -> x(t), y(t) lookup tables.
        val pitch = BallisticsEngine.solveZeroPitch(bullet, atmosphere, zeroDistanceM, sightHeightM)
        val traj = BallisticsEngine.simulate(
            bullet, atmosphere, pitch, 0.0, targetDistanceM + 1.0
        )
        if (traj.size < 2) return emptyList()
        val tofS = traj.last().timeS
        val v0 = bullet.muzzleVelocityMps

        data class Point(
            val tS: Double, val xM: Double, val lagS: Double,
            val zObsM: Double,   // observed lateral offset (+right)
            val yResM: Double,   // observed-minus-predicted vertical offset (+up)
            val conf: Double
        )

        val pts = mutableListOf<Point>()
        for (o in observations) {
            if (o.confidence < minConfidence) continue
            val t = o.timestampS
            // Only in-flight frames: after impact the tracked point is the
            // impact flash / ricochet glow, which no longer flies ballistics.
            if (t <= 0.0 || t > tofS) continue
            val (xM, yPredM) = interpolate(traj, t) ?: continue
            if (xM < MIN_DOWNRANGE_M) continue
            val lag = t - xM / v0
            if (lag < MIN_LAG_S) continue
            val zObs = xM * tan(calibration.pixelAngleX(o.pixelX))
            // Predicted vertical angle: bullet height above the (level) line
            // of sight, which sits sightHeightM above the bore origin.
            val yObs = xM * tan(calibration.pixelAngleY(o.pixelY))
            val yPredLos = yPredM - sightHeightM
            pts.add(Point(t, xM, lag, zObs, yObs - yPredLos, o.conf))
        }
        Logger.i(TAG, "Tracer fit input: ${pts.size} usable in-flight points " +
            "(of ${observations.size} observations, tof=${"%.2f".format(tofS)}s)")
        if (pts.size < MIN_POINTS) return emptyList()

        val lag = DoubleArray(pts.size) { pts[it].lagS }
        val x = DoubleArray(pts.size) { pts[it].xM }
        val cw = DoubleArray(pts.size) { pts[it].conf }

        val fitZ = fitTwoParam(lag, x, DoubleArray(pts.size) { pts[it].zObsM }, cw) ?: return emptyList()
        val fitY = fitTwoParam(lag, x, DoubleArray(pts.size) { pts[it].yResM }, cw) ?: return emptyList()
        Logger.i(TAG, "Tracer fit: crosswind=${"%.2f".format(fitZ.wind)} m/s " +
            "(boresight bias ${"%.2f".format(Math.toDegrees(fitZ.biasRad) * 1000)} mdeg, q=${"%.2f".format(fitZ.quality)}), " +
            "vertical=${"%.2f".format(fitY.wind)} m/s (q=${"%.2f".format(fitY.quality)})")

        // Per-point samples with the fitted boresight bias removed.
        val out = mutableListOf<WindSample>()
        for (p in pts) {
            if (p.lagS < MIN_SAMPLE_LAG_S) continue
            out.add(
                WindSample(
                    timeS = p.tS,
                    downrangeM = p.xM,
                    crosswindMps = (p.zObsM - fitZ.biasRad * p.xM) / p.lagS,
                    verticalWindMps = (p.yResM - fitY.biasRad * p.xM) / p.lagS,
                    confidence = (p.conf * fitZ.quality * fitY.quality).coerceIn(0.0, 1.0)
                )
            )
        }
        return out
    }

    private data class TwoParamFit(val wind: Double, val biasRad: Double, val quality: Double)

    /**
     * Weighted least squares for y = wind * lag + bias * x  (no intercept —
     * both regressors are zero at t=0 by construction). Solves the 2x2
     * normal equations directly. quality penalises residual RMS relative
     * to the deflection span, like WindEstimator's slope fits.
     */
    private fun fitTwoParam(lag: DoubleArray, x: DoubleArray, y: DoubleArray, w: DoubleArray): TwoParamFit? {
        var sll = 0.0; var slx = 0.0; var sxx = 0.0; var sly = 0.0; var sxy = 0.0; var sw = 0.0
        for (i in lag.indices) {
            sll += w[i] * lag[i] * lag[i]
            slx += w[i] * lag[i] * x[i]
            sxx += w[i] * x[i] * x[i]
            sly += w[i] * lag[i] * y[i]
            sxy += w[i] * x[i] * y[i]
            sw += w[i]
        }
        if (sw < 1e-12) return null
        val det = sll * sxx - slx * slx
        // Near-singular: lag and x too collinear over this window (very
        // short observation) — a one-parameter wind fit would be biased,
        // so report failure instead.
        if (det < 1e-9 * sll * sxx || det <= 0.0) return null
        val wind = (sxx * sly - slx * sxy) / det
        val bias = (sll * sxy - slx * sly) / det

        var ss = 0.0; var yMin = y[0]; var yMax = y[0]
        for (i in lag.indices) {
            val r = y[i] - (wind * lag[i] + bias * x[i])
            ss += w[i] * r * r
            if (y[i] < yMin) yMin = y[i]
            if (y[i] > yMax) yMax = y[i]
        }
        val rms = kotlin.math.sqrt(ss / sw)
        val span = yMax - yMin
        val quality = if (span < 1e-9) 0.5 else (1.0 / (1.0 + 3.0 * rms / span)).coerceIn(0.0, 1.0)
        return TwoParamFit(wind, bias, quality)
    }

    /** Linear interpolation of (x, y) at time t from the trajectory. */
    private fun interpolate(traj: List<com.rfsat.vtb.ballistics.TrajectoryPoint>, tS: Double): Pair<Double, Double>? {
        if (tS < traj.first().timeS || tS > traj.last().timeS) return null
        var lo = 0; var hi = traj.size - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if (traj[mid].timeS <= tS) lo = mid else hi = mid
        }
        val a = traj[lo]; val b = traj[hi]
        val dt = b.timeS - a.timeS
        if (dt < 1e-9) return Pair(a.position.x, a.position.y)
        val f = (tS - a.timeS) / dt
        return Pair(
            a.position.x + f * (b.position.x - a.position.x),
            a.position.y + f * (b.position.y - a.position.y)
        )
    }
}
