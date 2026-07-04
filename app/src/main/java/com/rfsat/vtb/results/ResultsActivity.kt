package com.rfsat.vtb.results

import android.os.Bundle
import com.rfsat.vtb.databinding.ActivityResultsBinding
import com.rfsat.vtb.ui.BaseActivity
import com.rfsat.vtb.ui.UnitsManager
import kotlin.math.abs

class ResultsActivity : BaseActivity() {

    private lateinit var binding: ActivityResultsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupBottomNav(com.rfsat.vtb.R.id.nav_capture)

        val adjustment = AnalysisSession.adjustment
        if (adjustment == null) {
            binding.tvAdjustmentSummary.text = "No analysis available."
            return
        }

        val distUnit = UnitsManager.distanceUnitLabel()
        val speedUnit = UnitsManager.speedUnitLabel()
        val offsetUnit = UnitsManager.offsetUnitLabel()
        val targetDist = UnitsManager.displayDistance(AnalysisSession.targetDistanceYd * 0.9144)

        binding.tvAdjustmentSummary.text = buildString {
            appendLine("Target distance: ${fmt1(targetDist)} $distUnit")
            appendLine()
            appendLine("WINDAGE:   ${adjustment.windageDirection} " +
                "${fmt2(abs(adjustment.windageScopeUnits))} ${adjustment.scopeUnitLabel} " +
                "(${abs(adjustment.windageClicks)} clicks)")
            appendLine("ELEVATION: ${adjustment.elevationDirection} " +
                "${fmt2(abs(adjustment.elevationScopeUnits))} ${adjustment.scopeUnitLabel} " +
                "(${abs(adjustment.elevationClicks)} clicks)")
            appendLine()
            appendLine("Estimated crosswind: " +
                "${fmt1(UnitsManager.displaySpeed(abs(adjustment.estimatedCrosswindMps)))} $speedUnit " +
                (if (adjustment.estimatedCrosswindMps >= 0) "left-to-right" else "right-to-left") +
                "  (confidence ${(adjustment.windConfidence * 100).toInt()}%)")
            appendLine()
            appendLine("Last shot's estimated impact offset from POA: " +
                "${fmt1(UnitsManager.displayOffset(adjustment.impactOffsetMAtTarget.z))} $offsetUnit lateral, " +
                "${fmt1(UnitsManager.displayOffset(adjustment.impactOffsetMAtTarget.y))} $offsetUnit vertical")
            if (adjustment.warnings.isNotEmpty()) {
                appendLine()
                adjustment.warnings.forEach { appendLine("\u26A0 $it") }
            }
        }

        binding.windChart.setSeries(
            AnalysisSession.windSamples.map { it.timeS to UnitsManager.displaySpeed(it.crosswindMps) }
        )
        binding.windChart.title = "Estimated crosswind vs. seconds after shot ($speedUnit, +right)"
    }

    private fun fmt1(v: Double) = String.format("%.1f", v)
    private fun fmt2(v: Double) = String.format("%.2f", v)
}
