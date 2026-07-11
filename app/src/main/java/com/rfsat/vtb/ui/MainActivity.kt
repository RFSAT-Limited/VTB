package com.rfsat.vtb.ui

import android.content.Intent
import android.os.Bundle
import com.rfsat.vtb.ui.BaseActivity
import com.rfsat.vtb.capture.CaptureActivity
import com.rfsat.vtb.databinding.ActivityMainBinding
import com.rfsat.vtb.profiles.ProfileActivity
import com.rfsat.vtb.profiles.ProfileRepository

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val repo = ProfileRepository(this)

        refreshSummary(repo)

        binding.btnProfiles.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        binding.btnCapture.setOnClickListener {
            startActivity(Intent(this, CaptureActivity::class.java))
        }

        binding.spinnerTheme.adapter = android.widget.ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, ThemeMode.values().map { it.label }
        )
        binding.spinnerTheme.setSelection(ThemeMode.values().indexOf(ThemeManager.mode()))
        binding.spinnerTheme.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val selected = ThemeMode.values()[position]
                if (selected != ThemeManager.mode()) {
                    ThemeManager.setMode(this@MainActivity, selected)
                    recreate() // re-inflate with the new theme
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        binding.spinnerUnits.adapter = android.widget.ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, UnitSystem.values().map { it.label }
        )
        binding.spinnerUnits.setSelection(UnitSystem.values().indexOf(UnitsManager.system()))
        binding.spinnerUnits.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val selected = UnitSystem.values()[position]
                if (selected != UnitsManager.system()) {
                    UnitsManager.setSystem(this@MainActivity, selected)
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        setupBottomNav(com.rfsat.vtb.R.id.nav_home)
        maybeShowCrashReport()
    }

    /** v19.1: if the previous launch died, show the recorded stack in a
     *  shareable dialog — crash diagnosis without adb. Dismiss clears the
     *  record (which also re-enables stored-analysis restore next launch). */
    private fun maybeShowCrashReport() {
        val prefs = getSharedPreferences(com.rfsat.vtb.VtbApp.CRASH_PREFS, MODE_PRIVATE)
        val stack = prefs.getString(com.rfsat.vtb.VtbApp.KEY_STACK, null) ?: return
        val tv = android.widget.TextView(this).apply {
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 10f
            setPadding(32, 16, 32, 0)
            text = stack
            setTextIsSelectable(true)
        }
        val scroll = android.widget.ScrollView(this).apply { addView(tv) }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("VTB crashed on the previous launch")
            .setView(scroll)
            .setPositiveButton("Share") { _, _ ->
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "VTB crash report")
                    putExtra(Intent.EXTRA_TEXT, stack)
                }
                startActivity(Intent.createChooser(send, "Share crash report"))
                prefs.edit().clear().apply()
            }
            .setNegativeButton("Dismiss") { _, _ -> prefs.edit().clear().apply() }
            .setCancelable(false)
            .show()
    }

    override fun onResume() {
        super.onResume()
        refreshSummary(ProfileRepository(this))
        setupBottomNav(com.rfsat.vtb.R.id.nav_home)
    }

    private fun refreshSummary(repo: ProfileRepository) {
        val rifle = repo.getRifle()
        val bullet = repo.getBullet()
        val scope = repo.getScope()
        binding.tvSummary.text = getString(
            com.rfsat.vtb.R.string.active_profile_summary,
            rifle.name, bullet.name, scope.name
        )
    }
}
