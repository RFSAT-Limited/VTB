package com.rfsat.vtb.profiles

data class RifleProfile(
    val name: String = "Ruger Precision Rimfire",
    val barrelLengthIn: Double = 18.0,
    val twistRateInPerTurn: Double = 16.0, // 1:16" — factory RPR .22LR twist
    val sightHeightIn: Double = 1.7,       // scope centerline over bore, adjust per mount
    /** Distance at which the scope was zeroed, metres. Default 200 m
     *  (matches the test rifle's current zero). This
     *  drives the launch-pitch solution ([com.rfsat.vtb.ballistics.BallisticsEngine.solveZeroPitch])
     *  and therefore every computed correction. */
    val zeroDistanceM: Double = 200.0,
    /** LEGACY (pre-v9.0) zero distance in yards. Kept only so Gson can read
     *  old saved profiles; migrated to [zeroDistanceM] in ProfileRepository.
     *  0.0 means "not present". */
    @Deprecated("Use zeroDistanceM") val zeroDistanceYards: Double = 0.0,
    // Fine calibration for a phone mounted parallel to the scope with its
    // on-screen crosshair aligned to the scope's crosshair. 0,0 = dead
    // center of the frame; nudge in-app if the mechanical mount isn't
    // perfectly aligned. Normalized to frame width/height (-0.5..0.5).
    val boresightOffsetXNorm: Double = 0.0,
    val boresightOffsetYNorm: Double = 0.0
) {
    companion object {
        val DEFAULT = RifleProfile()
    }
}
