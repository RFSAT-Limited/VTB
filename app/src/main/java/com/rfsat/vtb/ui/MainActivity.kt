package com.rfsat.vtb.ui

import android.content.Intent
import android.os.Bundle
import com.rfsat.vtb.ui.BaseActivity
import com.rfsat.vtb.about.AboutActivity
import com.rfsat.vtb.capture.CaptureActivity
import com.rfsat.vtb.databinding.ActivityMainBinding
import com.rfsat.vtb.log.LogActivity
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

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                com.rfsat.vtb.R.id.nav_home -> true // already home
                com.rfsat.vtb.R.id.nav_log -> { startActivity(Intent(this, LogActivity::class.java)); false }
                com.rfsat.vtb.R.id.nav_about -> { startActivity(Intent(this, AboutActivity::class.java)); false }
                else -> false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshSummary(ProfileRepository(this))
        binding.bottomNav.selectedItemId = com.rfsat.vtb.R.id.nav_home
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
