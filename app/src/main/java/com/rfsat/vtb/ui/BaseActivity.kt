package com.rfsat.vtb.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.rfsat.vtb.R

open class BaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
    }

    /**
     * Wires the shared bottom toolbar (include_bottom_nav) if the layout has
     * one. Call after setContentView with this screen's own nav item id.
     */
    protected fun setupBottomNav(selectedItemId: Int) {
        val nav = findViewById<BottomNavigationView>(R.id.bottomNav) ?: return
        nav.selectedItemId = selectedItemId // set BEFORE the listener to avoid a callback loop
        nav.setOnItemSelectedListener { item ->
            if (item.itemId == selectedItemId) return@setOnItemSelectedListener true
            val target = when (item.itemId) {
                R.id.nav_home -> MainActivity::class.java
                R.id.nav_capture -> com.rfsat.vtb.capture.CaptureActivity::class.java
                R.id.nav_profiles -> com.rfsat.vtb.profiles.ProfileActivity::class.java
                R.id.nav_log -> com.rfsat.vtb.log.LogActivity::class.java
                R.id.nav_about -> com.rfsat.vtb.about.AboutActivity::class.java
                else -> return@setOnItemSelectedListener false
            }
            val intent = Intent(this, target)
            if (target == MainActivity::class.java) intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
            if (this !is MainActivity) finish() // keep the back stack flat when hopping tabs
            false
        }
    }
}
