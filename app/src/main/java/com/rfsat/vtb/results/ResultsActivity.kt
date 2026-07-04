package com.rfsat.vtb.results

import android.os.Bundle
import com.rfsat.vtb.ui.BaseActivity
import com.rfsat.vtb.databinding.ActivityResultsBinding

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

        binding.tvAdjustmentSummary.text = buildString {
            appendLine("Target distance: ${AnalysisSession.targetDistanceYd} yd")
            appendLine()
            appendLine("WINDAGE: ${adjustment.windageDirection} ${String.format("%.2f", kotlin.math.abs(adjustment.windageMoa))} MOA " +
                "(${kotlin.math.abs(adjustment.windageClicks)} clicks)")
            appendLine("ELEVATION: ${adjustment.elevationDirection} ${String.format("%.2f", kotlin.math.abs(adjustment.elevationMoa))} MOA " +
                "(${kotlin.math.abs(adjustment.elevationClicks)} clicks)")
            appendLine()
            appendLine("Last shot's estimated impact offset from POA: " +
                "${String.format("%.1f", adjustment.impactOffsetInAtTarget.z)} in lateral, " +
                "${String.format("%.1f", adjustment.impactOffsetInAtTarget.y)} in vertical")
        }

        binding.windChart.setSeries(
            AnalysisSession.windSamples.map { it.timeS to it.crosswindMps * 2.23694 /* mph */ }
        )
        binding.windChart.title = "Estimated crosswind vs. time-of-flight (mph, +right)"
    }
}
